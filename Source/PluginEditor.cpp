#include "PluginEditor.h"
#include "Parameters.h"

using namespace vox;
using LnF = vox::gui::VoxLookAndFeel;

namespace
{
    // A knob creation table: {paramID, display name}. Order drives layout.
    struct KnobDef { const char* id; const char* name; };
    const std::array<KnobDef, 13> kKnobDefs {{
        { params::id::inputGain,  "INPUT" },
        { params::id::outputGain, "OUTPUT" },
        { params::id::mix,        "MIX" },
        { params::id::hpfFreq,    "HPF" },
        { params::id::gateThresh, "GATE" },
        { params::id::gateAmount, "GATE DEPTH" },
        { params::id::nrAmount,   "DENOISE" },
        { params::id::warmth,     "WARMTH" },
        { params::id::body,       "BODY" },
        { params::id::presence,   "PRESENCE" },
        { params::id::brightness, "BRIGHT" },
        { params::id::compAmount, "COMP" },
        { params::id::deEssAmount,"DE-ESS" },
    }};
}

VoxAIAudioProcessorEditor::VoxAIAudioProcessorEditor (VoxAIAudioProcessor& p)
    : AudioProcessorEditor (&p), proc (p)
{
    setLookAndFeel (&lnf);

    addAndMakeVisible (spectrum);
    addAndMakeVisible (inMeter);
    addAndMakeVisible (outMeter);

    for (auto& def : kKnobDefs)
        addKnob (def.id, def.name);

    addAndMakeVisible (bypassButton);
    addAndMakeVisible (neuralButton);
    bypassAttach = std::make_unique<ButtonAttach> (proc.apvts, params::id::bypass, bypassButton);
    neuralAttach = std::make_unique<ButtonAttach> (proc.apvts, params::id::nrUseNeural, neuralButton);

    for (auto* b : { &aButton, &bButton, &copyButton })
        addAndMakeVisible (b);
    aButton.setClickingTogglesState (false);
    bButton.setClickingTogglesState (false);
    aButton.onClick    = [this] { proc.storeToSlot (false); proc.recallSlot (false);
                                   aButton.setToggleState (true, juce::dontSendNotification); };
    bButton.onClick    = [this] { proc.recallSlot (true); };
    copyButton.onClick = [this] { proc.storeToSlot (proc.isSlotB()); };

    addAndMakeVisible (presetBox);
    presetBox.addItemList ({ "Init", "Podcast", "Broadcast", "Streamer", "Singer" }, 1);
    presetBox.setSelectedId (1, juce::dontSendNotification);
    presetBox.onChange = [this] { applyPreset (presetBox.getSelectedId() - 1); };

    // A slot starts as the current state.
    proc.storeToSlot (false);

    setSize (720, 480);
    setResizable (true, true);
    setResizeLimits (640, 420, 1100, 720);
    startTimerHz (30);
}

VoxAIAudioProcessorEditor::~VoxAIAudioProcessorEditor()
{
    stopTimer();
    setLookAndFeel (nullptr);
}

VoxAIAudioProcessorEditor::Knob& VoxAIAudioProcessorEditor::addKnob (const juce::String& paramID,
                                                                    const juce::String& name)
{
    auto* k = new Knob();
    k->slider.setSliderStyle (juce::Slider::RotaryHorizontalVerticalDrag);
    k->slider.setTextBoxStyle (juce::Slider::TextBoxBelow, false, 60, 16);
    addAndMakeVisible (k->slider);

    k->label.setText (name, juce::dontSendNotification);
    k->label.setJustificationType (juce::Justification::centred);
    k->label.setColour (juce::Label::textColourId, LnF::textDim);
    k->label.setFont (juce::Font (juce::FontOptions (11.f).withStyle ("Bold")));
    addAndMakeVisible (k->label);

    k->attach = std::make_unique<SliderAttach> (proc.apvts, paramID, k->slider);
    knobs.add (k);
    return *k;
}

