#include "NeuralDenoiser.h"

// The NeuralDenoiser is currently header-only (see NeuralDenoiser.h). This
// translation unit exists so the integration seam has a home for the real
// RNNoise / ONNX Runtime implementation without touching the build again.
//
// When you add a model:
//   * put the inference state and helpers here,
//   * keep the public interface in the header unchanged,
//   * flip modelLoaded = true once the session/context is ready.

namespace vox::ai
{
    // (intentionally empty for now)
}
