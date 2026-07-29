#pragma once

#include "../dsp/SpectralDenoiser.h"
#include <vector>

namespace vox::ai
{
    /**
        Neural noise suppression via RNNoise, with an honest classic fallback.

        Build-time gate: define VOX_HAVE_RNNOISE (CMake option VOX_ENABLE_RNNOISE)
        to link the RNNoise library. Without it this class always uses the
        classic spectral denoiser, and toggling "Neural NR" simply keeps using
        that engine — nothing here pretends EQ or classic DSP is AI.

        Runtime engagement (when built with RNNoise):
          * RNNoise runs at its native 48 kHz on fixed frames (frame size from
            rnnoise_get_frame_size(), 480 samples). We therefore engage the
            neural path only when the host session is 48 kHz; at other rates we
            fall back to the classic denoiser. (Adding sample-rate conversion so
            neural mode works at any rate is a documented follow-up — see
            docs/ARCHITECTURE.md.)
          * One RNNoise state per channel. Input is scaled to int16 range for the
            model and back on output.
          * The neural path's framing latency is padded to exactly match the
            classic STFT latency, so getLatencySamples() is constant regardless
            of which engine is active and host delay compensation stays stable.
    */
    class NeuralDenoiser
    {
    public:
        NeuralDenoiser() = default;
        ~NeuralDenoiser();

        void prepare (double sampleRate, int numChannels, int maxBlock);
        void reset();

        void setAmount (float amount01);
        void setUseNeural (bool shouldUseNeural) { useNeural = shouldUseNeural; }

        /** True only when neural mode is requested AND a model is actually running. */
        bool isNeuralActive() const { return useNeural && neuralReady; }

        /** Constant across engines so host PDC never has to change. */
        int getLatencySamples() const { return classic.getLatencySamples(); }

        void process (juce::AudioBuffer<float>& block);

    private:
        void processNeural (juce::AudioBuffer<float>& block);
        void destroyStates();

        vox::dsp::SpectralDenoiser classic;
        bool  useNeural   = false;
        bool  neuralReady = false;    // true only when RNNoise is built, created, and sr == 48k
        float amount = 0.f;
        int   frameSize = 480;

        struct NChan
        {
            void* state = nullptr;          // rnnoise DenoiseState* (opaque here)
            std::vector<float> inFrame;     // collects frameSize input samples
            std::vector<float> outFrame;    // rnnoise output scratch
            int   fill = 0;
            std::vector<float> outRing;      // latency-matched output ring
            int   outW = 0, outR = 0, outCount = 0;
        };
        std::vector<NChan> nchans;
    };
}