void VoxAIAudioProcessorEditor::applyPreset (int index)
{
    // index 0 = Init leaves user values; presets set voice-tuned defaults.
    auto set = [this] (const char* id, float v)
    {
        if (auto* p = proc.apvts.getParameter (id))
            p->setValueNotifyingHost (p->convertTo0to1 (v));
    };

    using namespace vox::params;
    switch (index)
    {
        case 1: // Podcast — warm, controlled, clean
            set (id::hpfFreq, 85.f);  set (id::gateAmount, 40.f); set (id::nrAmount, 45.f);
            set (id::warmth, 3.f);    set (id::body, 1.5f);       set (id::presence, 3.f);
            set (id::brightness, 1.f);set (id::compAmount, 55.f); set (id::deEssAmount, 40.f);
            break;
        case 2: // Broadcast — forward, tight, bright
            set (id::hpfFreq, 100.f); set (id::gateAmount, 55.f); set (id::nrAmount, 55.f);
            set (id::warmth, 1.f);    set (id::body, -1.f);       set (id::presence, 5.f);
            set (id::brightness, 3.f);set (id::compAmount, 70.f); set (id::deEssAmount, 55.f);
            break;
        case 3: // Streamer — heavy noise reduction, punchy
            set (id::hpfFreq, 110.f); set (id::gateAmount, 65.f); set (id::nrAmount, 70.f);
            set (id::warmth, 2.f);    set (id::body, 0.f);        set (id::presence, 4.f);
            set (id::brightness, 2.f);set (id::compAmount, 60.f); set (id::deEssAmount, 45.f);
            break;
        case 4: // Singer — gentle, open, musical
            set (id::hpfFreq, 70.f);  set (id::gateAmount, 15.f); set (id::nrAmount, 25.f);
            set (id::warmth, 2.f);    set (id::body, 1.f);        set (id::presence, 2.5f);
            set (id::brightness, 2.f);set (id::compAmount, 35.f); set (id::deEssAmount, 35.f);
            break;
        default: break;
    }
}

void VoxAIAudioProcessorEditor::timerCallback()
{
    inMeter.pushPeak  (proc.getInputPeak());
    outMeter.pushPeak (proc.getOutputPeak());

    int got = proc.readScope (scopeTransfer.data(), (int) scopeTransfer.size());
    for (int i = 0; i < got; ++i)
        spectrum.pushSample (scopeTransfer[(size_t) i]);
}

void VoxAIAudioProcessorEditor::paint (juce::Graphics& g)
{
    g.fillAll (LnF::bg);

    auto header = getLocalBounds().removeFromTop (40).toFloat();
    g.setColour (LnF::panel);
    g.fillRect (header);
    g.setColour (LnF::accent);
    g.setFont (juce::Font (juce::FontOptions (20.f).withStyle ("Bold")));
    g.drawText ("VoxAI", header.reduced (14, 0), juce::Justification::centredLeft);
    g.setColour (LnF::textDim);
    g.setFont (juce::Font (juce::FontOptions (11.f)));
    g.drawText ("AI Voice Channel Strip", header.reduced (90, 0), juce::Justification::centredLeft);
}

void VoxAIAudioProcessorEditor::resized()
{
    auto r = getLocalBounds();
    auto header = r.removeFromTop (40);

    // Header-right controls: preset + A/B.
    auto hr = header.reduced (8, 6);
    presetBox.setBounds (hr.removeFromRight (140));
    hr.removeFromRight (8);
    copyButton.setBounds (hr.removeFromRight (60));
    bButton.setBounds (hr.removeFromRight (34));
    aButton.setBounds (hr.removeFromRight (34));
    bypassButton.setBounds (hr.removeFromRight (80));
    neuralButton.setBounds (hr.removeFromRight (90));

    // Meters on the sides.
    inMeter.setBounds  (r.removeFromLeft (18).reduced (3, 8));
    outMeter.setBounds (r.removeFromRight (18).reduced (3, 8));

    // Spectrum across the top of the body.
    spectrum.setBounds (r.removeFromTop (140).reduced (8));

    // Knob grid: 5 columns.
    auto grid = r.reduced (8, 4);
    const int cols = 5;
    const int rows = (knobs.size() + cols - 1) / cols;
    const int cw = grid.getWidth() / cols;
    const int ch = grid.getHeight() / juce::jmax (1, rows);

    for (int i = 0; i < knobs.size(); ++i)
    {
        const int col = i % cols;
        const int row = i / cols;
        auto cell = juce::Rectangle<int> (grid.getX() + col * cw, grid.getY() + row * ch, cw, ch)
                        .reduced (6);
        knobs[i]->label.setBounds (cell.removeFromTop (16));
        knobs[i]->slider.setBounds (cell);
    }
}
