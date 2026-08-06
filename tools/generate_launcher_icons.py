"""Generate Android launcher icon assets from a square raster source."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter


DENSITIES = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}
BACKGROUND_COLOR = (0, 111, 171, 255)


def remove_connected_light_background(image: Image.Image) -> Image.Image:
    rgb = image.convert("RGB")
    candidate = Image.new("L", rgb.size)
    pixels = rgb.get_flattened_data()
    candidate.putdata(
        [
            0 if min(pixel) >= 185 and max(pixel) - min(pixel) <= 30 else 255
            for pixel in pixels
        ],
    )
    # Close narrow white strokes that belong to the artwork but touch the outer
    # white canvas (notably the folded map along the left edge).
    candidate = candidate.filter(ImageFilter.MaxFilter(41))
    candidate = candidate.filter(ImageFilter.MinFilter(41))
    ImageDraw.floodfill(candidate, (0, 0), 128, thresh=0)
    alpha = candidate.point(lambda value: 0 if value == 128 else 255)
    alpha = alpha.filter(ImageFilter.GaussianBlur(1.1))
    result = rgb.convert("RGBA")
    result.putalpha(alpha)
    return result


def visible_crop(image: Image.Image) -> Image.Image:
    alpha = image.getchannel("A")
    bbox = alpha.point(lambda value: 255 if value >= 8 else 0).getbbox()
    if bbox is None:
        raise ValueError("The source image has no visible content")
    return image.crop(bbox)


def premultiplied_resize(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    return image.convert("RGBa").resize(size, Image.Resampling.LANCZOS).convert("RGBA")


def contain_square(image: Image.Image, size: int, fill_ratio: float = 0.94) -> Image.Image:
    target = max(1, round(size * fill_ratio))
    scale = min(target / image.width, target / image.height)
    resized = premultiplied_resize(
        image,
        (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
    )
    canvas = Image.new("RGBA", (size, size))
    canvas.alpha_composite(
        resized,
        ((size - resized.width) // 2, (size - resized.height) // 2),
    )
    return canvas


def circular_variant(image: Image.Image) -> Image.Image:
    mask = Image.new("L", image.size)
    ImageDraw.Draw(mask).ellipse((0, 0, image.width - 1, image.height - 1), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(0.6))
    result = image.copy()
    result.putalpha(ImageChops.multiply(result.getchannel("A"), mask))
    return result


def save_webp(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "WEBP", lossless=True, quality=100, method=6, exact=True)


def build_preview(legacy: Image.Image, rounded: Image.Image, adaptive: Image.Image) -> Image.Image:
    tile = 320
    gap = 32
    preview = Image.new("RGBA", (tile * 3 + gap * 4, tile + gap * 2), (242, 245, 248, 255))
    for index, icon in enumerate((legacy, rounded, adaptive)):
        checker = Image.new("RGBA", (tile, tile), (224, 229, 235, 255))
        draw = ImageDraw.Draw(checker)
        block = 20
        for y in range(0, tile, block):
            for x in range(0, tile, block):
                if (x // block + y // block) % 2:
                    draw.rectangle((x, y, x + block - 1, y + block - 1), fill=(245, 247, 250, 255))
        icon_preview = premultiplied_resize(icon, (tile, tile))
        checker.alpha_composite(icon_preview)
        preview.alpha_composite(checker, (gap + index * (tile + gap), gap))
    return preview


def generate(source_path: Path, project_root: Path) -> None:
    source = Image.open(source_path)
    if source.width != source.height:
        raise ValueError("Launcher icon source must be square")

    cutout = visible_crop(remove_connected_light_background(source))
    master = contain_square(cutout, 1024)
    round_master = circular_variant(master)

    resource_root = project_root / "app" / "src" / "main" / "res"
    for density, (legacy_size, foreground_size) in DENSITIES.items():
        directory = resource_root / f"mipmap-{density}"
        save_webp(premultiplied_resize(master, (legacy_size, legacy_size)), directory / "ic_launcher.webp")
        save_webp(
            premultiplied_resize(round_master, (legacy_size, legacy_size)),
            directory / "ic_launcher_round.webp",
        )
        save_webp(
            premultiplied_resize(master, (foreground_size, foreground_size)),
            directory / "ic_launcher_foreground.webp",
        )

    artwork = project_root / "artwork"
    artwork.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source_path, artwork / "ic_launcher_source.png")
    master.save(artwork / "ic_launcher_master.png", "PNG", optimize=True)

    play_store = Image.new("RGBA", master.size, BACKGROUND_COLOR)
    play_store.alpha_composite(master)
    premultiplied_resize(play_store, (512, 512)).convert("RGB").save(
        artwork / "play_store_icon.png",
        "PNG",
        optimize=True,
    )
    build_preview(master, round_master, play_store).convert("RGB").save(
        artwork / "ic_launcher_preview.png",
        "PNG",
        optimize=True,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("project_root", type=Path)
    args = parser.parse_args()
    generate(args.source.resolve(), args.project_root.resolve())


if __name__ == "__main__":
    main()
