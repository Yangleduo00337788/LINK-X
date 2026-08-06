# -*- coding: utf-8 -*-
"""
空状态插画去底：对四角渐变背景建模，从边缘连通移除背景并羽化边缘。

用法: python scripts/make-logo-transparent.py
"""
from __future__ import annotations

import shutil
import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / (
    "assets/c__Users_yangleduo_AppData_Roaming_Cursor_User_workspaceStorage_ef0844aa4f52a696ff6f6fa13b7ab5dd_images"
    "_ad0207af6ac15f226460429abffab75f_720-7db5938f-0f5d-4d21-b57c-4cb33d0acf62.png"
)
# 项目内源图备选（若 Cursor assets 路径不存在）
SRC_FALLBACK = ROOT / "linkx-client" / "src" / "assets" / "logo-linkx-empty-source.png"
DST = ROOT / "linkx-client" / "src" / "assets" / "logo-linkx-empty.png"

THRESHOLD = 14
FEATHER = 8
CORNER_SAMPLE = 24


def recenter_visual_mass(im: Image.Image) -> Image.Image:
    """按 alpha^2 加权重心将插画主体水平居中，避免几何居中偏左。"""
    arr = np.array(im.convert("RGBA"))
    weight = (arr[:, :, 3].astype(np.float32) / 255) ** 2
    total = float(weight.sum())
    if total <= 0:
        return im
    w = arr.shape[1]
    cx = float((np.arange(w) * weight.sum(axis=0)).sum() / total)
    shift = int(round(w / 2 - cx))
    if shift == 0:
        return im
    canvas = Image.new("RGBA", im.size, (0, 0, 0, 0))
    canvas.paste(im, (shift, 0))
    return canvas


def remove_gradient_background(src: Path, dst: Path) -> None:
    im = Image.open(src).convert("RGBA")
    arr = np.array(im, dtype=np.float32)
    h, w = arr.shape[:2]
    s = CORNER_SAMPLE

    def corner_color(x0: int, y0: int) -> np.ndarray:
        patch = arr[y0 : y0 + s, x0 : x0 + s, :3]
        return patch.reshape(-1, 3).mean(axis=0)

    c00, c10 = corner_color(0, 0), corner_color(w - s, 0)
    c01, c11 = corner_color(0, h - s), corner_color(w - s, h - s)
    y = np.linspace(0, 1, h, dtype=np.float32)[:, None, None]
    x = np.linspace(0, 1, w, dtype=np.float32)[None, :, None]
    bg = (1 - x) * (1 - y) * c00 + x * (1 - y) * c10 + (1 - x) * y * c01 + x * y * c11
    dist = np.linalg.norm(arr[:, :, :3] - bg, axis=2)

    visited = np.zeros((h, w), dtype=bool)
    q: deque[tuple[int, int]] = deque()
    for xi in range(w):
        for yi in (0, h - 1):
            if dist[yi, xi] < THRESHOLD:
                visited[yi, xi] = True
                q.append((xi, yi))
    for yi in range(h):
        for xi in (0, w - 1):
            if dist[yi, xi] < THRESHOLD and not visited[yi, xi]:
                visited[yi, xi] = True
                q.append((xi, yi))

    while q:
        xi, yi = q.popleft()
        for nx, ny in ((xi + 1, yi), (xi - 1, yi), (xi, yi + 1), (xi, yi - 1)):
            if 0 <= nx < w and 0 <= ny < h and not visited[ny, nx] and dist[ny, nx] < THRESHOLD:
                visited[ny, nx] = True
                q.append((nx, ny))

    out = arr.copy()
    alpha = np.where(visited, 0.0, 1.0)
    edge_zone = (~visited) & (dist < THRESHOLD + FEATHER)
    alpha = np.where(edge_zone, np.clip((dist - THRESHOLD) / FEATHER, 0, 1), alpha)
    out[:, :, 3] = (alpha * 255).astype(np.uint8)

    result = Image.fromarray(out.astype(np.uint8))
    bbox = result.getbbox()
    if bbox:
        result = result.crop(bbox)
    result = recenter_visual_mass(result)
    dst.parent.mkdir(parents=True, exist_ok=True)
    result.save(dst)
    print(f"saved {dst} ({result.size[0]}x{result.size[1]})")


def main() -> None:
    src = SRC if SRC.exists() else SRC_FALLBACK
    if not src.exists():
        print("source image not found", file=sys.stderr)
        sys.exit(1)
    remove_gradient_background(src, DST)


if __name__ == "__main__":
    main()
