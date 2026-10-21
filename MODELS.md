# Models

Veil ships no model weights. They are downloaded on first use (or side-loaded), and every file
that Hugging Face publishes a hash for is checked against the SHA-256 below. The source of truth
in code is `models/src/main/java/com/owaisahmed/veil/models/ModelRegistry.kt`.

## OCR — PP-OCRv6 small (PaddlePaddle)

License: Apache-2.0

| File | Source | Size | SHA-256 |
|---|---|---|---|
| `det.onnx` | [PaddlePaddle/PP-OCRv6_small_det_onnx](https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx) `inference.onnx` | 9,880,512 | `d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e` |
| `rec.onnx` | [PaddlePaddle/PP-OCRv6_small_rec_onnx](https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx) `inference.onnx` | 21,159,378 | `5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634` |
| `rec.yml` | same repo at revision `b8f84f0`, `inference.yml` (character dictionary) | 150,579 | `ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1` (ours; not an LFS file) |

The recognizer dictionary has 18,708 entries and includes ä ö ü Ä Ö Ü ß. It does not include
`„` (German low quotation mark) or `ẞ` (capital sharp s).

## Translation — Hy-MT2 1.8B (Tencent)

License: Apache-2.0, not gated.

| File | Source | Size | SHA-256 |
|---|---|---|---|
| `model.gguf` (default) | [tencent/Hy-MT2-1.8B-GGUF](https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF) `Hy-MT2-1.8B-Q4_K_M.gguf` | 1,133,080,448 | `dc5f44fcf1fa496ee7ad725982c0c8c553a4de00259b53af84c4b89fb0c06699` |

Other builds, not wired up yet:

| Build | Size | Note |
|---|---|---|
| `Hy-MT2-1.8B-Q6_K.gguf` | 1.47 GB | `d98fe604dec1f28f58f80d7d560f7177e584d3b8e5835862687660e5ff97cb40` |
| `Hy-MT2-1.8B-Q8_0.gguf` | 1.91 GB | `5c3fe0b1408a5ceb0143184ef247b11b579c525f4b02b060e6c851bb76fef1a4` |
| [Hy-MT2-1.8B-1.25Bit-GGUF](https://huggingface.co/tencent/Hy-MT2-1.8B-1.25Bit-GGUF) | 462 MB | Needs the STQ1_0 kernel from llama.cpp PR #22836, which is not merged yet. |

## Translation — Firefox Translations German→English (Mozilla, Bergamot), default

License: MPL-2.0 (models and the bergamot-translator engine, built from the submodule
`third_party/bergamot-translator`). The "base-memory" model v2.0 that Firefox itself ships, from
Mozilla's Remote Settings CDN (`https://firefox-settings-attachments.cdn.mozilla.net/main-workspace/translations-models/`),
with the hashes Remote Settings publishes. Flores-200: BLEU 41.4, COMET 0.8829. Stored as
`bergamot-de-en-base-memory/`.

| File | Source | Size | SHA-256 |
|---|---|---|---|
| `model.bin` | `f44b1b1b-9df6-4ece-971e-0e5ce96fae54.bin` (`model.deen.intgemm.alphas.bin`) | 31,561,787 | `3e6f7c2c2425d10824797270b382bee718ff34af2cab9308841c82ca46dc6f20` |
| `vocab.spm` | `8ad4d93e-21e6-4862-81d5-c1c3a7d0767b.spm` (`vocab.deen.spm`) | 810,073 | `69f730becafa48e3bb2c244eab66456877c08959a02f2bd5519b5a3088b62f9c` |
| `lex.bin` | `d0e4efcb-6145-43db-a69e-568904cc2925.bin` (`lex.50.50.deen.s2t.bin`) | 4,945,796 | `113b98460468360cca68c042e1cddf49c4e1931cbb975ed04349c9a3bd607010` |

## Translation — OPUS-MT German→English (Helsinki-NLP)

License: Apache-2.0. Int8 ONNX export from [Xenova/opus-mt-de-en](https://huggingface.co/Xenova/opus-mt-de-en),
pinned to revision `399dfd68706739fffd503f876093e455ae268a06`. Stored as `opus-mt-de-en-int8/`.

| File | Source | Size | SHA-256 |
|---|---|---|---|
| `encoder.onnx` | `onnx/encoder_model_quantized.onnx` | 49,366,942 | `4cedda8f8c89b72a42b3c6cd1e7a27f2de24457093e3bf80cb3e46829641fcd8` |
| `decoder.onnx` | `onnx/decoder_model_merged_quantized.onnx` | 56,652,404 | `4712800584b2d77c2f9e620bf29523e2b20b62f65ade5011ee01780e2b0ea5d3` |
| `source.spm` | `source.spm` (not LFS; hash is ours) | 796,845 | `bbd1f495eea99c8e21ae086d9146e0fa7b096c3dfdd9ba07ab8b631889df5c9b` |
| `vocab.json` | `vocab.json` (not LFS; hash is ours) | 1,389,436 | `d5acea957b265a78554999144459c5e391e0df525864edc8287bc090290baa44` |

## Side-loading

```bash
adb push det.onnx rec.onnx rec.yml /sdcard/Android/data/com.owaisahmed.veil/files/models/pp-ocrv6-small/
adb push Hy-MT2-1.8B-Q4_K_M.gguf /sdcard/Android/data/com.owaisahmed.veil/files/models/hy-mt2-1.8b-q4km/model.gguf
```
