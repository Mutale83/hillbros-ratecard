#pragma once

#include <juce_audio_processors/juce_audio_processors.h>
#include "PluginProcessor.h"
#include "gui/VoxLookAndFeel.h"
#include "gui/LevelMeter.h"
#include "gui/SpectrumAnalyzer.h"

class VoxAIAudioProcessorEditor : public juce::AudioProcessorEditor,
                                  private juce::Timer
{
public:
    explicit VoxAIAudioProcessorEditor (VoxAIAudioProcessor&);
    ~VoxAIAudioProcessorEditor() override;

    void paint (juce::Graphics&) override;
    void resized() override;

private:
    using SliderAttach = juce::AudioProcessorValueTreeState::SliderAttachment;
    using ButtonAttach = juce::AudioProcessorValueTreeState::ButtonAttachment;

    void timerCallback() override;
    void applyPreset (int index);

    struct Knob
    {
        juce::Slider slider;
        juce::Label  label;
        std::unique_ptr<SliderAttach> attach;
    };

    Knob& addKnob (const juce::String& paramID, const juce::String& name);

    VoxAIAudioProcessor& proc;
    vox::gui::VoxLookAndFeel lnf;

    vox::gui::SpectrumAnalyzer spectrum;
    vox::gui::LevelMeter inMeter, outMeter;

    juce::OwnedArray<Knob> knobs;   // holds all rotary controls

    juce::ToggleButton bypassButton { "Bypass" };
    juce::ToggleButton neuralButton { "Neural NR" };
    std::unique_ptr<ButtonAttach> bypassAttach, neuralAttach;

    juce::TextButton aButton { "A" }, bButton { "B" }, copyButton { "Copy \xE2\x86\x92" };
    juce::ComboBox presetBox;

    std::array<float, 4096> scopeTransfer {};

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (VoxAIAudioProcessorEditor)
};
