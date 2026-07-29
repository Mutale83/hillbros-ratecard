#include "VoxLookAndFeel.h"

namespace vox::gui
{
    const juce::Colour VoxLookAndFeel::bg        { 0xff14161a };
    const juce::Colour VoxLookAndFeel::panel     { 0xff1d2026 };
    const juce::Colour VoxLookAndFeel::accent    { 0xff36d1c4 };
    const juce::Colour VoxLookAndFeel::accentDim { 0xff1f6f69 };
    const juce::Colour VoxLookAndFeel::text      { 0xffe6e9ef };
    const juce::Colour VoxLookAndFeel::textDim   { 0xff8a90a0 };

    VoxLookAndFeel::VoxLookAndFeel()
    {
        setColour (juce::Slider::textBoxTextColourId, text);
        setColour (juce::Slider::textBoxOutlineColourId, juce::Colours::transparentBlack);
        setColour (juce::Label::textColourId, text);
        setColour (juce::ComboBox::backgroundColourId, panel);
        setColour (juce::ComboBox::textColourId, text);
        setColour (juce::ComboBox::outlineColourId, accentDim);
        setColour (juce::PopupMenu::backgroundColourId, panel);
        setColour (juce::PopupMenu::highlightedBackgroundColourId, accentDim);
        setColour (juce::TextButton::buttonColourId, panel);
        setColour (juce::TextButton::textColourOnId, accent);
        setColour (juce::TextButton::textColourOffId, textDim);
    }

    void VoxLookAndFeel::drawRotarySlider (juce::Graphics& g, int x, int y, int w, int h,
                                           float sliderPos, float startAngle, float endAngle,
                                           juce::Slider&)
    {
        const auto bounds = juce::Rectangle<float> ((float) x, (float) y, (float) w, (float) h).reduced (6.f);
        const auto radius = juce::jmin (bounds.getWidth(), bounds.getHeight()) * 0.5f;
        const auto centre = bounds.getCentre();
        const auto angle  = startAngle + sliderPos * (endAngle - startAngle);
        const float thickness = radius * 0.18f;

        // Track.
        juce::Path track;
        track.addCentredArc (centre.x, centre.y, radius - thickness, radius - thickness,
                             0.f, startAngle, endAngle, true);
        g.setColour (panel.brighter (0.15f));
        g.strokePath (track, juce::PathStrokeType (thickness, juce::PathStrokeType::curved,
                                                   juce::PathStrokeType::rounded));

        // Value arc.
        juce::Path value;
        value.addCentredArc (centre.x, centre.y, radius - thickness, radius - thickness,
                             0.f, startAngle, angle, true);
        g.setColour (accent);
        g.strokePath (value, juce::PathStrokeType (thickness, juce::PathStrokeType::curved,
                                                   juce::PathStrokeType::rounded));

        // Knob body.
        const float knobR = radius - thickness * 2.2f;
        g.setColour (panel.brighter (0.3f));
        g.fillEllipse (centre.x - knobR, centre.y - knobR, knobR * 2, knobR * 2);

        // Pointer.
        juce::Path pointer;
        pointer.addRoundedRectangle (-thickness * 0.25f, -knobR, thickness * 0.5f, knobR * 0.6f, 1.5f);
        pointer.applyTransform (juce::AffineTransform::rotation (angle).translated (centre));
        g.setColour (text);
        g.fillPath (pointer);
    }

    void VoxLookAndFeel::drawLinearSlider (juce::Graphics& g, int x, int y, int w, int h,
                                           float sliderPos, float, float,
                                           juce::Slider::SliderStyle style, juce::Slider& s)
    {
        if (style == juce::Slider::LinearVertical || style == juce::Slider::LinearBarVertical)
        {
            auto track = juce::Rectangle<float> ((float) x + (float) w * 0.5f - 2.f, (float) y, 4.f, (float) h);
            g.setColour (panel.brighter (0.15f));
            g.fillRoundedRectangle (track, 2.f);

            auto filled = track.withTop (sliderPos);
            g.setColour (accent);
            g.fillRoundedRectangle (filled, 2.f);

            g.setColour (text);
            g.fillEllipse (track.getCentreX() - 7.f, sliderPos - 7.f, 14.f, 14.f);
        }
        else
        {
            LookAndFeel_V4::drawLinearSlider (g, x, y, w, h, sliderPos, 0.f, 0.f, style, s);
        }
    }

    juce::Label* VoxLookAndFeel::createSliderTextBox (juce::Slider& s)
    {
        auto* l = LookAndFeel_V4::createSliderTextBox (s);
        l->setFont (juce::Font (12.f));
        l->setJustificationType (juce::Justification::centred);
        return l;
    }
}
