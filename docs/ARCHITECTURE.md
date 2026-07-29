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

**This is also the AI seam.** To go neural, replace only the per-bin *gain*
computation in `processFrame()` with a model-predicted mask (DeepFilterNet/DTLN
via ONNX), or bypass the STFT entirely and call RNNoise on its native 480-sample
frames. See `Source/ai/NeuralDenoiser.h` for the step-by-step.

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
