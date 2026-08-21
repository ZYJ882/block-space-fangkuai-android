from pathlib import Path
from PIL import Image

PROJECT = Path("/home/ubuntu/TetrisNative")
SOURCE = PROJECT / "artwork/icon-v117/block_space_flat_icon.png"
DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

with Image.open(SOURCE) as source:
    icon = source.convert("RGBA")
    for directory, size in DENSITIES.items():
        destination = PROJECT / "app/src/main/res" / directory / "ic_launcher.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        icon.resize((size, size), Image.Resampling.LANCZOS).save(destination, optimize=True)

print("Generated Android launcher icon resources:")
for directory, size in DENSITIES.items():
    print(f"{directory}/ic_launcher.png: {size}x{size}")
