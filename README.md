# VoxAI — AI Voice Channel Strip (VST3 / AU)

A real-time voice-processing plugin for music-production and podcast/streaming
environments (Cubase, Ableton Live, Logic Pro, Reaper, Pro Tools*), built with
JUCE and CMake.

> **Read [`docs/FEASIBILITY.md`](docs/FEASIBILITY.md) first.** It is an honest
> engineering assessment of what a solo developer can and cannot build versus a
> commercial product like Sonarworks SoundID VoiceAI — including exactly which
> features are real DSP today, which need a pre-trained model, and which require
> a research team.

\* AAX/Pro Tools requires the Avid AAX SDK + PACE signing (paperwork, not code).

---

## What this repository is

This is a **buildable Phase 1 + Phase 2 implementation** (per the roadmap in the
feasibility doc), plus an **honest integration seam for Phase 3 (AI)**:

| Area | Status |
|---|---|
| VST3 / AU / Standalone shell (JUCE, CMake) | ✅ |
| Parameters, state save/load, presets, A/B compare | ✅ |
| High-pass / rumble filter | ✅ |
| Noise gate (attack/hold/release, depth) | ✅ |
| **Spectral noise reduction (real STFT/overlap-add)** | ✅ |
| Tone EQ: warmth / body / presence / brightness | ✅ |
| Compressor (soft-knee, macro-controlled) | ✅ |
| De-esser (split-band sidechain) | ✅ |
| Latency-compensated dry/wet + I/O gain | ✅ |
| Dark professional UI, meters, real-time spectrum | ✅ |
| **Neural denoiser (RNNoise)** | ✅ Opt-in: `-DVOX_ENABLE_RNNOISE=ON` (engages at 48 kHz) |

The plugin does **not** label classic DSP as "AI". The `Denoise` control drives a
genuine spectral denoiser. The `Neural NR` toggle engages **RNNoise** (a real
neural network) when the plugin is built with `-DVOX_ENABLE_RNNOISE=ON` and the
session runs at 48 kHz; otherwise it transparently falls back to the classic
spectral engine. So the AI claim is truthful when — and only when — a real model
is running. See `Source/ai/NeuralDenoiser.h`.

### Enabling the neural denoiser

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Release -DVOX_ENABLE_RNNOISE=ON
cmake --build build --config Release
```

This fetches [xiph/rnnoise](https://github.com/xiph/rnnoise) (BSD) and compiles it
in. It is **off by default** so the base build has no extra dependencies. RNNoise
runs at 48 kHz on 480-sample frames; the plugin engages it only at that session
rate and reports a constant latency across both engines. Sample-rate conversion so
neural mode works at any rate is a documented follow-up (`docs/ARCHITECTURE.md`).

> **Windows note:** the neural denoiser builds on **Linux/macOS (GCC/Clang)**.
> RNNoise's CELT sources use C99 variable-length arrays that MSVC's C compiler
> rejects, so `VOX_ENABLE_RNNOISE=ON` is unsupported under MSVC (use clang-cl, or
> build `OFF`). The classic spectral denoiser works on every platform, so Windows
> VST3 builds are fully functional without neural mode.

---

## Build

Prerequisites: CMake ≥ 3.22, a C++17 compiler (MSVC 2022 / Xcode / Clang / GCC).
JUCE is fetched automatically via CMake `FetchContent` (needs network on first
configure), or point `-DJUCE_SOURCE_DIR=/path/to/JUCE` at a local checkout.

```bash
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
```

Artifacts land in `build/VoxAI_artefacts/Release/` (VST3, AU on macOS, and a
Standalone app). `COPY_PLUGIN_AFTER_BUILD` also installs them to your user plugin
folders so a DAW rescan will find them.

### Validate

- Load the **Standalone** build for a quick sanity check with a mic.
- Run Steinberg's **VST3 validator** (bundled with JUCE) or `pluginval` against
  the built `.vst3` before shipping.

### Prebuilt binaries / releases

- Every push is built for Windows + macOS by `.github/workflows/build.yml`; the
  binaries are attached to each run as artifacts.
- To produce **downloadable release assets** (per-platform ZIPs + best-effort
  installers), push a version tag: `git tag v0.1.0 && git push origin v0.1.0`.
  `.github/workflows/release.yml` builds all platforms and uploads them to a
  **draft** GitHub Release (publish it when you're ready). See
  `docs/ARCHITECTURE.md` (Phase 5) for codesigning/notarization.

---

## Project layout

```
CMakeLists.txt            JUCE plugin target + FetchContent
Source/
  Parameters.h            single source of truth for all parameters
  PluginProcessor.*       real-time chain, state, A/B, GUI data taps
  PluginEditor.*          dark UI: knobs, meters, spectrum, presets
  dsp/
    NoiseGate.*           downward gate / soft expander
    Compressor.*          soft-knee compressor (macro-driven)
    ParametricEQ.*        HPF + tone shaping (juce::dsp IIR)
    DeEsser.*             split-band sibilance control
    SpectralDenoiser.*    STFT overlap-add spectral subtraction
  ai/
    NeuralDenoiser.*      RNNoise neural denoiser (opt-in) + classic fallback
cmake/
  rnnoise.cmake           optional RNNoise fetch/build (VOX_ENABLE_RNNOISE)
  gui/
    VoxLookAndFeel.*      dark theme
    LevelMeter.*          lock-free peak meter
    SpectrumAnalyzer.*    real-time FFT display
docs/
  FEASIBILITY.md          the honest assessment — start here
  ARCHITECTURE.md         real-time design notes & how to extend
```

## Roadmap

See `docs/FEASIBILITY.md §7`. Phase 3 has begun: **RNNoise** is integrated behind
`-DVOX_ENABLE_RNNOISE`. Next steps: sample-rate conversion so neural mode runs at
any session rate, and an optional offline "AI render" path using a larger ONNX
model (RX-class quality without the real-time constraint).

## License

Choose a license before distributing. Note that JUCE, and any AI model weights
you integrate, carry their own license terms you must comply with for commercial
release.
