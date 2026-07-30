#pragma once

#include <juce_gui_basics/juce_gui_basics.h>

namespace vox::gui
{
    /** Dark, modern rotary/slider styling for a professional plugin look. */
    class VoxLookAndFeel : public juce::LookAndFeel_V4
    {
    public:
        VoxLookAndFeel();

        void drawRotarySlider (juce::Graphics&, int x, int y, int w, int h,
                               float sliderPos, float startAngle, float endAngle,
                               juce::Slider&) override;

        void drawLinearSlider (juce::Graphics&, int x, int y, int w, int h,
                               float sliderPos, float minPos, float maxPos,
                               juce::Slider::SliderStyle, juce::Slider&) override;

        juce::Label* createSliderTextBox (juce::Slider&) override;

        // Palette shared across the UI.
        static const juce::Colour bg;
        static const juce::Colour panel;
        static const juce::Colour accent;
        static const juce::Colour accentDim;
        static const juce::Colour text;
        static const juce::Colour textDim;
    };
}
