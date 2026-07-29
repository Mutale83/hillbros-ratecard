#pragma once

#include "../dsp/SpectralDenoiser.h"

namespace vox::ai
{
    /**
        Integration seam for a neural noise-suppression model.

        RIGHT NOW this class is an honest facade: when "neural" mode is off it
        forwards to the classic spectral denoiser, and when "neural" mode is on
        it *still* uses the classic denoiser but flags (via isNeuralActive) that
        no model is loaded. Nothing here pretends EQ is AI.

        TO MAKE THE AI CLAIM REAL, integrate one of the following behind this
        interface — the surrounding plugin needs no other changes:

          1) RNNoise (fastest path)
             - Add the rnnoise C sources to the build.
             - In prepare(): rnnoise_create(nullptr) -> DenoiseState*.
             - RNNoise works on 480-sample frames of 48 kHz float scaled to
               int16 range. Buffer/resample to that frame size, call
               rnnoise_process_frame(st, out, in) per frame, overlap into the
               block. Set modelLoaded = true.

          2) ONNX Runtime + DeepFilterNet / DTLN (higher quality)
             - Link onnxruntime (CPU EP, static). Create Ort::Session from the
               exported .onnx in prepare().
             - Feed the model its expected feature frames (usually STFT
               magnitude/complex); apply the predicted mask; inverse-STFT.
               The SpectralDenoiser's STFT plumbing is a ready template — you
               replace only the per-bin gain computation with the model's mask.

        Real-time notes: keep inference on the audio thread only if it fits the
        CPU budget at the host block size; otherwise run it in a bounded worker
        with a lock-free ring and report the added latency. Do NOT use a GPU
        execution provider for the real-time path (see docs/FEASIBILITY.md §5).
    */
    class NeuralDenoiser
    {
    public:
        void prepare (double sampleRate, int numChannels, int maxBlock)
        {
            classic.prepare (sampleRate, numChannels, maxBlock);
            // TODO(ai): load RNNoise / ONNX session here and set modelLoaded.
        }

        void reset() { classic.reset(); }

        void setAmount (float amount01) { classic.setAmount (amount01); }
        void setUseNeural (bool shouldUseNeural) { useNeural = shouldUseNeural; }

        /** True only when neural mode is requested AND a model is actually loaded. */
        bool isNeuralActive() const { return useNeural && modelLoaded; }

        int getLatencySamples() const { return classic.getLatencySamples(); }

        void process (juce::AudioBuffer<float>& block)
        {
            if (isNeuralActive())
            {
                // TODO(ai): run neural inference here instead of the classic path.
                classic.process (block);
            }
            else
            {
                classic.process (block);
            }
        }

    private:
        vox::dsp::SpectralDenoiser classic;
        bool useNeural = false;
        bool modelLoaded = false;   // flips true once a real model is integrated
    };
}
