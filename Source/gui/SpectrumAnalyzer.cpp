#include "SpectrumAnalyzer.h"
#include "VoxLookAndFeel.h"

namespace vox::gui
{
    SpectrumAnalyzer::SpectrumAnalyzer()  { startTimerHz (30); }
    SpectrumAnalyzer::~SpectrumAnalyzer() { stopTimer(); }

    void SpectrumAnalyzer::timerCallback()
    {
        computeFrameIfReady();
        repaint();
    }

    void SpectrumAnalyzer::computeFrameIfReady()
    {
        const int w = writeIdx.load (std::memory_order_acquire);

        int available = w - readIdx;
        if (available < 0) available += fifoSize;
        if (available < fftSize)
            return;

        // Copy the most recent fftSize samples out of the FIFO.
        std::fill (fftData.begin(), fftData.end(), 0.f);
        int idx = (w - fftSize + fifoSize) % fifoSize;
        for (int i = 0; i < fftSize; ++i)
        {
            fftData[(size_t) i] = fifo[(size_t) idx];
            idx = (idx + 1) % fifoSize;
        }
        readIdx = w;

        win.multiplyWithWindowingTable (fftData.data(), (size_t) fftSize);
        fft.performFrequencyOnlyForwardTransform (fftData.data());

        // Map to a smoothed, log-ish scope.
        const float mindB = -90.f, maxdB = 0.f;
        for (int i = 0; i < fftSize / 2; ++i)
        {
            const float mag = fftData[(size_t) i] / (float) fftSize;
            const float db = juce::Decibels::gainToDecibels (mag, mindB);
            const float norm = juce::jlimit (0.f, 1.f, juce::jmap (db, mindB, maxdB, 0.f, 1.f));
            scope[(size_t) i] = scope[(size_t) i] * 0.6f + norm * 0.4f;   // temporal smoothing
        }
    }

    void SpectrumAnalyzer::paint (juce::Graphics& g)
    {
        auto r = getLocalBounds().toFloat();
        g.setColour (VoxLookAndFeel::panel);
        g.fillRoundedRectangle (r, 4.f);

        const int bins = fftSize / 2;
        juce::Path curve;
        curve.startNewSubPath (r.getX(), r.getBottom());

        for (int i = 1; i < bins; ++i)
        {
            // Log frequency mapping across the width.
            const float prop = std::log10 (1.0f + 9.0f * (float) i / (float) bins);
            const float x = r.getX() + prop * r.getWidth();
            const float y = r.getBottom() - scope[(size_t) i] * r.getHeight();
            curve.lineTo (x, y);
        }
        curve.lineTo (r.getRight(), r.getBottom());
        curve.closeSubPath();

        g.setColour (VoxLookAndFeel::accent.withAlpha (0.25f));
        g.fillPath (curve);
        g.setColour (VoxLookAndFeel::accent);
        g.strokePath (curve, juce::PathStrokeType (1.5f));
    }
}
