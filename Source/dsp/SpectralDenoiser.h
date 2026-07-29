#pragma once

#include <juce_dsp/juce_dsp.h>

namespace vox::dsp
{
    /**
        Real STFT spectral-gating denoiser (classic, non-AI).

        Overlap-add processing with a Hann window and 4x overlap. A running
        estimate of the per-bin noise floor (tracked with a slow minimum
        follower during quiet passages) is used to compute a Wiener-style
        suppression gain per bin. The "amount" control scales how aggressively
        bins below the estimated noise floor are attenuated.

        This is the honest baseline: it is genuinely a spectral noise reducer,
        not EQ. It is also the exact seam where a neural denoiser replaces the
        gain computation — see Source/ai/NeuralDenoiser.

        Latency: fftSize - hopSize samples (reported to the host by the
        processor). Mono/per-channel independent processing.
    */
    class SpectralDenoiser
    {
    public:
        void prepare (double sampleRate, int numChannels, int maxBlock);
        void reset();

        void setAmount (float amount01) { amount = juce::jlimit (0.f, 1.f, amount01); }

        int  getLatencySamples() const { return fftSize - hopSize; }

        /** In-place. */
        void process (juce::AudioBuffer<float>& block);

    private:
        static constexpr int fftOrder = 10;              // 1024-point FFT
        static constexpr int fftSize  = 1 << fftOrder;
        static constexpr int hopSize  = fftSize / 4;     // 75% overlap

        struct ChannelState
        {
            std::vector<float> history;      // fftSize sliding input window (index 0 = oldest)
            std::vector<float> accum;        // fftSize overlap-add accumulator
            std::vector<float> hopIn;        // hopSize input collector
            int fill = 0;                    // samples collected toward the next hop
            std::vector<float> outRing;      // ready-output ring, size fftSize*2
            int outW = 0, outR = 0, outCount = 0;
            std::vector<float> noiseFloor;   // per-bin magnitude estimate, size fftSize/2+1
        };

        void processFrame (ChannelState& st);   // consumes st.history, adds synthesis to st.accum

        double sr = 44100.0;
        float amount = 0.f;
        int numCh = 2;
        float windowCorrection = 1.f;

        juce::dsp::FFT fft { fftOrder };
        std::vector<float> window;
        std::vector<float> fftData;   // 2*fftSize scratch (interleaved for JUCE realFFT)
        std::vector<ChannelState> chans;
    };
}
