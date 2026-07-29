#pragma once

#include <juce_dsp/juce_dsp.h>

namespace vox::dsp
{
    /**
        Voice tone shaper built from juce::dsp IIR filters:

          HPF        - rumble / proximity clean-up (variable freq)
          Warmth     - low shelf   (~180 Hz)
          Body       - low-mid bell (~300 Hz)
          Presence   - upper-mid bell (~4 kHz)  -> clarity / intelligibility
          Brightness - high shelf   (~9 kHz)    -> air

        Each user control maps to filter gain in dB. Coefficients are only
        rebuilt when a value actually changes, so audio-thread cost is just the
        filter processing itself.
    */
    class ParametricEQ
    {
    public:
        void prepare (const juce::dsp::ProcessSpec& spec);
        void reset();

        void setHpfFreq   (float hz);
        void setWarmth    (float db);
        void setBody      (float db);
        void setPresence  (float db);
        void setBrightness(float db);

        void process (juce::dsp::AudioBlock<float>& block);

    private:
        using Filter = juce::dsp::ProcessorDuplicator<
                           juce::dsp::IIR::Filter<float>,
                           juce::dsp::IIR::Coefficients<float>>;

        double sr = 44100.0;

        Filter hpf, warmth, body, presence, brightness;

        float hpfFreqHz = 70.f;
        float warmthDb = 0.f, bodyDb = 0.f, presenceDb = 0.f, brightDb = 0.f;

        void updateHpf();
        void updateWarmth();
        void updateBody();
        void updatePresence();
        void updateBrightness();
    };
}
