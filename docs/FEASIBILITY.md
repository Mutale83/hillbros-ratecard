# Technical Feasibility Assessment — AI Voice Processing Plugin (VST3/AU)

**Project codename:** VoxAI
**Comparable commercial product:** Sonarworks SoundID VoiceAI, iZotope RX, Waves Clarity Vx, Accentize dxRevive
**Author:** Engineering assessment prepared with Claude Code
**Date:** 2026-07-29

---

## 0. TL;DR — Read This First

You **can** build a professional, shippable VST3/AU voice-processing plugin as a solo
developer using Claude Code. The plugin *shell*, the *DSP chain* (EQ, compression,
gate, de-esser, classic noise reduction), the *UI*, and the *packaging/licensing* are
all realistic solo work. This repository already contains a **buildable Phase 1 + Phase 2
implementation** of exactly that.

You **cannot realistically** train, from scratch, a neural voice-enhancement model that
matches SoundID VoiceAI or Waves Clarity Vx as a solo developer. Those models represent
**multi-year, multi-million-dollar R&D efforts** by dedicated ML+DSP teams with
proprietary datasets. What *is* achievable solo is **integrating an existing pre-trained
model** (open-source or licensed) via ONNX Runtime and shipping it inside the plugin.

The honest split:

| Layer | Solo + Claude Code | Needs a team |
|---|---|---|
| VST3/AU shell, state, presets | ✅ Fully achievable | — |
| Classic DSP (EQ/comp/gate/de-ess/expander) | ✅ Fully achievable | — |
| Spectral (FFT) noise reduction | ✅ Achievable (this repo has it) | — |
| Professional UI | ✅ Achievable | Polish/UX designer helps |
| **Integrating** a pre-trained NN denoiser (ONNX) | ✅ Achievable | — |
| **Training** a SOTA voice-enhancement model | ❌ | ✅ ML team + data + GPUs |
| Voice *transformation* / timbre morphing (SOTA) | ⚠️ Prototype only | ✅ ML research team |
| AAX (Pro Tools) | ⚠️ Needs Avid NDA/SDK access | Paperwork, not headcount |

---

## 1. What SoundID VoiceAI Actually Is (so we know the target)

SoundID VoiceAI is **not** a conventional DSP plugin. Its headline feature is a
**generative voice-transformation / re-synthesis engine**: it can take a poorly recorded
voice and re-render it as if captured on a studio mic, and it offers *voice models*
("Studio Voice", speaker profiles). Under the hood this is **neural audio synthesis**
(vocoder-class / diffusion-class models), not EQ + compression.

That distinction drives the entire feasibility picture:

- **The DSP-style features** it *also* exposes (noise reduction, de-reverb, clarity) are
  each individually reproducible at "very good" quality with classic + light-ML DSP.
- **The generative re-synthesis** ("make this sound like it was recorded in a studio",
  arbitrary voice character morphing) is the part that is genuinely research-grade.

**Strategic implication:** target the *achievable* 80% (a superb real-time voice channel
strip with a neural denoiser) rather than the *research* 20% (generative re-synthesis).
The achievable 80% is already a commercially viable product — that describes most of
Accentize, Clarity Vx "lite" tiers, Nvidia Broadcast-style tools, etc.

---

## 2. Feature-by-Feature Feasibility Matrix

Legend: 🟢 Solo-achievable now · 🟡 Solo-achievable with a licensed/OSS model · 🔴 Team/research

### 2.1 AI Voice Enhancement Engine
| Feature | Verdict | Notes |
|---|---|---|
| Real-time voice cleanup | 🟢/🟡 | Classic spectral NR is 🟢 (in this repo). Neural NR is 🟡 (integrate RNNoise / DeepFilterNet / DTLN). |
| Background noise removal | 🟢/🟡 | Same as above. RNNoise is small, real-time, MIT-ish, C — easiest neural win. |
| Room/reverb reduction | 🟡 | Real-time de-reverb is *hard*. Light single-channel de-reverb via spectral subtraction is 🟢 but modest quality. Good de-reverb ≈ 🔴. |
| Voice clarity improvement | 🟢 | EQ presence lift + dynamic EQ + transient shaping. |
| Vocal presence enhancement | 🟢 | Dynamic presence band + upward compression. |
| Dynamic processing | 🟢 | Compressor/expander/gate — in this repo. |
| Natural-sounding restoration | 🟡 | "Restoration" of *lost* content (bandwidth extension) is 🟡→🔴 (needs a generative model). |

### 2.2 Voice Transformation
| Feature | Verdict | Notes |
|---|---|---|
| AI voice modeling | 🔴 | Core SoundID differentiator. Research-grade neural synthesis. |
| Voice character adjustment | 🟡 | *Timbral* adjustment via EQ/formant shifting is 🟢. *Identity* morphing is 🔴. |
| Tone/warmth/brightness/body | 🟢 | Tilt EQ, harmonic saturation, formant tilt — classic DSP. |
| Male/female characteristics | 🟡 | Formant + pitch shifting gets you *part way* (🟢 for subtle, 🔴 for convincing). |
| Broadcast/podcast presets | 🟢 | Preset curation over the DSP chain. |
| Singing enhancement | 🟡 | Pitch correction is 🟢 (well-trodden). "Enhancement" beyond that is 🟡. |

