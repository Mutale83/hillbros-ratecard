#include "PluginProcessor.h"
#include "PluginEditor.h"

using namespace vox;

VoxAIAudioProcessor::VoxAIAudioProcessor()
    : AudioProcessor (BusesProperties()
          .withInput  ("Input",  juce::AudioChannelSet::stereo(), true)
          .withOutput ("Output", juce::AudioChannelSet::stereo(), true)),
      apvts (*this, nullptr, "PARAMS", params::createLayout())
{
}

bool VoxAIAudioProcessor::isBusesLayoutSupported (const BusesLayout& layouts) const
{
    const auto& in  = layouts.getMainInputChannelSet();
    const auto& out = layouts.getMainOutputChannelSet();
    if (in != out) return false;
    return in == juce::AudioChannelSet::mono() || in == juce::AudioChannelSet::stereo();
}

void VoxAIAudioProcessor::prepareToPlay (double sr, int samplesPerBlock)
{
    sampleRate = sr;
    const auto numCh = (juce::uint32) juce::jmax (getTotalNumInputChannels(), 1);

    juce::dsp::ProcessSpec spec { sr, (juce::uint32) samplesPerBlock, numCh };

    eq.prepare (spec);
    gate.prepare (sr);
    denoiser.prepare (sr, (int) numCh, samplesPerBlock);
    comp.prepare (sr);
    deEss.prepare (spec);

    dryDelay.prepare (spec);
    dryDelay.reset();
    dryBuffer.setSize ((int) numCh, samplesPerBlock);

    inGain.reset  (sr, 0.02);
    outGain.reset (sr, 0.02);
    wetMix.reset  (sr, 0.02);

    // The only latency in the chain comes from the STFT denoiser.
    latencySamples = denoiser.getLatencySamples();
    dryDelay.setDelay ((float) latencySamples);
    setLatencySamples (latencySamples);
}

void VoxAIAudioProcessor::updateParameters()
{
    using namespace vox::params;
    auto get = [this] (const char* id) { return apvts.getRawParameterValue (id)->load(); };

    inGain.setTargetValue  (juce::Decibels::decibelsToGain (get (id::inputGain)));
    outGain.setTargetValue (juce::Decibels::decibelsToGain (get (id::outputGain)));
    wetMix.setTargetValue  (get (id::mix) * 0.01f);

    eq.setHpfFreq    (get (id::hpfFreq));
    eq.setWarmth     (get (id::warmth));
    eq.setBody       (get (id::body));
    eq.setPresence   (get (id::presence));
    eq.setBrightness (get (id::brightness));

    gate.setThresholdDb (get (id::gateThresh));
    gate.setDepth       (get (id::gateAmount) * 0.01f);

    denoiser.setAmount    (get (id::nrAmount) * 0.01f);
    denoiser.setUseNeural (get (id::nrUseNeural) > 0.5f);

    comp.setAmount  (get (id::compAmount) * 0.01f);
    deEss.setAmount (get (id::deEssAmount) * 0.01f);
}

void VoxAIAudioProcessor::processBlock (juce::AudioBuffer<float>& buffer, juce::MidiBuffer&)
{
    juce::ScopedNoDenormals noDenormals;
    const int numCh = buffer.getNumChannels();
    const int n = buffer.getNumSamples();

    if (apvts.getRawParameterValue (params::id::bypass)->load() > 0.5f)
        return;

    updateParameters();

    // Input gain + input meter.
    for (int i = 0; i < n; ++i)
    {
        const float g = inGain.getNextValue();
        float peak = 0.f;
        for (int ch = 0; ch < numCh; ++ch)
        {
            auto* d = buffer.getWritePointer (ch);
            d[i] *= g;
            peak = juce::jmax (peak, std::abs (d[i]));
        }
        const float cur = inputPeak.load (std::memory_order_relaxed);
        if (peak > cur) inputPeak.store (peak, std::memory_order_relaxed);
    }

    // Build a latency-aligned copy of the (post-input-gain) dry signal so the
    // dry/wet blend does not comb-filter against the STFT denoiser's latency.
    for (int ch = 0; ch < numCh; ++ch)
    {
        auto* dry = dryBuffer.getWritePointer (ch);
        for (int i = 0; i < n; ++i)
        {
            dryDelay.pushSample (ch, buffer.getSample (ch, i));
            dry[i] = dryDelay.popSample (ch, (float) latencySamples);
        }
    }

    // === WET CHAIN =========================================================
    juce::dsp::AudioBlock<float> block (buffer);

    gate.process (buffer);
    denoiser.process (buffer);
    eq.process (block);
    comp.process (buffer);
    deEss.process (buffer);

    // Output gain, dry/wet blend, output meter, and spectrum tap.
    for (int i = 0; i < n; ++i)
    {
        const float og  = outGain.getNextValue();
        const float wet = wetMix.getNextValue();
        float peak = 0.f, mono = 0.f;

        for (int ch = 0; ch < numCh; ++ch)
        {
            auto* d = buffer.getWritePointer (ch);
            const float dry = dryBuffer.getSample (ch, i);
            float s = (d[i] * wet + dry * (1.0f - wet)) * og;
            d[i] = s;
            peak = juce::jmax (peak, std::abs (s));
            mono += s;
        }

        const float cur = outputPeak.load (std::memory_order_relaxed);
        if (peak > cur) outputPeak.store (peak, std::memory_order_relaxed);

        // Feed the spectrum FIFO.
        scopeBuf[(size_t) scopeWrite.load (std::memory_order_relaxed)] = mono / juce::jmax (1, numCh);
        scopeWrite.store ((scopeWrite.load (std::memory_order_relaxed) + 1) % scopeSize,
                          std::memory_order_release);
    }
}

int VoxAIAudioProcessor::readScope (float* dst, int maxN) noexcept
{
    const int w = scopeWrite.load (std::memory_order_acquire);
    int avail = w - scopeRead;
    if (avail < 0) avail += scopeSize;
    const int count = juce::jmin (avail, maxN);
    for (int i = 0; i < count; ++i)
    {
        dst[i] = scopeBuf[(size_t) scopeRead];
        scopeRead = (scopeRead + 1) % scopeSize;
    }
    return count;
}

// --- A/B ------------------------------------------------------------------
void VoxAIAudioProcessor::storeToSlot (bool slotBflag)
{
    (slotBflag ? slotB : slotA) = apvts.copyState();
    currentSlotB = slotBflag;
}

void VoxAIAudioProcessor::recallSlot (bool slotBflag)
{
    auto& tree = slotBflag ? slotB : slotA;
    if (tree.isValid())
        apvts.replaceState (tree);
    currentSlotB = slotBflag;
}

// --- State ----------------------------------------------------------------
void VoxAIAudioProcessor::getStateInformation (juce::MemoryBlock& dest)
{
    if (auto xml = apvts.copyState().createXml())
        copyXmlToBinary (*xml, dest);
}

void VoxAIAudioProcessor::setStateInformation (const void* data, int size)
{
    if (auto xml = getXmlFromBinary (data, size))
        apvts.replaceState (juce::ValueTree::fromXml (*xml));
}

juce::AudioProcessorEditor* VoxAIAudioProcessor::createEditor()
{
    return new VoxAIAudioProcessorEditor (*this);
}

// This creates new instances of the plugin.
juce::AudioProcessor* JUCE_CALLTYPE createPluginFilter()
{
    return new VoxAIAudioProcessor();
}
