#pragma once

#include <juce_audio_processors/juce_audio_processors.h>

/**
    Central definition of every plugin parameter.

    Keeping the IDs, ranges and the layout builder in one place means the
    processor, the editor and any preset code all agree on exactly one source
    of truth. Parameter IDs are stable strings — never renumber them once the
    plugin has shipped or you break users' saved sessions.
*/
namespace vox::params
{
    // --- Parameter IDs -----------------------------------------------------
    namespace id
    {
        static constexpr auto inputGain    = "inputGain";
        static constexpr auto outputGain   = "outputGain";
        static constexpr auto mix          = "mix";           // dry/wet
        static constexpr auto bypass       = "bypass";

        static constexpr auto hpfFreq      = "hpfFreq";        // rumble filter

        static constexpr auto gateThresh   = "gateThresh";
        static constexpr auto gateAmount   = "gateAmount";     // 0..1 depth

        static constexpr auto nrAmount     = "nrAmount";       // "AI" denoise amount
        static constexpr auto nrUseNeural  = "nrUseNeural";    // classic vs neural seam

        static constexpr auto warmth       = "warmth";         // low tilt
        static constexpr auto body         = "body";           // low-mid
        static constexpr auto presence     = "presence";       // upper-mid clarity
        static constexpr auto brightness   = "brightness";     // high shelf / air

        static constexpr auto compAmount   = "compAmount";     // macro compression
        static constexpr auto deEssAmount  = "deEssAmount";
    }

    // --- Layout ------------------------------------------------------------
    inline juce::AudioProcessorValueTreeState::ParameterLayout createLayout()
    {
        using namespace juce;
        std::vector<std::unique_ptr<RangedAudioParameter>> p;

        auto dbRange = [] (float lo, float hi)
        {
            return NormalisableRange<float> { lo, hi, 0.01f };
        };

        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::inputGain, 1 }, "Input", dbRange (-24.f, 24.f), 0.f));
        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::outputGain, 1 }, "Output", dbRange (-24.f, 24.f), 0.f));
        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::mix, 1 }, "Mix", NormalisableRange<float> { 0.f, 100.f, 0.1f }, 100.f));
        p.push_back (std::make_unique<AudioParameterBool>(
            ParameterID { id::bypass, 1 }, "Bypass", false));

        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::hpfFreq, 1 }, "HPF",
            NormalisableRange<float> { 20.f, 300.f, 1.f, 0.5f }, 70.f));

        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::gateThresh, 1 }, "Gate Thresh",
            NormalisableRange<float> { -80.f, 0.f, 0.1f }, -55.f));
        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::gateAmount, 1 }, "Gate Depth",
            NormalisableRange<float> { 0.f, 100.f, 0.1f }, 0.f));

        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::nrAmount, 1 }, "Denoise",
            NormalisableRange<float> { 0.f, 100.f, 0.1f }, 0.f));
        p.push_back (std::make_unique<AudioParameterBool>(
            ParameterID { id::nrUseNeural, 1 }, "Neural NR", false));

        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::warmth, 1 }, "Warmth",
            NormalisableRange<float> { -12.f, 12.f, 0.1f }, 0.f));
        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::body, 1 }, "Body",
            NormalisableRange<float> { -12.f, 12.f, 0.1f }, 0.f));
        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::presence, 1 }, "Presence",
            NormalisableRange<float> { -12.f, 12.f, 0.1f }, 0.f));
        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::brightness, 1 }, "Brightness",
            NormalisableRange<float> { -12.f, 12.f, 0.1f }, 0.f));

        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::compAmount, 1 }, "Compression",
            NormalisableRange<float> { 0.f, 100.f, 0.1f }, 0.f));
        p.push_back (std::make_unique<AudioParameterFloat>(
            ParameterID { id::deEssAmount, 1 }, "De-Ess",
            NormalisableRange<float> { 0.f, 100.f, 0.1f }, 0.f));

        return { p.begin(), p.end() };
    }
}
