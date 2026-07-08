<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="branding/vanish_logo_dark.png">
    <img src="branding/vanish_logo.png" alt="Vanish" width="420">
  </picture>
</p>

<p align="center"><b>On-device object &amp; watermark eraser for Android — tap it, and it's gone.</b></p>

<p align="center">
  <a href="https://github.com/SpyrosTheBoss/Vanish/releases/latest"><img src="https://img.shields.io/badge/Download-APK-0F766E?style=for-the-badge&logo=android&logoColor=white" alt="Download APK"></a>
</p>

---

Vanish grew out of [WatermarkRemover-AI-Revamped](https://github.com/SpyrosTheBoss/WatermarkRemover-AI-Revamped) and is now its own app. Point it at any photo, tap the thing you want gone, and an AI model rebuilds the background behind it — all on the phone, no upload.

## Features

- **Tap-to-select** — one tap and MobileSAM outlines the exact object. Lasso and brush are there too when you want manual control.
- **On-device inpainting** — MI-GAN (bundled, ~2s) rebuilds the background. No photo ever leaves the device.
- **Choose your model** — switch to LaMa in Settings for higher quality; it downloads on demand (208 MB).
- **Before / after** — drag to compare, then save to your gallery or share.
- **Material 3** — dynamic color (Material You), light/dark, themed icon.

## How it works

| Stage | Model | Runs |
|-------|-------|------|
| Select (tap) | MobileSAM (TinyViT encoder + decoder, ONNX) | once per photo + per tap |
| Erase | MI-GAN or LaMa (ONNX Runtime Mobile) | per removal |

Both stages run locally through ONNX Runtime. The `scripts/` folder documents how each model was exported and verified.

## Build

Open the `Vanish/` folder in Android Studio (JDK 17+, minSdk 29) and run. The MI-GAN and MobileSAM models are bundled in `app/src/main/assets/`; LaMa is fetched at runtime the first time you select it.

## Credits

Inpainting models via [IOPaint](https://github.com/Sanster/IOPaint) (MI-GAN, LaMa). Segmentation via [MobileSAM](https://github.com/ChaoningZhang/MobileSAM). LaMa ONNX from [Carve/LaMa-ONNX](https://huggingface.co/Carve/LaMa-ONNX).
