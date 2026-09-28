"""Crops the Roborazzi screenshots down to the component each guide section talks about.

The full captures stay in docs/docs/images/ (they are the source of truth, refreshed by
`./gradlew recordRoborazziDebug`); this script only cuts regions out of them into images/crop/.

    python3 docs/tools/crop.py
"""
from pathlib import Path

from PIL import Image

IMAGES = Path(__file__).resolve().parents[1] / "docs" / "images"
OUT = IMAGES / "crop"

# name: (source, (left, top, right, bottom)) in source pixels.
CROPS = {
    # Home — dark and light, same regions, so pages can follow the reader's theme.
    "home-hero": ("screen-home", (0, 0, 998, 780)),
    "home-modes": ("screen-home", (0, 800, 998, 1240)),
    "home-record": ("screen-home", (0, 1250, 998, 1480)),
    "home-arcade": ("screen-home", (0, 1500, 998, 2153)),
    "home-hero-light": ("screen-home-light", (0, 0, 998, 780)),
    "home-modes-light": ("screen-home-light", (0, 800, 998, 1240)),
    "home-record-light": ("screen-home-light", (0, 1250, 998, 1480)),
    "home-arcade-light": ("screen-home-light", (0, 1500, 998, 2153)),
    # Game
    "game-cards": ("screen-game", (0, 175, 998, 560)),
    "game-board": ("screen-game", (0, 580, 998, 1520)),
    # Awards
    "awards-collectors": ("screen-awards", (0, 180, 998, 640)),
    "awards-meters": ("screen-awards", (0, 620, 998, 1060)),
    "awards-cabinet": ("screen-awards", (0, 1060, 998, 1900)),
    # Scores
    "scores-tabs": ("screen-scores", (0, 180, 998, 330)),
    "scores-card": ("screen-scores", (0, 330, 998, 920)),
    # Settings
    "settings-preview": ("screen-settings", (0, 160, 998, 620)),
    "settings-colours": ("screen-settings", (0, 620, 998, 1240)),
    "settings-sliders": ("screen-settings", (0, 1240, 998, 1880)),
    # LAN
    "lan-identity": ("screen-lan", (0, 230, 998, 450)),
    "lan-host": ("screen-lan", (0, 650, 998, 1140)),
    "lan-join": ("screen-lan", (0, 1140, 998, 1760)),
    # Word Hive
    "hive-top": ("screen-hive", (0, 180, 998, 480)),
    "hive-board": ("screen-hive", (0, 820, 998, 1700)),
    # Blocks
    "blocks-hud": ("screen-blocks", (0, 0, 998, 180)),
    "blocks-well": ("screen-blocks", (0, 1520, 998, 2153)),
    # Solo setup
    "solo-difficulty": ("screen-solo-setup", (0, 180, 998, 720)),
    "solo-record": ("screen-solo-setup", (0, 760, 998, 1560)),
    # Celebration
    "celebration-medal": ("celebration", (0, 300, 998, 1450)),
    # Ludo Palace
    "ludo-topbar": ("screen-ludo", (0, 0, 998, 140)),
    "ludo-board": ("screen-ludo", (0, 540, 998, 1470)),
    "ludo-seat": ("screen-ludo", (0, 1500, 330, 1820)),
    "ludo-panel": ("screen-ludo", (0, 1900, 998, 2153)),
    "ludo-duel": ("screen-ludo-duel", (0, 1720, 998, 2153)),
    "ludo-lobby-board": ("screen-ludo-lobby", (214, 180, 784, 650)),
    "ludo-lobby-seats": ("screen-ludo-lobby", (0, 900, 998, 2140)),
    # Board close-ups: the ✕ and ◯ corner of the mark film strip.
    "mark-80": ("mark-frame-80", (0, 0, 640, 640)),
    "mark-180": ("mark-frame-180", (0, 0, 640, 640)),
    "mark-300": ("mark-frame-300", (0, 0, 640, 640)),
    "mark-700": ("mark-frame-700", (0, 0, 640, 640)),
    # Components sheet, one control each.
    "ui-buttons": ("components", (20, 20, 930, 200)),
    "ui-icons": ("components", (20, 195, 930, 330)),
    "ui-segmented": ("components", (20, 340, 980, 480)),
    "ui-slider": ("components", (40, 500, 960, 610)),
    "ui-panel": ("components", (20, 620, 980, 840)),
}


def main() -> None:
    OUT.mkdir(exist_ok=True)
    for name, (src, box) in CROPS.items():
        with Image.open(IMAGES / f"{src}.png") as im:
            im.crop(box).save(OUT / f"{name}.png", optimize=True)
    print(f"{len(CROPS)} crops written to {OUT}")


if __name__ == "__main__":
    main()
