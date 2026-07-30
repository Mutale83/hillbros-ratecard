#include "SpectralDenoiser.h"
#include <cstring>

namespace vox::dsp
{
    void SpectralDenoiser::prepare (double sampleRate, int numChannels, int /*maxBlock*/)
    {
        sr = sampleRate;
        numCh = juce::jmax (1, numChannels);

        // Hann analysis+synthesis window.
        window.resize (fftSize);
        for (int n = 0; n < fftSize; ++n)
            window[(size_t) n] = 0.5f * (1.0f - std::cos (2.0f * juce::MathConstants<float>::pi
                                                          * (float) n / (float) fftSize));

        // COLA correction: sum of squared window over the overlapping hops.
        float c = 0.f;
        for (int m = 0; m * hopSize < fftSize; ++m)
        {
            const float w = window[(size_t) ((m * hopSize) % fftSize)];
            c += w * w;
        }
        windowCorrection = (c > 0.f) ? 1.0f / c : 1.0f;

        fftData.assign ((size_t) fftSize * 2, 0.f);

        chans.clear();
        chans.resize ((size_t) numCh);
        for (auto& st : chans)
        {
            st.history.assign (fftSize, 0.f);
            st.accum.assign   (fftSize, 0.f);
            st.hopIn.assign   (hopSize, 0.f);
            st.outRing.assign ((size_t) fftSize * 2, 0.f);
            st.noiseFloor.assign ((size_t) (fftSize / 2 + 1), 1.0e-4f);
            st.fill = 0; st.outW = 0; st.outR = 0; st.outCount = 0;
        }
    }

    void SpectralDenoiser::reset()
    {
        for (auto& st : chans)
        {
            std::fill (st.history.begin(), st.history.end(), 0.f);
            std::fill (st.accum.begin(),   st.accum.end(),   0.f);
            std::fill (st.hopIn.begin(),   st.hopIn.end(),   0.f);
            std::fill (st.outRing.begin(), st.outRing.end(), 0.f);
            std::fill (st.noiseFloor.begin(), st.noiseFloor.end(), 1.0e-4f);
            st.fill = 0; st.outW = 0; st.outR = 0; st.outCount = 0;
        }
    }

    void SpectralDenoiser::process (juce::AudioBuffer<float>& block)
    {
        if (amount <= 0.0001f)
            return;

        const int n = block.getNumSamples();
        const int chToDo = juce::jmin (block.getNumChannels(), (int) chans.size());
        const int ringSize = fftSize * 2;

        for (int ch = 0; ch < chToDo; ++ch)
        {
            auto& st = chans[(size_t) ch];
            auto* data = block.getWritePointer (ch);

            for (int i = 0; i < n; ++i)
            {
                st.hopIn[(size_t) st.fill++] = data[i];

                if (st.fill == hopSize)
                {
                    st.fill = 0;

                    // Slide the analysis window: drop the oldest hop, append the new one.
                    std::memmove (st.history.data(),
                                  st.history.data() + hopSize,
                                  sizeof (float) * (size_t) (fftSize - hopSize));
                    std::memcpy (st.history.data() + (fftSize - hopSize),
                                 st.hopIn.data(),
                                 sizeof (float) * (size_t) hopSize);

                    processFrame (st);   // overlap-adds synthesis into st.accum

                    // Emit the now-complete first hopSize output samples.
                    for (int k = 0; k < hopSize; ++k)
                    {
                        st.outRing[(size_t) st.outW] = st.accum[(size_t) k];
                        st.outW = (st.outW + 1) % ringSize;
                        if (st.outCount < ringSize) ++st.outCount;
                    }

                    // Shift the accumulator down by one hop and clear the tail.
                    std::memmove (st.accum.data(),
                                  st.accum.data() + hopSize,
                                  sizeof (float) * (size_t) (fftSize - hopSize));
                    std::fill (st.accum.end() - hopSize, st.accum.end(), 0.f);
                }

                // Pull one ready output sample (0 during the initial latency priming).
                float y = 0.f;
                if (st.outCount > 0)
                {
                    y = st.outRing[(size_t) st.outR];
                    st.outR = (st.outR + 1) % ringSize;
                    --st.outCount;
                }
                data[i] = y;
            }
        }
    }

    void SpectralDenoiser::processFrame (ChannelState& st)
    {
        const int half = fftSize / 2;

        // Windowed analysis frame → real FFT input.
        for (int k = 0; k < fftSize; ++k)
            fftData[(size_t) k] = st.history[(size_t) k] * window[(size_t) k];
        std::fill (fftData.begin() + fftSize, fftData.end(), 0.f);

        fft.performRealOnlyForwardTransform (fftData.data());

        // Spectral subtraction with a slow per-bin noise-floor tracker.
        const float overSub   = 1.0f + amount * 2.0f;   // oversubtraction factor
        const float floorGain = 0.05f;                   // residual to limit musical noise

        for (int b = 0; b <= half; ++b)
        {
            const float re = fftData[(size_t) (2 * b)];
            const float im = fftData[(size_t) (2 * b + 1)];
            const float mag = std::sqrt (re * re + im * im);

            float& nf = st.noiseFloor[(size_t) b];
            if (mag < nf)  nf = 0.9f * nf + 0.1f * mag;   // adapt down in quiet passages
            else           nf *= 1.0005f;                 // slow rise to track drift
            nf = juce::jmin (nf, mag + 1.0e-6f);

            const float thr = nf * overSub;
            float g = (mag > thr) ? (mag - thr) / (mag + 1.0e-9f) : 0.0f;
            g = juce::jmax (g, floorGain);

            const float finalGain = 1.0f - amount * (1.0f - g);

            fftData[(size_t) (2 * b)]     = re * finalGain;
            fftData[(size_t) (2 * b + 1)] = im * finalGain;

            // Maintain Hermitian symmetry for the inverse transform.
            if (b > 0 && b < half)
            {
                const int m = fftSize - b;
                fftData[(size_t) (2 * m)]     *= finalGain;
                fftData[(size_t) (2 * m + 1)] *= finalGain;
            }
        }

        fft.performRealOnlyInverseTransform (fftData.data());

        // Windowed synthesis + overlap-add into the accumulator.
        for (int k = 0; k < fftSize; ++k)
            st.accum[(size_t) k] += fftData[(size_t) k] * window[(size_t) k] * windowCorrection;
    }
}
