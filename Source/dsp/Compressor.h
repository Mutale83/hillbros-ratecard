#pragma once

#include <juce_audio_basics/juce_audio_basics.h>

namespace vox::dsp
{
    /**
        Feed-forward peak/RMS-ish compressor with soft knee and auto makeup.

        Exposed to the user as a single "Compression" macro (0..100). Internally
        that macro moves threshold, ratio and makeup together along a curve tuned
        for voice, so a non-engineer gets a musical result from one control while
        the code stays a real compressor you can expand into full controls later.
    */
    class Compressor
    {
    public:
        void prepare (double sampleRate);
        void reset();

        /** macro 0..1 */
        void setAmount (float amount01);

        void process (juce::AudioBuffer<float>& block);

    private:
        double sr = 44100.0;
        float amount = 0.f;

        float thresholdDb = 0.f;
        float ratio = 1.f;
        float makeupDb = 0.f;
        float kneeDb = 6.f;

        float attackCoeff = 0.f, releaseCoeff = 0.f;
        float envDb = -100.f;   // detector in dB
    };
}
