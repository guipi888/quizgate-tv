# -*- coding: utf-8 -*-
"""生成 Android TV 图标与横幅资源（Leanback 桌面要求 PNG 位图）。

用法：python tools/gen_assets.py
产出：
  app/src/main/res/drawable-xhdpi/tv_banner.png   (320x180)
  app/src/main/res/mipmap-hdpi/ic_launcher.png    (162x162)
  app/src/main/res/mipmap-hdpi/ic_launcher_round.png (162x162)
"""
import os

from PIL import Image, ImageDraw, ImageFont

BG = (18, 22, 28, 255)        # #12161C
ACCENT = (217, 122, 62, 255)  # #D97A3E 桂皮橙
TEXT = (245, 237, 228, 255)   # #F5EDE4

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BANNER = os.path.join(ROOT, "app/src/main/res/drawable-xhdpi/tv_banner.png")
ICON = os.path.join(ROOT, "app/src/main/res/mipmap-hdpi/ic_launcher.png")
ICON_ROUND = os.path.join(ROOT, "app/src/main/res/mipmap-hdpi/ic_launcher_round.png")
STAT_LOCK = os.path.join(ROOT, "app/src/main/res/drawable-hdpi/ic_stat_lock.png")

FONT_CANDIDATES = [
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/msyhbd.ttc",
    "/System/Library/Fonts/PingFang.ttc",
    "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
]


def load_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                pass
    return ImageFont.load_default()


def draw_check(draw, cx, cy, scale, color):
    """画对勾：两段折线。"""
    pts = [
        (cx - 34 * scale, cy + 2 * scale),
        (cx - 8 * scale, cy + 28 * scale),
        (cx + 38 * scale, cy - 30 * scale),
    ]
    draw.line(pts, fill=color, width=int(12 * scale), joint="curve")


def make_icon(round_mode):
    size = 162
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if round_mode:
        d.ellipse([0, 0, size - 1, size - 1], fill=BG)
    else:
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=28, fill=BG)
    draw_check(d, size // 2, size // 2, 1.6, ACCENT)
    return img


def make_banner():
    w, h = 320, 180
    img = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(img)
    # 左侧徽标区
    d.rounded_rectangle([24, 34, 132, 146], radius=20, fill=(28, 35, 45, 255))
    draw_check(d, 78, 90, 1.9, ACCENT)
    # 右侧文字
    font = load_font(40)
    d.text((150, 52), "答题门禁", font=font, fill=TEXT)
    font_small = load_font(20)
    d.text((150, 106), "QuizGate TV", font=font_small, fill=(154, 164, 176, 255))
    return img


def make_stat_lock():
    """通知栏小图标：24x24 白色锁形，透明背景。"""
    size = 24
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    white = (255, 255, 255, 255)
    # 锁梁（上半圆弧）
    d.arc([7, 2, 17, 14], start=180, end=360, fill=white, width=2)
    # 锁身（圆角矩形）
    d.rounded_rectangle([5, 11, 19, 22], radius=2, fill=white)
    # 锁孔（背景色镂空）
    d.rectangle([11, 14, 13, 18], fill=(0, 0, 0, 0))
    return img


def main():
    os.makedirs(os.path.dirname(BANNER), exist_ok=True)
    os.makedirs(os.path.dirname(ICON), exist_ok=True)
    os.makedirs(os.path.dirname(STAT_LOCK), exist_ok=True)
    make_banner().save(BANNER)
    make_icon(False).save(ICON)
    make_icon(True).save(ICON_ROUND)
    make_stat_lock().save(STAT_LOCK)
    print("banner:", BANNER)
    print("icon:", ICON)
    print("icon_round:", ICON_ROUND)
    print("stat_lock:", STAT_LOCK)


if __name__ == "__main__":
    main()
