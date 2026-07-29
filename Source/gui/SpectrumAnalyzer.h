#pragma once

#include <juce_dsp/juce_dsp.h>
#include <juce_gui_basics/juce_gui_basics.h>
#include <atomic>
#include <array>

namespace vox::gui
{
    /**
        Real-time FFT spectrum display. The audio thread pushes samples into a
        lock-free single-producer FIFO; the GUI thread drains it, runs an FFT on
        a timer, and paints a smoothed magnitude curve. This is the standard
        JUCE analyser pattern (SimpleFFTDemo) adapted to a component.
    */
    class SpectrumAnalyzer : public juce::Component, private juce::Timer
    {
    public:
        SpectrumAnalyzer();
        ~SpectrumAnalyzer() override;

        /** Audio thread: push one mono sample (mix of channels). RT-safe. */
        void pushSample (float s) noexcept
        {
            fifo[(size_t) writeIdx.load (std::memory_order_relaxed)] = s;
            auto next = (writeIdx.load (std::memory_order_relaxed) + 1) % fifoSize;
            writeIdx.store (next, std::memory_order_release);
        }

        void paint (juce::Graphics&) override;

    private:
        void timerCallback() override;
        void computeFrameIfReady();

        static constexpr int fftOrder = 11;            // 2048
        static constexpr int fftSize  = 1 << fftOrder;
        static constexpr int fifoSize = fftSize * 2;

        juce::dsp::FFT fft { fftOrder };
        juce::dsp::WindowingFunction<float> win { (size_t) fftSize,
                                                  juce::dsp::WindowingFunction<float>::hann };

        std::array<float, fifoSize> fifo {};
        std::atomic<int> writeIdx { 0 };
        int readIdx = 0;

        std::array<float, fftSize * 2> fftData {};
        std::array<float, fftSize / 2> scope {};       // smoothed dB magnitudes 0..1
    };
}
