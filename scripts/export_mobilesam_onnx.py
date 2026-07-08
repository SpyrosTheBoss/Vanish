"""
Export MobileSAM to two ONNX files for on-device tap-to-segment:
  sam_encoder.onnx : image [1,3,1024,1024] -> image_embeddings [1,256,64,64]
  sam_decoder.onnx : (embeddings + a tap point) -> a binary mask at original size

Runs the encoder once per photo, the decoder per tap. Verifies end-to-end that
a single foreground click segments a plausible region.
"""
import os
import sys
import urllib.request
import numpy as np
import torch

from mobile_sam import sam_model_registry
from mobile_sam.utils.onnx import SamOnnxModel
from mobile_sam.utils.transforms import ResizeLongestSide

OUT_ASSETS = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets"))
ENC = os.path.join(OUT_ASSETS, "sam_encoder.onnx")
DEC = os.path.join(OUT_ASSETS, "sam_decoder.onnx")
CKPT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "models_export", "mobile_sam.pt"))
WEIGHTS_URL = "https://github.com/ChaoningZhang/MobileSAM/raw/master/weights/mobile_sam.pt"


def ensure_weights():
    os.makedirs(os.path.dirname(CKPT), exist_ok=True)
    if not os.path.exists(CKPT):
        print("Downloading mobile_sam.pt (~40MB)...")
        urllib.request.urlretrieve(WEIGHTS_URL, CKPT)
    print(f"weights: {CKPT} ({os.path.getsize(CKPT)/1e6:.0f} MB)")


def main():
    ensure_weights()
    os.makedirs(OUT_ASSETS, exist_ok=True)
    sam = sam_model_registry["vit_t"](checkpoint=CKPT).eval()

    # ---- encoder ----
    print("Exporting encoder...")
    dummy = torch.randn(1, 3, 1024, 1024)
    torch.onnx.export(
        sam.image_encoder, dummy, ENC,
        input_names=["image"], output_names=["embeddings"],
        opset_version=17, do_constant_folding=True,
    )
    print(f"  {ENC}  {os.path.getsize(ENC)/1e6:.1f} MB")

    # ---- decoder ----
    print("Exporting decoder...")
    onnx_model = SamOnnxModel(sam, return_single_mask=True)
    embed_dim = sam.prompt_encoder.embed_dim
    embed_size = sam.prompt_encoder.image_embedding_size
    mask_in = [4 * embed_size[0], 4 * embed_size[1]]
    dummy_inputs = {
        "image_embeddings": torch.randn(1, embed_dim, *embed_size),
        "point_coords": torch.randint(0, 1024, (1, 2, 2)).float(),
        "point_labels": torch.randint(0, 4, (1, 2)).float(),
        "mask_input": torch.randn(1, 1, *mask_in),
        "has_mask_input": torch.tensor([0], dtype=torch.float),
        "orig_im_size": torch.tensor([1024, 1024], dtype=torch.float),
    }
    torch.onnx.export(
        onnx_model, tuple(dummy_inputs.values()), DEC,
        input_names=list(dummy_inputs.keys()),
        output_names=["masks", "iou_predictions", "low_res_masks"],
        dynamic_axes={"point_coords": {1: "n"}, "point_labels": {1: "n"}},
        opset_version=17, do_constant_folding=True,
    )
    print(f"  {DEC}  {os.path.getsize(DEC)/1e6:.1f} MB")

    # ---- verify end-to-end ----
    print("Verifying encode->click->mask ...")
    import onnxruntime as ort
    enc = ort.InferenceSession(ENC, providers=["CPUExecutionProvider"])
    dec = ort.InferenceSession(DEC, providers=["CPUExecutionProvider"])

    H, W = 720, 1000
    img = np.zeros((H, W, 3), np.uint8)
    img[:] = (60, 90, 130)
    # a bright square "object" to click on
    img[250:470, 380:620] = (230, 200, 80)

    transform = ResizeLongestSide(1024)
    pixel_mean = np.array([123.675, 116.28, 103.53], np.float32)
    pixel_std = np.array([58.395, 57.12, 57.375], np.float32)

    resized = transform.apply_image(img)  # HxWx3 uint8, longest side 1024
    rh, rw = resized.shape[:2]
    x = (resized.astype(np.float32) - pixel_mean) / pixel_std
    x = x.transpose(2, 0, 1)[None]  # [1,3,rh,rw]
    padded = np.zeros((1, 3, 1024, 1024), np.float32)
    padded[:, :, :rh, :rw] = x

    embeddings = enc.run(["embeddings"], {"image": padded})[0]

    click = np.array([[500.0, 360.0]], np.float32)  # center of the square (orig coords)
    coords = transform.apply_coords(click[None], (H, W)).astype(np.float32)  # -> resized frame
    labels = np.array([[1.0]], np.float32)
    masks, iou, _ = dec.run(
        ["masks", "iou_predictions", "low_res_masks"],
        {
            "image_embeddings": embeddings,
            "point_coords": coords,
            "point_labels": labels,
            "mask_input": np.zeros((1, 1, mask_in[0], mask_in[1]), np.float32),
            "has_mask_input": np.zeros(1, np.float32),
            "orig_im_size": np.array([H, W], np.float32),
        },
    )
    mask = masks[0, 0] > 0.0
    # is the segmented region roughly the square?
    ys, xs = np.where(mask)
    if len(xs) == 0:
        print("[FAIL] empty mask"); sys.exit(1)
    cover = mask[250:470, 380:620].mean()
    outside = mask.mean() - (mask[250:470, 380:620].sum() / mask.size)
    print(f"  iou={float(iou.max()):.3f}  square coverage={cover:.2f}  mask bbox=x[{xs.min()},{xs.max()}] y[{ys.min()},{ys.max()}]")
    if cover > 0.8 and xs.min() > 300 and xs.max() < 700:
        print("[PASS] click segmented the object cleanly.")
    else:
        print("[WARN] segmentation off — inspect."); sys.exit(1)


if __name__ == "__main__":
    main()
