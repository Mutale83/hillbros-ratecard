#pragma once

#include <juce_gui_basics/juce_gui_basics.h>
#include <atomic>

namespace vox::gui
{
    /**
        Lightweight peak level meter. The audio thread pushes a rectified peak
        via setLevel() (a lock-free atomic store); the GUI thread reads and
        decays it on a timer. No allocation or locking on either side.
    */
    class LevelMeter : public juce::Component, private juce::Timer
    {
    public:
        LevelMeter();
        ~LevelMeter() override;

        /** Called from the audio thread — atomic, real-time safe. */
        void pushPeak (float linearPeak) noexcept
        {
            const float cur = peak.load (std::memory_order_relaxed);
            if (linearPeak > cur)
                peak.store (linearPeak, std::memory_order_relaxed);
        }

        void paint (juce::Graphics&) override;

    private:
        void timerCallback() override;

        std::atomic<float> peak { 0.f };
        float display = 0.f;   // GUI-side smoothed value in dB-normalised 0..1
    };
}
