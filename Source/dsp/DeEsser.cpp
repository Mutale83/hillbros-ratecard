#include "DeEsser.h"

namespace vox::dsp
{
    static inline float msToCoeff (float ms, double sr)
    {
        return std::exp (-1.0f / (float) ((ms * 0.001) * sr));
    }

    void DeEsser::prepare (const juce::dsp::ProcessSpec& spec)
    {
        sr = spec.sampleRate;
        highSplit.prepare (spec);
        scBand.prepare (spec);

        *highSplit.state = *juce::dsp::IIR::Coefficients<float>::makeHighPass (sr, 6000.f, 0.707f);
        *scBand.state    = *juce::dsp::IIR::Coefficients<float>::makeBandPass (sr, 6500.f, 1.2f);

        highBuf.setSize ((int) spec.numChannels, (int) spec.maximumBlockSize);
        scBuf.setSize   ((int) spec.numChannels, (int) spec.maximumBlockSize);
        env.assign (spec.numChannels, -100.f);

        attackCoeff  = msToCoeff (1.0f,  sr);
        releaseCoeff = msToCoeff (60.0f, sr);
    }

    void DeEsser::reset()
    {
        highSplit.reset();
        scBand.reset();
        std::fill (env.begin(), env.end(), -100.f);
    }

    void DeEsser::setAmount (float amount01)
    {
        amount = juce::jlimit (0.f, 1.f, amount01);
        thresholdDb = juce::jmap (amount, 0.f, 1.f, -18.f, -42.f);
    }

    void DeEsser::process (juce::AudioBuffer<float>& block)
    {
        if (amount <= 0.0001f)
            return;

        const int numCh = block.getNumChannels();
        const int n = block.getNumSamples();

        // Copy input into the high-band and sidechain buffers.
        for (int ch = 0; ch < numCh; ++ch)
        {
            highBuf.copyFrom (ch, 0, block, ch, 0, n);
            scBuf.copyFrom  (ch, 0, block, ch, 0, n);
        }

        juce::dsp::AudioBlock<float> highBlk (highBuf); highBlk = highBlk.getSubBlock (0, (size_t) n);
        juce::dsp::AudioBlock<float> scBlk   (scBuf);   scBlk   = scBlk.getSubBlock   (0, (size_t) n);

        { juce::dsp::ProcessContextReplacing<float> c (highBlk); highSplit.process (c); }
        { juce::dsp::ProcessContextReplacing<float> c (scBlk);   scBand.process   (c); }

        const float ratio = 4.0f;

        for (int ch = 0; ch < numCh; ++ch)
        {
            auto* out    = block.getWritePointer (ch);
            auto* high   = highBuf.getReadPointer (ch);
            auto* sc     = scBuf.getReadPointer  (ch);
            float e      = env[(size_t) ch];

            for (int i = 0; i < n; ++i)
            {
                const float scDb = juce::Decibels::gainToDecibels (std::abs (sc[i]), -100.f);
                const float coeff = (scDb > e) ? attackCoeff : releaseCoeff;
                e = coeff * e + (1.0f - coeff) * scDb;

                float grDb = 0.f;
                if (e > thresholdDb)
                    grDb = (e - thresholdDb) * (1.0f - 1.0f / ratio);

                const float grLin = juce::Decibels::decibelsToGain (-grDb);
                // Reduce only the high band's contribution: out = full - high*(1-gr)
                out[i] -= high[i] * (1.0f - grLin);
            }

            env[(size_t) ch] = e;
        }
    }
}
