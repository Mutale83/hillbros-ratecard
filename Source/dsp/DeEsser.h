#pragma once

#include <juce_dsp/juce_dsp.h>

namespace vox::dsp
{
    /**
        Split-band de-esser. A band-pass sidechain (~5–8 kHz) drives gain
        reduction that is applied only to the high band, so sibilance is tamed
        without dulling the whole signal. One "amount" macro sets how aggressive
        the reduction is.
    */
    class DeEsser
    {
    public:
        void prepare (const juce::dsp::ProcessSpec& spec);
        void reset();

        void setAmount (float amount01);

        void process (juce::AudioBuffer<float>& block);

    private:
        double sr = 44100.0;
        float amount = 0.f;
        float thresholdDb = 0.f;
        float attackCoeff = 0.f, releaseCoeff = 0.f;

        // High band split (Linkwitz-Riley-ish via high-pass) + detector band-pass.
        juce::dsp::ProcessorDuplicator<juce::dsp::IIR::Filter<float>,
                                       juce::dsp::IIR::Coefficients<float>> highSplit;
        juce::dsp::ProcessorDuplicator<juce::dsp::IIR::Filter<float>,
                                       juce::dsp::IIR::Coefficients<float>> scBand;

        juce::AudioBuffer<float> highBuf, scBuf;
        std::vector<float> env;   // per-channel detector state
    };
}
