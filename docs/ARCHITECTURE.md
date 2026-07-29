# Architecture & Real-Time Design Notes

This document explains *how* the code is structured and the real-time rules it
follows, so the project can be extended without introducing audio-thread hazards.

## Threading model

There are two threads that matter:

- **Audio thread** — `processBlock()` and everything it calls. Hard real-time.
  No heap allocation, no locks, no file/system I/O, no exceptions.
- **Message (GUI) thread** — the editor, timers, painting. May allocate.

Data crosses between them only through lock-free primitives:

| Direction | Mechanism |
|---|---|
| GUI → audio (parameters) | `AudioProcessorValueTreeState` atomics, smoothed on the audio thread |
| audio → GUI (meters) | `std::atomic<float>` peak, `exchange(0)` on read |
| audio → GUI (spectrum) | single-producer/single-consumer ring (`scopeBuf` + atomic write index), drained by the editor timer and fed to `SpectrumAnalyzer` |

The audio thread never touches a GUI component pointer, so closing the editor is
always safe.

## Signal chain

```
input gain (smoothed) ──► [tap: input meter]
                          │
              ┌───────────┴── dry copy ──► dryDelay (= plugin latency) ──┐
              ▼                                                           │
        NoiseGate                                                        │
              ▼                                                           │
     NeuralDenoiser ── classic SpectralDenoiser (STFT)  [adds latency]   │
              ▼                                                           │
        ParametricEQ (HPF + warmth/body/presence/bright)                 │
              ▼                                                           │
        Compressor                                                       │
              ▼                                                           │
        DeEsser                                                          ▼
              ▼                                                    dry/wet blend
        output gain (smoothed) ◄──────────────────────────────────────┘
              ▼
        [tap: output meter] ──► [tap: spectrum FIFO]
```

### Latency compensation

The only latency in the chain is the STFT denoiser
(`fftSize - hopSize` = 768 samples at the default 1024/256 config). We:

1. report it to the host with `setLatencySamples()` so the DAW delay-compensates
   the whole plugin, and
2. delay the **dry** signal by the same amount (`dryDelay`) so the dry/wet blend
   does not comb-filter.

If you change `fftSize`/`hopSize`, both fall out of `getLatencySamples()`
automatically — no other code changes needed.

## The STFT denoiser (`SpectralDenoiser`)

Standard weighted-overlap-add:

- Hann analysis **and** synthesis window, 75% overlap (hop = fftSize/4).
- COLA normalisation computed in `prepare()` from the window itself, so unity
  gain reconstructs the input exactly when the spectral gain is 1.0.
- Per-bin noise floor tracked with a fast-down / slow-up follower; spectral
  subtraction with an oversubtraction factor and a residual floor to limit
  musical noise.
- Hermitian symmetry is maintained before the inverse transform.

## The neural denoiser (`NeuralDenoiser`, RNNoise)

Built in when `-DVOX_ENABLE_RNNOISE=ON` (defines `VOX_HAVE_RNNOISE`). Design:

- One `DenoiseState` per channel (`rnnoise_create`). Input is scaled to int16
  range for the model and back on output; the `Denoise` amount blends dry→wet
  per sample so the control still sweeps continuously.
- RNNoise is **48 kHz / 480-sample-frame only**, so the neural path engages only
  when the session runs at 48 kHz (`neuralReady`); at any other rate, or when
  RNNoise isn't compiled in, `process()` falls back to the classic STFT denoiser.
- **Constant reported latency.** The neural path buffers 480-sample frames
  (480 samples of latency) and is then padded with a primed output ring so its
  total latency equals the classic STFT latency. `getLatencySamples()` is
  therefore identical for both engines, so toggling `Neural NR` never disturbs
  host plugin-delay compensation.
- `reset()` uses `rnnoise_init()` (in-place, no allocation) so it's real-time
  safe; allocation happens only in `prepare()`.

**Follow-ups.** (1) Add streaming sample-rate conversion (e.g. a
`juce::LagrangeInterpolator` per direction with an input FIFO) so neural mode
runs at any session rate — the framing/latency machinery already in place stays
unchanged. (2) For higher quality, swap RNNoise for DeepFilterNet/DTLN via ONNX
Runtime, reusing the `SpectralDenoiser` STFT plumbing to feed the model its
magnitude frames and apply the predicted mask. See `Source/ai/NeuralDenoiser.h`.

## Extending: turning macros into full controls

Several modules expose a single "amount" macro that internally moves multiple
parameters (e.g. `Compressor` maps one knob to threshold+ratio+makeup). To expose
full controls, add the parameters in `Parameters.h`, add setters on the module,
and wire them in `VoxAIAudioProcessor::updateParameters()`. The UI grid in
`PluginEditor::resized()` lays out whatever knobs exist, so new knobs appear
automatically once added to the `kKnobDefs` table.

## Phase 4 (optimization) starting points

- Denormals are already guarded (`ScopedNoDenormals`).
- Move per-sample inner loops to `juce::dsp` / SIMD (`juce::dsp::AudioBlock`,
  `FloatVectorOperations`) where profiling shows hotspots.
- Consider a lower FFT order or `hopSize` trade-off if latency matters more than
  denoise quality; expose it as a "quality/latency" switch.
- Profile neural inference at the host's smallest block size before shipping;
  provide a latency/quality mode if it does not fit the CPU budget.

## Phase 5 (packaging) checklist

- Windows: Inno Setup or WiX around the built `.vst3`.
- macOS: `pkgbuild`/`productbuild`, then codesign + notarize.
- Code-sign on both platforms (users' security prompts otherwise).
- Licensing: start simple (offline signed license file / keygen); escalate to a
  service only if piracy actually becomes a problem.
- CI: the provided GitHub Actions workflow builds on Windows + macOS.
