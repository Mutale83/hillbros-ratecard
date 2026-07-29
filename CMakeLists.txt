cmake_minimum_required(VERSION 3.22)

# ---------------------------------------------------------------------------
# VoxAI — AI-assisted real-time voice processing plugin (VST3/AU)
# ---------------------------------------------------------------------------
project(VoxAI VERSION 0.1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

# ---------------------------------------------------------------------------
# JUCE
#
# By default we fetch JUCE via CMake FetchContent so the project builds with a
# single `cmake` invocation and no manual SDK downloads (the VST3 & AU SDKs are
# bundled inside JUCE). If you already have JUCE checked out, point
# JUCE_SOURCE_DIR at it to skip the network fetch.
# ---------------------------------------------------------------------------
set(JUCE_SOURCE_DIR "" CACHE PATH "Path to a local JUCE checkout (optional)")

if(JUCE_SOURCE_DIR)
    add_subdirectory(${JUCE_SOURCE_DIR} juce-build)
else()
    include(FetchContent)
    FetchContent_Declare(
        JUCE
        GIT_REPOSITORY https://github.com/juce-framework/JUCE.git
        GIT_TAG        8.0.4
        GIT_SHALLOW    TRUE
    )
    FetchContent_MakeAvailable(JUCE)
endif()

# ---------------------------------------------------------------------------
# Plugin target
# ---------------------------------------------------------------------------
juce_add_plugin(VoxAI
    COMPANY_NAME              "HillBros Audio"
    BUNDLE_ID                 com.hillbros.voxai
    PLUGIN_MANUFACTURER_CODE  Hbrs
    PLUGIN_CODE               Vxai
    FORMATS                   VST3 AU Standalone
    PRODUCT_NAME              "VoxAI"
    IS_SYNTH                  FALSE
    NEEDS_MIDI_INPUT          FALSE
    NEEDS_MIDI_OUTPUT         FALSE
    IS_MIDI_EFFECT            FALSE
    COPY_PLUGIN_AFTER_BUILD   TRUE
    VST3_CATEGORIES           Fx Restoration
    AU_MAIN_TYPE              kAudioUnitType_Effect
)

juce_generate_juce_header(VoxAI)

target_sources(VoxAI PRIVATE
    Source/PluginProcessor.cpp
    Source/PluginEditor.cpp
    Source/dsp/NoiseGate.cpp
    Source/dsp/Compressor.cpp
    Source/dsp/ParametricEQ.cpp
    Source/dsp/DeEsser.cpp
    Source/dsp/SpectralDenoiser.cpp
    Source/ai/NeuralDenoiser.cpp
    Source/gui/LevelMeter.cpp
    Source/gui/SpectrumAnalyzer.cpp
    Source/gui/VoxLookAndFeel.cpp
)

target_include_directories(VoxAI PRIVATE Source)

target_compile_definitions(VoxAI PUBLIC
    JUCE_WEB_BROWSER=0
    JUCE_USE_CURL=0
    JUCE_VST3_CAN_REPLACE_VST2=0
    JUCE_DISPLAY_SPLASH_SCREEN=0
)

target_link_libraries(VoxAI PRIVATE
    juce::juce_audio_utils
    juce::juce_dsp
    PUBLIC
    juce::juce_recommended_config_flags
    juce::juce_recommended_lto_flags
    juce::juce_recommended_warning_flags
)