### 2.3 Real-Time Audio Processing
| Feature | Verdict | Notes |
|---|---|---|
| Low-latency DSP | 🟢 | Sample-accurate; report `getLatencySamples()` for FFT lookahead. |
| VST3 + AU | 🟢 | JUCE builds both from one codebase. |
| 64-bit processing | 🟢 | JUCE processes float by default; double-precision optional. "64-bit" in marketing usually means double *support*. |
| Multi-channel | 🟢 | Bus layout config. Voice work is mono/stereo in practice. |
| CPU-efficient | 🟢/🟡 | Classic DSP is cheap. Neural inference is the CPU cost driver (🟡 to keep it real-time). |
| Real-time spectrum/waveform | 🟢 | FFT analyzer in the editor (lock-free FIFO to GUI). |

### 2.4 AI Model Integration — Recommendation
See §5. Short version: **ONNX Runtime** (CPU EP, statically linked) is the pragmatic
choice for cross-platform C++ plugin inference. TensorFlow Lite is viable but ONNX has
better desktop-C++ ergonomics. Native/hand-written inference only for *tiny* models
(RNNoise ships its own C inference — no framework needed, and it's the fastest path to a
real neural denoiser).

### 2.5 Plugin Interface
Every listed UI element (meters, sliders, preset browser, A/B, before/after, dark theme)
is 🟢 standard JUCE component work. This repo ships a functional dark UI.

### 2.6 Development Requirements
JUCE + VST3 SDK (bundled with JUCE) + AU (bundled) + CMake + cross-platform: all 🟢.
**AAX/Pro Tools is 🟡 for a non-code reason:** Avid requires you to register for the AAX
SDK and, for distribution, PACE/iLok signing. That's paperwork and a hardware signer, not
engineering headcount.

---

## 3. Complexity vs. Sonarworks SoundID VoiceAI

