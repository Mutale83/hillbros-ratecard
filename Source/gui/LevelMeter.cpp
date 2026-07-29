#include "LevelMeter.h"
#include "VoxLookAndFeel.h"
#include <juce_audio_basics/juce_audio_basics.h>

namespace vox::gui
{
    LevelMeter::LevelMeter()  { startTimerHz (30); }
    LevelMeter::~LevelMeter() { stopTimer(); }

    void LevelMeter::timerCallback()
    {
        const float p = peak.exchange (0.f, std::memory_order_relaxed);
        const float db = juce::Decibels::gainToDecibels (p, -60.f);
        const float norm = juce::jlimit (0.f, 1.f, (db + 60.f) / 60.f);

        if (norm > display) display = norm;                 // instant attack
        else                display += (norm - display) * 0.2f;  // smooth release
        repaint();
    }

    void LevelMeter::paint (juce::Graphics& g)
    {
        auto r = getLocalBounds().toFloat();
        g.setColour (VoxLookAndFeel::panel.brighter (0.1f));
        g.fillRoundedRectangle (r, 3.f);

        const float filled = r.getHeight() * display;
        auto bar = r.withTop (r.getBottom() - filled).reduced (2.f, 0.f);

        juce::Colour c = display < 0.75f ? VoxLookAndFeel::accent
                       : display < 0.9f  ? juce::Colour (0xffe0c341)
                                         : juce::Colour (0xffe0554b);
        g.setColour (c);
        g.fillRoundedRectangle (bar, 2.f);
    }
}
