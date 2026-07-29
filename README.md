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
| Neural denoiser (RNNoise / ONNX) | 🧩 Seam + fallback — see `Source/ai/NeuralDenoiser.h` |

The plugin does **not** label classic DSP as "AI". The `Denoise` control drives a
genuine spectral denoiser; the `Neural NR` toggle is wired to an integration seam
that currently falls back to that same classic engine until a real model is
dropped in (the header documents exactly how).

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
    NeuralDenoiser.*      integration seam for RNNoise / ONNX (documented)
  gui/
    VoxLookAndFeel.*      dark theme
    LevelMeter.*          lock-free peak meter
    SpectrumAnalyzer.*    real-time FFT display
docs/
  FEASIBILITY.md          the honest assessment — start here
  ARCHITECTURE.md         real-time design notes & how to extend
```

## Roadmap

See `docs/FEASIBILITY.md §7`. Next highest-value step: integrate **RNNoise**
behind the existing `NeuralDenoiser` seam to make the AI claim truthful.

## License

Choose a license before distributing. Note that JUCE, and any AI model weights
you integrate, carry their own license terms you must comply with for commercial
release.
