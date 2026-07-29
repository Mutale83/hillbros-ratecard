#include "NoiseGate.h"

namespace vox::dsp
{
    static inline float msToCoeff (float ms, double sr)
    {
        return std::exp (-1.0f / (float) ((ms * 0.001) * sr));
    }

    void NoiseGate::prepare (double sampleRate)
    {
        sr = sampleRate;
        attackCoeff  = msToCoeff (2.0f,  sr);
        releaseCoeff = msToCoeff (120.0f, sr);
        holdSamples  = (int) (0.010 * sr);   // 10 ms hold
        reset();
    }

    void NoiseGate::reset()
    {
        env = 1.f;
        holdCounter = 0;
    }

    void NoiseGate::process (juce::AudioBuffer<float>& block)
    {
        if (depth <= 0.0001f)
            return;

        const int numCh = block.getNumChannels();
        const int n = block.getNumSamples();
        const float threshLin = juce::Decibels::decibelsToGain (thresholdDb);
        const float floorGain = juce::Decibels::decibelsToGain (-60.0f * depth);

        for (int i = 0; i < n; ++i)
        {
            // Linked detector: peak across channels.
            float detect = 0.f;
            for (int ch = 0; ch < numCh; ++ch)
                detect = juce::jmax (detect, std::abs (block.getSample (ch, i)));

            const float target = (detect >= threshLin) ? 1.0f : floorGain;

            if (target >= env)
            {
                // Opening.
                env = attackCoeff * env + (1.0f - attackCoeff) * target;
                holdCounter = holdSamples;
            }
            else if (holdCounter > 0)
            {
                --holdCounter;   // keep open during hold
            }
            else
            {
                env = releaseCoeff * env + (1.0f - releaseCoeff) * target;
            }

            for (int ch = 0; ch < numCh; ++ch)
                block.getWritePointer (ch)[i] *= env;
        }
    }
}
