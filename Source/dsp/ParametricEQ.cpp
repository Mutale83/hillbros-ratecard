#include "ParametricEQ.h"

namespace vox::dsp
{
    void ParametricEQ::prepare (const juce::dsp::ProcessSpec& spec)
    {
        sr = spec.sampleRate;
        for (auto* f : { &hpf, &warmth, &body, &presence, &brightness })
            f->prepare (spec);

        updateHpf(); updateWarmth(); updateBody(); updatePresence(); updateBrightness();
    }

    void ParametricEQ::reset()
    {
        for (auto* f : { &hpf, &warmth, &body, &presence, &brightness })
            f->reset();
    }

    void ParametricEQ::setHpfFreq (float hz)
    {
        if (! juce::approximatelyEqual (hz, hpfFreqHz)) { hpfFreqHz = hz; updateHpf(); }
    }
    void ParametricEQ::setWarmth (float db)
    {
        if (! juce::approximatelyEqual (db, warmthDb)) { warmthDb = db; updateWarmth(); }
    }
    void ParametricEQ::setBody (float db)
    {
        if (! juce::approximatelyEqual (db, bodyDb)) { bodyDb = db; updateBody(); }
    }
    void ParametricEQ::setPresence (float db)
    {
        if (! juce::approximatelyEqual (db, presenceDb)) { presenceDb = db; updatePresence(); }
    }
    void ParametricEQ::setBrightness (float db)
    {
        if (! juce::approximatelyEqual (db, brightDb)) { brightDb = db; updateBrightness(); }
    }

    void ParametricEQ::updateHpf()
    {
        *hpf.state = *juce::dsp::IIR::Coefficients<float>::makeHighPass (sr, hpfFreqHz, 0.707f);
    }
    void ParametricEQ::updateWarmth()
    {
        *warmth.state = *juce::dsp::IIR::Coefficients<float>::makeLowShelf (
            sr, 180.f, 0.707f, juce::Decibels::decibelsToGain (warmthDb));
    }
    void ParametricEQ::updateBody()
    {
        *body.state = *juce::dsp::IIR::Coefficients<float>::makePeakFilter (
            sr, 320.f, 0.9f, juce::Decibels::decibelsToGain (bodyDb));
    }
    void ParametricEQ::updatePresence()
    {
        *presence.state = *juce::dsp::IIR::Coefficients<float>::makePeakFilter (
            sr, 4000.f, 0.8f, juce::Decibels::decibelsToGain (presenceDb));
    }
    void ParametricEQ::updateBrightness()
    {
        *brightness.state = *juce::dsp::IIR::Coefficients<float>::makeHighShelf (
            sr, 9000.f, 0.707f, juce::Decibels::decibelsToGain (brightDb));
    }

    void ParametricEQ::process (juce::dsp::AudioBlock<float>& block)
    {
        juce::dsp::ProcessContextReplacing<float> ctx (block);
        hpf.process (ctx);
        warmth.process (ctx);
        body.process (ctx);
        presence.process (ctx);
        brightness.process (ctx);
    }
}
