package com.owaisahmed.veil.models

/** One downloadable file. [sha256] is checked after every download; mismatching files are deleted. */
data class ModelFile(
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String?,
)

data class ModelSpec(
    val id: String,
    val displayName: String,
    val license: String,
    val files: List<ModelFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

/** Keep in sync with MODELS.md. */
object ModelRegistry {

    private const val HF = "https://huggingface.co"

    val ocrSmall = ModelSpec(
        id = "pp-ocrv6-small",
        displayName = "PP-OCRv6 small (detector + recognizer)",
        license = "Apache-2.0",
        files = listOf(
            ModelFile(
                fileName = "det.onnx",
                url = "$HF/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/main/inference.onnx",
                sizeBytes = 9_880_512,
                sha256 = "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e",
            ),
            ModelFile(
                fileName = "rec.onnx",
                url = "$HF/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/main/inference.onnx",
                sizeBytes = 21_159_378,
                sha256 = "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634",
            ),
            // Holds the recognizer's character dictionary. Not an LFS file, so Hugging Face publishes no hash for it.
            ModelFile(
                fileName = "rec.yml",
                url = "$HF/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/main/inference.yml",
                sizeBytes = 150_579,
                sha256 = null,
            ),
        ),
    )

    val hyMt2Q4 = ModelSpec(
        id = "hy-mt2-1.8b-q4km",
        displayName = "Hy-MT2 1.8B Q4_K_M",
        license = "Apache-2.0",
        files = listOf(
            ModelFile(
                fileName = "model.gguf",
                url = "$HF/tencent/Hy-MT2-1.8B-GGUF/resolve/main/Hy-MT2-1.8B-Q4_K_M.gguf",
                sizeBytes = 1_133_080_448,
                sha256 = "dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699",
            ),
        ),
    )

    /** OPUS-MT de→en, int8 ONNX export by Xenova, pinned to one revision (D-036). */
    private const val OPUS = "$HF/Xenova/opus-mt-de-en/resolve/399dfd68706739fffd503f876093e455ae268a06"

    val opusMtDeEn = ModelSpec(
        id = "opus-mt-de-en-int8",
        displayName = "OPUS-MT German→English (int8)",
        license = "Apache-2.0",
        files = listOf(
            ModelFile(
                fileName = "encoder.onnx",
                url = "$OPUS/onnx/encoder_model_quantized.onnx",
                sizeBytes = 49_366_942,
                sha256 = "4cedda8f8c89b72a42b3c6cd1e7a27f2de24457093e3bf80cb3e46829641fcd8",
            ),
            ModelFile(
                fileName = "decoder.onnx",
                url = "$OPUS/onnx/decoder_model_merged_quantized.onnx",
                sizeBytes = 56_652_404,
                sha256 = "4712800584b2d77c2f9e620bf29523e2b20b62f65ade5011ee01780e2b0ea5d3",
            ),
            // Not LFS files, so Hugging Face publishes no hash; ours, from the pinned revision.
            ModelFile(
                fileName = "source.spm",
                url = "$OPUS/source.spm",
                sizeBytes = 796_845,
                sha256 = "bbd1f495eea99c8e21ae086d9146e0fa7b096c3dfdd9ba07ab8b631889df5c9b",
            ),
            ModelFile(
                fileName = "vocab.json",
                url = "$OPUS/vocab.json",
                sizeBytes = 1_389_436,
                sha256 = "d5acea957b265a78554999144459c5e391e0df525864edc8287bc090290baa44",
            ),
        ),
    )

    val all = listOf(ocrSmall, hyMt2Q4, opusMtDeEn)
}
