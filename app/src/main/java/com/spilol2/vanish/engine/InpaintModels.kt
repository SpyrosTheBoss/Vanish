package com.spilol2.vanish.engine

/**
 * The inpainting models the app can use. MI-GAN ships in the APK; heavier
 * models are downloaded on demand the first time they're selected.
 */
enum class InpaintModelId(
    val display: String,
    val tagline: String,
    val sizeLabel: String,
    /** Asset filename if bundled in the APK, else null. */
    val bundledAsset: String?,
    /** Download URL + local filename for on-demand models. */
    val downloadUrl: String?,
    val downloadFile: String?,
) {
    MIGAN(
        display = "MI-GAN",
        tagline = "Fast, lightweight",
        sizeLabel = "27 MB · bundled",
        bundledAsset = "migan_512.onnx",
        downloadUrl = null,
        downloadFile = null,
    ),
    LAMA(
        display = "LaMa",
        tagline = "Best quality",
        sizeLabel = "208 MB · download",
        bundledAsset = null,
        downloadUrl = "https://huggingface.co/Carve/LaMa-ONNX/resolve/main/lama_fp32.onnx",
        downloadFile = "lama_fp32.onnx",
    );

    val bundled: Boolean get() = bundledAsset != null
}
