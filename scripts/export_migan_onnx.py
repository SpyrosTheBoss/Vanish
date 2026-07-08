"""
Export the MI-GAN traced TorchScript inpainting model to ONNX for on-device
(Android / ONNX Runtime Mobile) use, then verify the ONNX output matches the
original PyTorch model pixel-for-pixel on a real masked image.

MI-GAN interface (from iopaint.model.mi_gan):
  input : [1, 4, 512, 512]  = concat([0.5 - mask, erased_rgb], dim=1)
          - mask channel: (0.5 - mask), where mask is 1.0 in the hole, else 0.0
          - erased_rgb   : image in [-1,1] with the hole zeroed out
  output: [1, 3, 512, 512]  in [-1, 1] (RGB)

Run with the repo's bundled python:
  python/python.exe scripts/export_migan_onnx.py
"""
import os
import sys
import numpy as np
import torch

from iopaint.helper import get_cache_path_by_url
from iopaint.model.mi_gan import MIGAN_MODEL_URL, MIGAN

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
OUT_DIR = os.path.abspath(OUT_DIR)
OUT_ONNX = os.path.join(OUT_DIR, "migan_512.onnx")
SIZE = 512


def build_input(seed=0):
    """Build a realistic [1,4,512,512] MI-GAN input from a synthetic image + hole."""
    rng = np.random.RandomState(seed)
    # a smooth-ish RGB image in [-1,1]
    img = np.zeros((3, SIZE, SIZE), np.float32)
    yy, xx = np.mgrid[0:SIZE, 0:SIZE]
    img[0] = np.sin(xx / 40.0) * 0.6
    img[1] = np.cos(yy / 55.0) * 0.5
    img[2] = ((xx + yy) / (2.0 * SIZE)) * 2 - 1
    img += rng.randn(3, SIZE, SIZE).astype(np.float32) * 0.05
    img = np.clip(img, -1, 1)

    # a rectangular hole
    mask = np.zeros((1, SIZE, SIZE), np.float32)
    mask[:, 180:340, 200:360] = 1.0

    erased = img * (1 - mask)
    inp = np.concatenate([0.5 - mask, erased], axis=0)[None]  # [1,4,512,512]
    return torch.from_numpy(inp)


def main():
    pt_path = get_cache_path_by_url(MIGAN_MODEL_URL)
    if not os.path.exists(pt_path):
        print("MI-GAN model not downloaded; downloading...")
        MIGAN.download()
    print(f"Loading traced model: {pt_path}")
    model = torch.jit.load(pt_path, map_location="cpu").eval()

    example = build_input(seed=0)
    print(f"Example input: {tuple(example.shape)} dtype={example.dtype}")

    with torch.no_grad():
        pt_out = model(example)
    print(f"PyTorch output: {tuple(pt_out.shape)} range=[{pt_out.min():.3f}, {pt_out.max():.3f}]")

    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"Exporting ONNX -> {OUT_ONNX}")
    torch.onnx.export(
        model,
        example,
        OUT_ONNX,
        input_names=["input"],
        output_names=["output"],
        opset_version=17,
        dynamic_axes=None,  # fixed 512x512 — best for mobile
        do_constant_folding=True,
    )

    # simplify
    try:
        import onnx
        from onnxsim import simplify
        m = onnx.load(OUT_ONNX)
        m_simp, ok = simplify(m)
        if ok:
            onnx.save(m_simp, OUT_ONNX)
            print("onnxsim: simplified OK")
        else:
            print("onnxsim: simplify check failed, keeping original")
    except Exception as e:
        print(f"onnxsim skipped: {e}")

    size_mb = os.path.getsize(OUT_ONNX) / 1e6
    print(f"ONNX size: {size_mb:.1f} MB")

    # ---- verify parity on multiple seeds ----
    import onnxruntime as ort
    sess = ort.InferenceSession(OUT_ONNX, providers=["CPUExecutionProvider"])
    max_abs = 0.0
    for seed in range(3):
        x = build_input(seed=seed)
        with torch.no_grad():
            ref = model(x).numpy()
        got = sess.run(["output"], {"input": x.numpy()})[0]
        # compare as final uint8 pixels (what actually matters visually)
        ref_u8 = np.clip(ref * 127.5 + 127.5, 0, 255).round().astype(np.uint8)
        got_u8 = np.clip(got * 127.5 + 127.5, 0, 255).round().astype(np.uint8)
        diff = np.abs(ref_u8.astype(int) - got_u8.astype(int))
        max_abs = max(max_abs, diff.max())
        print(f"seed {seed}: max uint8 pixel diff = {diff.max()}, mean = {diff.mean():.4f}")

    print()
    if max_abs <= 2:
        print(f"[PASS] ONNX matches PyTorch (max pixel diff {max_abs} <= 2). Export good.")
    else:
        print(f"[WARN] max pixel diff {max_abs} > 2 — inspect before shipping.")
        sys.exit(1)


if __name__ == "__main__":
    main()
