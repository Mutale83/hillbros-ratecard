#pragma once

#include <juce_audio_processors/juce_audio_processors.h>
#include <juce_dsp/juce_dsp.h>
#include <atomic>
#include <array>

#include "Parameters.h"
#include "dsp/NoiseGate.h"
#include "dsp/Compressor.h"
#include "dsp/ParametricEQ.h"
#include "dsp/DeEsser.h"
#include "ai/NeuralDenoiser.h"

/**
    VoxAI processor — the real-time voice channel strip.

    Signal chain (see docs/FEASIBILITY.md §4):
        input gain → HPF+tone EQ pre → gate → denoise → EQ tone → comp → de-ess
                   → output gain, wrapped in latency-compensated dry/wet.
*/
class VoxAIAudioProcessor : public juce::AudioProcessor
{
public:
    VoxAIAudioProcessor();
    ~VoxAIAudioProcessor() override = default;

    void prepareToPlay (double sampleRate, int samplesPerBlock) override;
    void releaseResources() override {}
    bool isBusesLayoutSupported (const BusesLayout&) const override;
    void processBlock (juce::AudioBuffer<float>&, juce::MidiBuffer&) override;

    juce::AudioProcessorEditor* createEditor() override;
    bool hasEditor() const override { return true; }

    const juce::String getName() const override { return "VoxAI"; }
    bool acceptsMidi() const override  { return false; }
    bool producesMidi() const override { return false; }
    bool isMidiEffect() const override { return false; }
    double getTailLengthSeconds() const override { return 0.0; }

    int getNumPrograms() override { return 1; }
    int getCurrentProgram() override { return 0; }
    void setCurrentProgram (int) override {}
    const juce::String getProgramName (int) override { return {}; }
    void changeProgramName (int, const juce::String&) override {}

    void getStateInformation (juce::MemoryBlock&) override;
    void setStateInformation (const void*, int) override;

    juce::AudioProcessorValueTreeState apvts;

    // --- A/B compare -------------------------------------------------------
    void storeToSlot (bool slotB);   // capture current params into A or B
    void recallSlot  (bool slotB);   // apply a stored slot to live params
    bool isSlotB() const { return currentSlotB; }

    // --- GUI data taps (lock-free, single consumer = editor) ---------------
    float getInputPeak()  noexcept { return inputPeak.exchange  (0.f, std::memory_order_relaxed); }
    float getOutputPeak() noexcept { return outputPeak.exchange (0.f, std::memory_order_relaxed); }
    int   readScope (float* dst, int maxN) noexcept;

private:
    void updateParameters();

    // DSP blocks.
    vox::dsp::ParametricEQ eq;
    vox::dsp::NoiseGate    gate;
    vox::ai::NeuralDenoiser denoiser;
    vox::dsp::Compressor   comp;
    vox::dsp::DeEsser      deEss;

    juce::dsp::DelayLine<float> dryDelay { 96000 };  // latency-align the dry path
    juce::AudioBuffer<float> dryBuffer;

    juce::LinearSmoothedValue<float> inGain, outGain, wetMix;

    double sampleRate = 44100.0;
    int latencySamples = 0;

    // A/B snapshots.
    juce::ValueTree slotA, slotB;
    bool currentSlotB = false;

    // GUI taps.
    std::atomic<float> inputPeak { 0.f };
    std::atomic<float> outputPeak { 0.f };
    static constexpr int scopeSize = 1 << 13;   // 8192
    std::array<float, scopeSize> scopeBuf {};
    std::atomic<int> scopeWrite { 0 };
    int scopeRead = 0;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (VoxAIAudioProcessor)
};