| Dimension | This project (achievable target) | SoundID VoiceAI |
|---|---|---|
| Core tech | DSP chain + integrated pre-trained denoiser | Proprietary generative voice model |
| Team behind it | 1 dev + Claude Code | ML researchers + DSP + UX + QA + product |
| Dataset | None needed (use pretrained) | Large proprietary speech corpora |
| Training compute | None (inference only) | Substantial GPU cluster |
| Realistic quality parity | ~70–85% on *cleanup*; ~0% on *generative re-synthesis* | 100% (it's the reference) |
| Time to MVP | Weeks (cleanup channel strip) | Years |
| Time to "sounds like SoundID" | Not reachable solo | — |

**Bottom line:** You can ship something a podcaster/streamer/home-studio user will happily
pay for. You will not out-engineer Sonarworks' generative model alone. Compete on
*price, latency, workflow, and being a great real-time channel strip*, not on beating
their neural re-synthesis.

---

## 4. Recommended Architecture (implemented in this repo)

```
Audio thread (real-time, no allocations, no locks):
  Input → InputMeter
        → HighPass (rumble)
        → NoiseGate            [Phase 2 ✅]
        → SpectralDenoiser     [Phase 2 ✅ classic / Phase 3 ⇒ neural]
        → ParametricEQ         [Phase 2 ✅ tone/warmth/brightness/presence]
        → Compressor           [Phase 2 ✅]
        → DeEsser              [Phase 2 ✅]
        → MakeupGain / Output
        → OutputMeter
        (A/B + dry/wet mix wraps the whole chain)

Message thread (GUI):
  Editor ← lock-free FIFO ← analysis taps (meters, spectrum)
  Editor → AudioProcessorValueTreeState → parameters (thread-safe)
```

Key real-time rules enforced in the code:
- No heap allocation, locking, or file I/O on the audio thread.
- All parameters via `AudioProcessorValueTreeState` (atomic, smoothed).
- FFT buffers pre-allocated in `prepareToPlay`.
- GUI reads analysis data through a single-producer/single-consumer FIFO.

---

## 5. AI Model Integration — Detailed Recommendation

### The four candidate paths

1. **RNNoise (recommended first neural step).** Tiny GRU-based denoiser, C inference
   built-in, no framework dependency, real-time on one core, permissive license. Fastest
   path to a *genuine* neural denoiser inside the plugin. Quality: good on stationary +
   moderate non-stationary noise; not SOTA but shippable.

2. **DeepFilterNet (best OSS quality/real-time trade-off).** Rust core, ONNX-exportable.
   Notably better than RNNoise. Real-time on modern CPUs. More integration work; watch the
   license (currently permissive but verify per version).

3. **ONNX Runtime + a chosen model (most flexible).** Statically link ONNX Runtime (CPU
   Execution Provider). Run any exported model (DTLN, DeepFilterNet, your own). Adds
   ~5–15 MB to the binary. GPU EPs (CUDA/CoreML/DirectML) exist but **avoid GPU for a
   real-time plugin** — PCIe/driver latency and contention with the host make it a poor
   fit; CPU inference of a right-sized model is the correct call.

4. **Train your own.** 🔴 Not solo. Only revisit if you raise a team.

### Why not TensorFlow Lite / PyTorch-in-plugin
- **TFLite:** works, but its C++ desktop story is clunkier than ONNX Runtime; better on
  mobile. Viable, not preferred here.
- **LibTorch (PyTorch C++):** huge binary, not designed for hard-real-time audio, GPL-ish
  friction for redistribution. Avoid for shipping plugins.

### GPU acceleration honest take
For a **real-time** insert plugin: **don't.** Round-trip latency to a GPU and unpredictable
scheduling under a DAW's audio callback make it unsuitable. GPU only makes sense for
*offline* processing (an "AI render" button, RX-style), which is a legitimate feature —
but that's a different, non-real-time code path.

### The pragmatic roadmap for AI
`Phase 2 classic spectral NR (done)` → `drop in RNNoise (weekend)` → `optionally ONNX +
DeepFilterNet (weeks)` → `offline "AI render" path with a bigger model (later)`.

This repo **integrates RNNoise** in `Source/ai/NeuralDenoiser.*` behind the CMake
option `-DVOX_ENABLE_RNNOISE=ON` (verified to build and link — see `cmake/rnnoise.cmake`).
When enabled and the session is 48 kHz, the `Neural NR` toggle runs the real RNNoise
network; otherwise it transparently falls back to the classic spectral denoiser. It does
**not** ship a fake "AI" that's secretly just EQ — the AI claim is truthful exactly when a
real model is running, and the fallback is honest about what's classic vs. neural.
Remaining work on this path: sample-rate conversion so neural mode runs at any rate, and
(optionally) an ONNX + DeepFilterNet path for higher quality.

---

## 6. Solo vs. Team — The Honest Line

### Realistically achievable by a solo developer with Claude Code
- Full VST3/AU plugin (this repo).
- Complete real-time DSP channel strip: HPF, gate, spectral NR, multiband/parametric EQ,
  compressor, de-esser, makeup, metering, spectrum analyzer.
- Professional dark UI, presets, A/B, dry/wet, before/after monitoring.
- Integrating a **pre-trained** neural denoiser (RNNoise → DeepFilterNet via ONNX).
- Installers (Inno Setup / productbuild), a basic licensing/activation scheme.
- Cross-platform Win/macOS builds via CMake + CI.

### Requires a dedicated AI/audio team
- Training a competitive neural voice-enhancement or **voice-transformation** model.
- SOTA real-time de-reverberation.
- Generative "studio voice" re-synthesis (the SoundID headline feature).
- Convincing cross-gender / identity voice morphing.
- Proprietary dataset collection, licensing, and evaluation infrastructure.

### Requires paperwork/hardware, not headcount
- AAX (Pro Tools): Avid AAX SDK registration + PACE/iLok signing for distribution.
- Notarization (macOS) + code-signing certs (Win/mac).

---

## 7. Roadmap Mapped to This Repository

| Phase | Scope | Status in this repo |
|---|---|---|
| **1** | Buildable VST3/AU shell (JUCE + CMake), params, state, UI | ✅ Implemented |
| **2** | DSP: EQ, compression, gate, (classic) noise reduction, de-esser | ✅ Implemented |
| **3** | Integrate pre-trained neural denoiser (ONNX/RNNoise) | ✅ RNNoise integrated (opt-in `-DVOX_ENABLE_RNNOISE`, 48 kHz); ONNX/any-rate = follow-up |
| **4** | Latency/CPU optimization, SIMD, denormals, block-size tuning | 🟡 Foundations laid; see `docs/ARCHITECTURE.md` |
| **5** | Installer, licensing, signing, packaging, CI | 🟡 CMake + CI notes provided |

---

## 8. Risks & Honesty Flags

- **"AI" marketing risk.** Don't ship classic DSP labeled as "AI". This repo keeps the
  distinction explicit. Once RNNoise/ONNX is integrated, the AI claim becomes truthful.
- **Real-time neural CPU budget.** Measure. A denoiser that xruns at 64-sample buffers is
  not shippable; give users a latency/quality switch.
- **Licensing of models AND datasets.** OSS model weights sometimes carry
  non-commercial or dataset-derived restrictions. Verify per model, per version, before
  shipping commercially.
- **De-reverb overselling.** Under-promise here; single-channel real-time de-reverb is the
  weakest achievable feature.
- **Latency reporting.** FFT-based NR adds latency; report it via `setLatencySamples`
  so the DAW compensates, or users will (rightly) complain.

---

## 9. Recommended Next Actions

1. Build this repo (see `README.md`) and load it in a DAW to validate Phase 1+2.
2. Integrate RNNoise behind the existing `NeuralDenoiser` seam — the single highest-value,
   lowest-risk step toward a *real* AI claim.
3. Add an offline "AI Render" path (non-real-time) if you want RX-class quality with a
   larger ONNX model.
4. Only consider voice-transformation / generative features if you assemble a team.
