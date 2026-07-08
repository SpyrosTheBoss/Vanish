"""
NOTE: This direct export does NOT work. big-LaMa uses Fourier convolutions
(aten::fft_rfftn), which PyTorch's ONNX exporter cannot emit at any opset. This
script is kept to document that finding. The Vanish app instead downloads a
known-good pre-exported LaMa ONNX (Carve/LaMa-ONNX, verified to match this
reference model to within 1/255 per pixel) on demand — see engine/ModelStore.kt.

Export the big-LaMa traced TorchScript inpainting model to ONNX for on-device
use, then verify parity against the original on a masked image.

LaMa interface (from iopaint.model.lama):
  inputs : image [1,3,H,W] RGB in [0,1], mask [1,1,H,W] binary (1 in the hole)
  output : [1,3,H,W] RGB in [0,1]
  pad_mod: 8

We export at a fixed 512x512 (matches the app's crop-around-mask strategy).
LaMa uses Fourier convolutions (rfft/irfft), which require ONNX opset >= 17.
"""
import os
import sys
import numpy as np
import torch

from iopaint.helper import get_cache_path_by_url
from iopaint.model.lama import LAMA_MODEL_URL, LaMa

OUT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "models_export"))
OUT_ONNX = os.path.join(OUT_DIR, "lama_512.onnx")
SIZE = 512


def build_inputs(seed=0):
    rng = np.random.RandomState(seed)
    yy, xx = np.mgrid[0:SIZE, 0:SIZE]
    img = np.stack([
        (np.sin(xx / 37.0) * 0.5 + 0.5),
        (np.cos(yy / 51.0) * 0.5 + 0.5),
        ((xx + yy) / (2.0 * SIZE)),
    ], 0).astype(np.float32)
    img = np.clip(img + rng.randn(3, SIZE, SIZE).astype(np.float32) * 0.03, 0, 1)
    mask = np.zeros((1, SIZE, SIZE), np.float32)
    mask[:, 190:330, 210:350] = 1.0
    return torch.from_numpy(img[None]), torch.from_numpy(mask[None])


def main():
    pt = get_cache_path_by_url(LAMA_MODEL_URL)
    if not os.path.exists(pt):
        print("Downloading big-lama (~200MB)...")
        LaMa.download()
    print(f"Loading traced model: {pt}  ({os.path.getsize(pt)/1e6:.0f} MB)")
    model = torch.jit.load(pt, map_location="cpu").eval()

    img, mask = build_inputs(0)
    with torch.no_grad():
        ref = model(img, mask)
    print(f"PyTorch out: {tuple(ref.shape)} range=[{ref.min():.3f},{ref.max():.3f}]")

    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"Exporting ONNX (opset 17) -> {OUT_ONNX}")
    torch.onnx.export(
        model,
        (img, mask),
        OUT_ONNX,
        input_names=["image", "mask"],
        output_names=["output"],
        opset_version=17,
        do_constant_folding=True,
        dynamic_axes=None,
    )
    print(f"ONNX size: {os.path.getsize(OUT_ONNX)/1e6:.1f} MB")

    import onnxruntime as ort
    sess = ort.InferenceSession(OUT_ONNX, providers=["CPUExecutionProvider"])
    worst = 0
    for s in range(3):
        i, m = build_inputs(s)
        with torch.no_grad():
            r = model(i, m).numpy()
        g = sess.run(["output"], {"image": i.numpy(), "mask": m.numpy()})[0]
        ru = np.clip(r * 255, 0, 255).round().astype(np.uint8)
        gu = np.clip(g * 255, 0, 255).round().astype(np.uint8)
        d = np.abs(ru.astype(int) - gu.astype(int))
        worst = max(worst, d.max())
        print(f"seed {s}: max uint8 diff={d.max()} mean={d.mean():.4f}")

    if worst <= 3:
        print(f"[PASS] LaMa ONNX matches (max diff {worst}).")
    else:
        print(f"[WARN] max diff {worst} > 3 — inspect.")
        sys.exit(1)


if __name__ == "__main__":
    main()
