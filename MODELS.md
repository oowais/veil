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
| `rec.yml` | same repo, `inference.yml` (character dictionary) | 150,579 | not an LFS file, no published hash |

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

## Side-loading

```bash
adb push det.onnx rec.onnx rec.yml /sdcard/Android/data/com.owaisahmed.veil/files/models/pp-ocrv6-small/
adb push Hy-MT2-1.8B-Q4_K_M.gguf /sdcard/Android/data/com.owaisahmed.veil/files/models/hy-mt2-1.8b-q4km/model.gguf
```
