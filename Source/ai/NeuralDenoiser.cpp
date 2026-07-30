#include "NeuralDenoiser.h"

#if VOX_HAVE_RNNOISE
  #include <rnnoise.h>
#endif

namespace vox::ai
{
    NeuralDenoiser::~NeuralDenoiser()
    {
        destroyStates();
    }

    void NeuralDenoiser::destroyStates()
    {
    #if VOX_HAVE_RNNOISE
        for (auto& c : nchans)
        {
            if (c.state != nullptr)
            {
                rnnoise_destroy (static_cast<DenoiseState*> (c.state));
                c.state = nullptr;
            }
        }
    #endif
    }

    void NeuralDenoiser::prepare (double sampleRate, int numChannels, int maxBlock)
    {
        classic.prepare (sampleRate, numChannels, maxBlock);
        destroyStates();

        const int numCh = juce::jmax (1, numChannels);
        neuralReady = false;

    #if VOX_HAVE_RNNOISE
        // RNNoise is 48 kHz / fixed-frame only; engage only at native rate.
        if (std::abs (sampleRate - 48000.0) < 1.0)
        {
            frameSize = rnnoise_get_frame_size();

            // Pad the neural path so its total latency equals the classic path's,
            // keeping the reported plugin latency constant across engines.
            const int pad = juce::jmax (0, classic.getLatencySamples() - frameSize);

            nchans.clear();
            nchans.resize ((size_t) numCh);
            bool ok = true;
            for (auto& c : nchans)
            {
                c.state = rnnoise_create (nullptr);
                ok = ok && (c.state != nullptr);
                c.inFrame.assign  ((size_t) frameSize, 0.f);
                c.outFrame.assign ((size_t) frameSize, 0.f);
                c.outRing.assign  ((size_t) (frameSize + pad + 8), 0.f);
                c.fill = 0;
                // Prime the ring with `pad` zeros to add the latency-matching delay.
                c.outW = c.outR = c.outCount = 0;
                for (int k = 0; k < pad; ++k)
                {
                    c.outRing[(size_t) c.outW] = 0.f;
                    c.outW = (c.outW + 1) % (int) c.outRing.size();
                    ++c.outCount;
                }
            }
            neuralReady = ok;
            if (! ok) destroyStates();
        }
    #else
        juce::ignoreUnused (maxBlock);
    #endif
    }

    void NeuralDenoiser::reset()
    {
        classic.reset();
    #if VOX_HAVE_RNNOISE
        // Re-initialise in place (no allocation → real-time safe).
        const int pad = juce::jmax (0, classic.getLatencySamples() - frameSize);
        for (auto& c : nchans)
        {
            if (c.state != nullptr)
                rnnoise_init (static_cast<DenoiseState*> (c.state), nullptr);

            std::fill (c.inFrame.begin(),  c.inFrame.end(),  0.f);
            std::fill (c.outFrame.begin(), c.outFrame.end(), 0.f);
            std::fill (c.outRing.begin(),  c.outRing.end(),  0.f);
            c.fill = 0; c.outW = 0; c.outR = 0; c.outCount = 0;

            for (int k = 0; k < pad; ++k)   // re-prime the latency-matching delay
            {
                c.outW = (c.outW + 1) % (int) c.outRing.size();
                ++c.outCount;
            }
        }
    #endif
    }

    void NeuralDenoiser::setAmount (float amount01)
    {
        amount = juce::jlimit (0.f, 1.f, amount01);
        classic.setAmount (amount);
    }

    void NeuralDenoiser::process (juce::AudioBuffer<float>& block)
    {
        if (isNeuralActive())
            processNeural (block);
        else
            classic.process (block);
    }

    void NeuralDenoiser::processNeural (juce::AudioBuffer<float>& block)
    {
    #if VOX_HAVE_RNNOISE
        if (amount <= 0.0001f)
            return;

        const int n = block.getNumSamples();
        const int chToDo = juce::jmin (block.getNumChannels(), (int) nchans.size());
        constexpr float kScale = 32768.0f;

        for (int ch = 0; ch < chToDo; ++ch)
        {
            auto& c = nchans[(size_t) ch];
            auto* st = static_cast<DenoiseState*> (c.state);
            auto* data = block.getWritePointer (ch);
            const int ring = (int) c.outRing.size();

            for (int i = 0; i < n; ++i)
            {
                c.inFrame[(size_t) c.fill++] = data[i] * kScale;

                if (c.fill == frameSize)
                {
                    c.fill = 0;
                    rnnoise_process_frame (st, c.outFrame.data(), c.inFrame.data());

                    for (int k = 0; k < frameSize; ++k)
                    {
                        // Blend by amount: 0 = dry, 1 = fully denoised.
                        const float dry = c.inFrame[(size_t) k] / kScale;
                        const float wet = c.outFrame[(size_t) k] / kScale;
                        const float outSample = dry + amount * (wet - dry);

                        c.outRing[(size_t) c.outW] = outSample;
                        c.outW = (c.outW + 1) % ring;
                        if (c.outCount < ring) ++c.outCount;
                    }
                }

                float y = 0.f;
                if (c.outCount > 0)
                {
                    y = c.outRing[(size_t) c.outR];
                    c.outR = (c.outR + 1) % ring;
                    --c.outCount;
                }
                data[i] = y;
            }
        }
    #else
        // Should never be reached: neuralReady is false without RNNoise.
        classic.process (block);
    #endif
    }
}
