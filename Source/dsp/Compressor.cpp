#include "Compressor.h"

namespace vox::dsp
{
    static inline float msToCoeff (float ms, double sr)
    {
        return std::exp (-1.0f / (float) ((ms * 0.001) * sr));
    }

    void Compressor::prepare (double sampleRate)
    {
        sr = sampleRate;
        attackCoeff  = msToCoeff (8.0f,   sr);
        releaseCoeff = msToCoeff (140.0f, sr);
        reset();
    }

    void Compressor::reset()
    {
        envDb = -100.f;
    }

    void Compressor::setAmount (float amount01)
    {
        amount = juce::jlimit (0.f, 1.f, amount01);

        // One macro drives a voice-friendly curve:
        //  threshold sweeps down, ratio and makeup rise with amount.
        thresholdDb = juce::jmap (amount, 0.f, 1.f,  -6.f, -30.f);
        ratio       = juce::jmap (amount, 0.f, 1.f,   1.f,   6.f);
        makeupDb    = juce::jmap (amount, 0.f, 1.f,   0.f,   9.f);
    }

    void Compressor::process (juce::AudioBuffer<float>& block)
    {
        if (amount <= 0.0001f)
            return;

        const int numCh = block.getNumChannels();
        const int n = block.getNumSamples();
        const float makeupLin = juce::Decibels::decibelsToGain (makeupDb);
        const float halfKnee = kneeDb * 0.5f;

        for (int i = 0; i < n; ++i)
        {
            // Linked detector on peak level.
            float peak = 0.f;
            for (int ch = 0; ch < numCh; ++ch)
                peak = juce::jmax (peak, std::abs (block.getSample (ch, i)));

            const float inDb = juce::Decibels::gainToDecibels (peak, -100.f);

            const float coeff = (inDb > envDb) ? attackCoeff : releaseCoeff;
            envDb = coeff * envDb + (1.0f - coeff) * inDb;

            // Soft-knee static curve → desired output level.
            float overDb = envDb - thresholdDb;
            float gainReductionDb = 0.f;

            if (overDb <= -halfKnee)
            {
                gainReductionDb = 0.f;
            }
            else if (overDb >= halfKnee)
            {
                gainReductionDb = overDb - overDb / ratio;
            }
            else
            {
                // Quadratic knee interpolation.
                const float x = overDb + halfKnee;      // 0..kneeDb
                const float slope = (1.0f - 1.0f / ratio);
                gainReductionDb = slope * (x * x) / (2.0f * kneeDb);
            }

            const float gain = juce::Decibels::decibelsToGain (-gainReductionDb) * makeupLin;

            for (int ch = 0; ch < numCh; ++ch)
                block.getWritePointer (ch)[i] *= gain;
        }
    }
}
