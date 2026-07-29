# ---------------------------------------------------------------------------
# RNNoise integration (optional)
#
# Enabled with -DVOX_ENABLE_RNNOISE=ON. RNNoise (xiph/rnnoise, BSD) ships no
# CMake build and keeps its trained model weights *outside* git (too large),
# so we:
#   1) fetch the sources,
#   2) run its download_model.sh to fetch the matching rnnoise_data.{c,h},
#   3) compile the C sources into a small static library `rnnoise`.
#
# This requires network access and a POSIX shell at configure time. The default
# plugin build does NOT enable this, so it stays dependency-free.
# ---------------------------------------------------------------------------
include(FetchContent)

FetchContent_Declare(
    rnnoise
    GIT_REPOSITORY https://github.com/xiph/rnnoise.git
    # Pin to a specific commit/tag for reproducible commercial builds.
    GIT_TAG        master
    GIT_SHALLOW    TRUE
)

# RNNoise has no CMakeLists, so populate without add_subdirectory.
FetchContent_GetProperties(rnnoise)
if(NOT rnnoise_POPULATED)
    FetchContent_Populate(rnnoise)
endif()

# Ensure the trained model weights are present. Some RNNoise versions bundle
# them in git (src/rnn_data.c); newer ones keep them out and provide a
# download_model.sh. Handle both.
if(EXISTS "${rnnoise_SOURCE_DIR}/src/rnn_data.c"
   OR EXISTS "${rnnoise_SOURCE_DIR}/src/rnnoise_data.c")
    message(STATUS "RNNoise: model weights bundled with sources")
elseif(EXISTS "${rnnoise_SOURCE_DIR}/download_model.sh")
    message(STATUS "RNNoise: downloading model weights via download_model.sh")
    execute_process(
        COMMAND bash download_model.sh
        WORKING_DIRECTORY "${rnnoise_SOURCE_DIR}"
        RESULT_VARIABLE rnnoise_model_result
        OUTPUT_VARIABLE rnnoise_model_out
        ERROR_VARIABLE  rnnoise_model_err)
    if(NOT rnnoise_model_result EQUAL 0)
        message(FATAL_ERROR
            "RNNoise model download failed (${rnnoise_model_result}).\n"
            "stdout: ${rnnoise_model_out}\nstderr: ${rnnoise_model_err}\n"
            "Fetch it manually: run ./download_model.sh in ${rnnoise_SOURCE_DIR}")
    endif()
else()
    message(FATAL_ERROR "RNNoise: no bundled model and no download_model.sh found")
endif()

# Scalar build: the top-level C sources compile without autotools' config.h
# (which is guarded by HAVE_CONFIG_H — we deliberately leave it undefined).
file(GLOB RNNOISE_SOURCES "${rnnoise_SOURCE_DIR}/src/*.c")

add_library(rnnoise STATIC ${RNNOISE_SOURCES})
target_include_directories(rnnoise PUBLIC
    "${rnnoise_SOURCE_DIR}/include"
    "${rnnoise_SOURCE_DIR}/src")
target_compile_definitions(rnnoise PRIVATE RNNOISE_BUILD)
set_target_properties(rnnoise PROPERTIES POSITION_INDEPENDENT_CODE ON)

if(NOT MSVC)
    target_compile_options(rnnoise PRIVATE -w)   # silence third-party warnings
endif()
