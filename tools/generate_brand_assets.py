#!/usr/bin/env python3
"""Generate ISAI launcher icons and in-app emblem from the approved Golden Tamil Harp emblem.

Source: assets/isai-emblem-source.png (the approved emblem, 1254x1254).
Outputs (all derived from the source, nothing redrawn):
  - native-android/app/src/main/res/mipmap-*/ic_launcher_foreground.png   adaptive foreground
  - native-android/app/src/main/res/mipmap-*/ic_launcher_monochrome.png   themed-icon silhouette
  - native-android/app/src/main/res/drawable-nodpi/isai_emblem.png        splash / About emblem
  - assets/isai-play-store-icon-512.png                                   store listing icon

Run from the repository root:  python3 tools/generate_brand_assets.py
Requires: Pillow, numpy.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "assets" / "isai-emblem-source.png"
RES = ROOT / "native-android" / "app" / "src" / "main" / "res"
BACKGROUND = (9, 9, 8)  # matches @color/isai_icon_background (#090908)

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def emblem_mask(rgb: np.ndarray) -> np.ndarray:
    """Gold pixels of the emblem (used for bounds and the monochrome layer)."""
    a = rgb.astype(int)
    return (a[:, :, 0] > 110) & (a[:, :, 0] - a[:, :, 2] > 40)


def feathered_disc(size: int, radius: float, feather: float) -> Image.Image:
    yy, xx = np.mgrid[0:size, 0:size]
    dist = np.hypot(xx - size / 2, yy - size / 2)
    alpha = np.clip((radius - dist) / feather, 0.0, 1.0)
    return Image.fromarray((alpha * 255).astype(np.uint8), "L")


def main() -> None:
    src = Image.open(SOURCE).convert("RGB")
    rgb = np.asarray(src)
    mask = emblem_mask(rgb)
    ys, xs = np.where(mask)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    height = y1 - y0 + 1
    print(f"emblem bounds x={x0}-{x1} y={y0}-{y1} centre=({cx},{cy}) height={height}")

    # Square crop around the emblem; the soft disc hides the blurred corners of the source art.
    half = int(height * 0.62)
    crop_box = (cx - half, cy - half, cx + half, cy + half)
    crop = src.crop(crop_box)
    crop_mask = Image.fromarray((mask[crop_box[1]:crop_box[3], crop_box[0]:crop_box[2]] * 255).astype(np.uint8), "L")
    side = crop.size[0]

    # Colour crop with feathered alpha (keeps the emblem's glow, fades into the solid background).
    disc = feathered_disc(side, radius=side * 0.5, feather=side * 0.18)
    emblem_rgba = crop.convert("RGBA")
    emblem_rgba.putalpha(disc)

    # Silhouette for themed (monochrome) icons.
    silhouette = Image.new("RGBA", crop.size, (255, 255, 255, 0))
    silhouette.putalpha(crop_mask.filter(ImageFilter.GaussianBlur(1.2)))

    # Adaptive icons: 108dp canvas, 66dp safe zone. Fit the emblem height inside ~58% of the canvas.
    for density, px in DENSITIES.items():
        out_dir = RES / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        scale = (px * 0.58) / height  # emblem height -> 58% of canvas
        layer_side = max(1, round(side * scale))
        for name, layer in (("ic_launcher_foreground.png", emblem_rgba), ("ic_launcher_monochrome.png", silhouette)):
            canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
            scaled = layer.resize((layer_side, layer_side), Image.LANCZOS)
            canvas.alpha_composite(scaled, ((px - layer_side) // 2, (px - layer_side) // 2))
            canvas.save(out_dir / name, optimize=True)

    # In-app emblem (splash, About): emblem over transparent, 640px square.
    drawable = RES / "drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)
    emblem_rgba.resize((640, 640), Image.LANCZOS).save(drawable / "isai_emblem.png", optimize=True)

    # Store icon: emblem on the brand background, 512px, full-bleed square.
    store = Image.new("RGBA", (512, 512), BACKGROUND + (255,))
    scale = (512 * 0.74) / height
    layer_side = round(side * scale)
    store.alpha_composite(emblem_rgba.resize((layer_side, layer_side), Image.LANCZOS),
                          ((512 - layer_side) // 2, (512 - layer_side) // 2))
    store.convert("RGB").save(ROOT / "assets" / "isai-play-store-icon-512.png", optimize=True)
    print("brand assets written")


if __name__ == "__main__":
    main()
