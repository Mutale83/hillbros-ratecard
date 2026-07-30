#pragma once

#include <juce_audio_basics/juce_audio_basics.h>

namespace vox::dsp
{
    /**
        Simple, click-free downward noise gate with attack / hold / release
        envelope smoothing. Operates on a linked stereo envelope so the image
        stays centred. Depth lets the gate act as a soft expander rather than a
        hard cut, which is what you usually want on voice.
    */
    class NoiseGate
    {
    public:
        void prepare (double sampleRate);
        void reset();

        void setThresholdDb (float db)  { thresholdDb = db; }
        /** 0 = no attenuation, 1 = full gate (up to ~60 dB of duck). */
        void setDepth (float d)         { depth = juce::jlimit (0.f, 1.f, d); }

        /** Processes in place; block is [channels][numSamples]. */
        void process (juce::AudioBuffer<float>& block);

    private:
        double sr = 44100.0;
        float thresholdDb = -55.f;
        float depth = 0.f;

        float env = 0.f;       // current gain reduction (linear, 0..1, 1 = open)
        float attackCoeff = 0.f, releaseCoeff = 0.f;
        int   holdSamples = 0, holdCounter = 0;
    };
}
