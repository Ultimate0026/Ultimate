#!/usr/bin/env python3
"""Builds the 20 slime pet models.

    python3 slimes/tools/build_slimes.py

Writes slimes/models/<Name>.glb, slimes/roblox/SlimeSetup.lua and slimes/models/slimes.json.
Every model is in studs, faces -Z, has its bottom centre at the origin and stays under 3k triangles.
"""
import json
import math
import os
import random

import numpy as np

from meshkit import (Body, Piece, bezier, box, closed, cone, cylinder, ellipsoid, extrude, frame,
                     icosphere, lathe, normalize, place, prism, ring_band, ring_mesh, rot,
                     rot_from_to, sphere, star_polygon, tf, torus, tube, write_glb)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
X, Y, Z = np.eye(3)
DEG = math.pi / 180
FRONT = 270 * DEG
BACK = 90 * DEG

INK = "#1B1B1F"
WHITE = "#FFFFFF"


class Slime:
    def __init__(self, name, rarity, size, face="Eyes"):
        self.name, self.rarity, self.size, self.face = name, rarity, size, face
        self.parts, self.looks = {}, {}

    def look(self, part, color, material="SmoothPlastic", transparency=0.0):
        self.looks[part] = {"color": color, "material": material, "transparency": transparency}

    def add(self, part, piece):
        self.parts.setdefault(part, []).append(piece)


# ---------------------------------------------------------------- face helpers

def eyes(s, B, y, sep, ex, ey, color=INK, shine=True, tilt=0.0, material="SmoothPlastic", depth=0.42):
    s.look("Eyes", color, material)
    if shine:
        s.look("EyeShine", WHITE)
    for side in (-1, 1):
        local = rot(Z, tilt * side)
        s.add("Eyes", B.on_face(ellipsoid(ex, ey, depth * ex), side * sep, y, 0.0, local))
        if shine:
            sh = tf(ellipsoid(0.28 * ex, 0.28 * ex, 0.14 * ex, 8, 5),
                    t=(-0.32 * ex, 0.38 * ey, depth * 0.8 * ex))
            s.add("EyeShine", B.on_face(sh, side * sep, y, 0.0, local))


def curve(s, part, B, pts, r, out=None, taper=0.6, k=14):
    path = B.face_path(bezier(pts, k), r * 0.35 if out is None else out)
    rad = np.full(len(path), r)
    rad[0] = rad[-1] = r * taper
    s.add(part, tube(path, rad, 8))


def smile(s, B, y, w, depth, r, color=INK, part="Mouth"):
    s.look(part, color)
    pts = [(w * t, y + depth * (t * t - 1)) for t in np.linspace(-1, 1, 14)]
    path = B.face_path(pts, r * 0.35)
    rad = np.full(len(path), r)
    rad[0] = rad[-1] = r * 0.7
    s.add(part, tube(path, rad, 8))


def cheeks(s, B, y, sep, W, color, part="Cheeks"):
    s.look(part, color)
    for side in (-1, 1):
        s.add(part, B.on_face(ellipsoid(0.075 * W, 0.045 * W, 0.022 * W, 10, 6), side * sep, y))


def surface_frame(tangent_up, out):
    """Rotation whose local y follows tangent_up and local z follows out."""
    out = normalize(out)
    up = normalize(tangent_up - np.dot(tangent_up, out) * out)
    return np.column_stack([np.cross(up, out), up, out])


def crown(s, part, gem_parts, r, band_h, points, point_h, point_w, thick, balls=True):
    """Crown around the Y axis with its base at the origin. gem_parts cycles per gem."""
    s.add(part, ring_band(r - thick, r, band_h, 24))
    for k in range(points):
        phi = FRONT + k * 2 * math.pi / points
        radial = np.array([math.cos(phi), 0, math.sin(phi)])
        tangent = np.array([-math.sin(phi), 0, math.cos(phi)])
        R = np.column_stack([tangent, Y, radial])
        tri = extrude([(-point_w, 0), (point_w, 0), (0, point_h)], thick)
        s.add(part, tf(tri, R=R, t=radial * (r - thick / 2) + Y * (band_h - 0.01 * r)))
        if balls:
            s.add(part, tf(sphere(8, 4), s=thick * 1.1,
                           t=radial * (r - thick / 2) + Y * (band_h + point_h - 0.01 * r)))
        gem = gem_parts[k % len(gem_parts)]
        g_phi = phi + math.pi / points
        g_rad = np.array([math.cos(g_phi), 0, math.sin(g_phi)])
        gp = tf(ellipsoid(thick * 1.3, thick * 1.3, thick * 0.7, 6, 4),
                R=frame(g_rad), t=g_rad * r + Y * band_h * 0.5)
        gp.flat = True
        s.add(gem, gp)


def wing(poly, span, height, thick):
    P = [(x * span, y * height) for x, y in poly]
    return extrude(P, thick)


def wing_frame(theta, up_tilt):
    """Local x along the wing span (outward, a little back and up), y up, z thickness."""
    d = np.array([math.cos(theta), 0, math.sin(theta)])
    d = rot(np.cross(Y, d), -up_tilt) @ d
    up = normalize(Y - np.dot(Y, d) * d)
    return np.column_stack([d, up, np.cross(d, up)])


# ---------------------------------------------------------------- the slimes

def lil_green():
    s = Slime("LilGreen", "Common", 3)
    W = 3.0
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#69D24B")
    s.add("Body", B.piece())
    eyes(s, B, 0.55 * H, 0.16 * W, 0.07 * W, 0.1 * W)
    smile(s, B, 0.4 * H, 0.1 * W, 0.055 * W, 0.022 * W)
    return s


def z_letter(size, thick):
    P = [(0, 1), (1, 1), (1, 0.8), (0.32, 0.2), (1, 0.2), (1, 0), (0, 0), (0, 0.2), (0.68, 0.8), (0, 0.8)]
    return extrude([(x * size, y * size) for x, y in P], thick)


def blue_blob():
    s = Slime("BlueBlob", "Common", 3)
    W = 3.0
    B = Body(W, 0.78 * W)
    H = B.H
    body = "#4FA3F7"
    s.look("Body", body)
    s.add("Body", B.piece())
    ex, ey, y, sep = 0.075 * W, 0.085 * W, 0.53 * H, 0.17 * W
    eyes(s, B, y, sep, ex, ey, shine=False)
    s.look("Eyelids", body)
    s.look("Lashes", "#16325C")
    lx, ly, lc, lz = 1.3 * ex, 0.7 * ey, 0.55 * ey, 0.55 * ex
    for side in (-1, 1):
        s.add("Eyelids", B.on_face(tf(ellipsoid(lx, ly, lz), t=(0, lc, 0)), side * sep, y))
        pts = []
        for t in np.linspace(-0.92, 0.92, 12):
            x = lx * t
            yy = lc - ly * math.sqrt(max(0.0, 1 - t * t)) * 0.9
            zz = lz * math.sqrt(max(0.0, 1 - t * t - ((yy - lc) / ly) ** 2)) + 0.004 * W
            pts.append((x, yy, zz))
        lash = tube(pts, 0.011 * W, 6)
        s.add("Lashes", B.on_face(lash, side * sep, y))
    smile(s, B, 0.37 * H, 0.045 * W, 0.025 * W, 0.018 * W, color="#16325C")
    s.look("Zzz", "#EAF5FF")
    for size, pos in ((0.13 * W, (-0.42 * W, 1.0 * H, -0.05 * W)), (0.085 * W, (-0.6 * W, 1.18 * H, 0.0))):
        zl = z_letter(size, 0.035 * W)
        s.add("Zzz", tf(zl, R=rot(Y, 180) @ rot(Z, 12), t=pos))
    return s


def pinky():
    s = Slime("Pinky", "Common", 3)
    W = 3.0
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#FF9CC8")
    s.add("Body", B.piece())
    eyes(s, B, 0.56 * H, 0.16 * W, 0.068 * W, 0.095 * W)
    cy, csep = 0.43 * H, 0.27 * W
    cheeks(s, B, cy, csep, W, "#FF5E99")
    s.look("BlushLines", "#E0407A")
    for side in (-1, 1):
        for i in (-1, 0, 1):
            fx = side * csep + i * 0.035 * W
            curve(s, "BlushLines", B, [(fx + 0.012 * W, cy + 0.025 * W), (fx - 0.012 * W, cy - 0.025 * W)],
                  0.008 * W, out=0.03 * W, taper=0.8, k=4)
    smile(s, B, 0.41 * H, 0.06 * W, 0.035 * W, 0.02 * W)
    return s


def drip(B, theta, y_top, length, r):
    ys = np.linspace(y_top, y_top - length, 10)
    prof = np.array([0.0, 0.55, 0.6, 0.62, 0.66, 0.74, 0.88, 1.0, 0.8, 0.0])
    sink = np.linspace(-0.7, -0.15, 10)
    rad = r * prof
    pts = np.array([B.pt(theta, yy, rr * k)[0] for yy, rr, k in zip(ys, rad, sink)])
    return tube(pts, rad, 7)


def mudsy():
    s = Slime("Mudsy", "Common", 3)
    W = 3.0
    B = Body(W, 0.76 * W, sag=0.2)
    H = B.H
    s.look("Body", "#7B5134")
    s.add("Body", B.piece())
    s.look("Puddle", "#5E3B24")
    r0 = float(B.R(0.0))
    s.add("Puddle", lathe([[(0, 0.035 * W), (r0 + 0.08 * W, 0.03 * W), (r0 + 0.13 * W, 0.012 * W),
                            (r0 + 0.14 * W, 0.0)], [(r0 + 0.14 * W, 0.0), (0, 0.0)]], 32))
    s.look("Drips", "#7B5134")
    for th, yt, ln in ((212, 0.8, 0.62), (318, 0.78, 0.55), (345, 0.86, 0.7),
                       (40, 0.82, 0.6), (100, 0.9, 0.72), (150, 0.8, 0.58)):
        s.add("Drips", drip(B, th * DEG, yt * H, ln * H, 0.06 * W))
    # derpy eyes: one big, one small, looking different ways
    s.look("Eyes", WHITE)
    s.look("Pupils", INK)
    for fx, y, ex, ey, px, py in ((-0.17 * W, 0.58 * H, 0.1 * W, 0.115 * W, -0.035 * W, 0.04 * W),
                                  (0.15 * W, 0.55 * H, 0.065 * W, 0.07 * W, 0.02 * W, -0.022 * W)):
        ez = 0.45 * ex
        s.add("Eyes", B.on_face(ellipsoid(ex, ey, ez), fx, y))
        s.add("Pupils", B.on_face(tf(ellipsoid(0.034 * W, 0.034 * W, 0.016 * W, 10, 6),
                                     t=(px, py, ez * 0.82)), fx, y))
    s.look("Mouth", "#3B1E10")
    s.add("Mouth", B.on_face(ellipsoid(0.1 * W, 0.05 * W, 0.022 * W, 12, 8), 0.02 * W, 0.34 * H, 0.0,
                             rot(Z, -8)))
    s.look("Tongue", "#FF7F9A")
    s.add("Tongue", B.on_face(ellipsoid(0.042 * W, 0.065 * W, 0.022 * W, 12, 8), 0.07 * W, 0.28 * H,
                              0.018 * W, rot(Z, 15)))
    s.look("Tooth", WHITE)
    s.add("Tooth", B.on_face(box(0.035 * W, 0.04 * W, 0.02 * W), -0.025 * W, 0.355 * H, 0.012 * W))
    return s


def bubblegum():
    s = Slime("Bubblegum", "Uncommon", 3.2)
    W = 3.2
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#FF3FA4")
    s.add("Body", B.piece())
    eyes(s, B, 0.62 * H, 0.17 * W, 0.068 * W, 0.092 * W)
    cheeks(s, B, 0.5 * H, 0.29 * W, W, "#FF77BE")
    my = 0.37 * H
    s.look("Mouth", "#B0186A")
    s.add("Mouth", B.on_face(tf(torus(0.04 * W, 0.013 * W, 16, 8), R=rot(X, 90)), 0.0, my))
    p, n = B.front(0.0, my)
    rb = 0.21 * W
    c = p + n * (rb * 0.97)
    s.look("Bubble", "#FF8CCB", transparency=0.3)
    s.add("Bubble", tf(sphere(20, 12), s=rb, t=c))
    s.look("BubbleShine", WHITE, transparency=0.1)
    sn = normalize([0.4, 0.5, -0.75])
    s.add("BubbleShine", place(ellipsoid(0.05 * W, 0.032 * W, 0.012 * W, 12, 8), c + sn * rb, sn,
                               rot(Z, 30)))
    return s


def lemon_drop():
    s = Slime("LemonDrop", "Uncommon", 3.2)
    W = 3.2
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#FFE873")
    s.add("Body", B.piece())
    eyes(s, B, 0.52 * H, 0.16 * W, 0.068 * W, 0.092 * W)
    smile(s, B, 0.37 * H, 0.09 * W, 0.05 * W, 0.021 * W)
    cheeks(s, B, 0.4 * H, 0.28 * W, W, "#FFB36B")
    # a round lemon slice worn like a beret, tipped toward the front
    R, t = 0.27 * W, 0.06 * W
    s.look("LemonRind", "#F2C400")
    s.look("LemonFlesh", "#FFF27A")
    s.look("LemonPith", "#FFFCE6")
    M = rot(Z, 10) @ rot(X, -32)
    at = np.array([0, H - 0.045 * W, 0.04 * W])
    s.add("LemonFlesh", tf(cylinder(0.84 * R, t, 32), R=M, t=at))
    s.add("LemonPith", tf(ring_band(0.84 * R, 0.9 * R, t, 32), R=M, t=at))
    s.add("LemonRind", tf(ring_band(0.9 * R - 0.001, R, t * 1.04, 32), R=M, t=at - M @ Y * t * 0.02))
    for k in range(5):
        spoke = tf(box(1.66 * R, t + 0.008 * W, 0.022 * W), R=rot(Y, k * 36), t=(0, t / 2, 0))
        s.add("LemonPith", tf(spoke, R=M, t=at))
    return s


def leaf_polygon(L, w, k=10):
    top = [(L * u, w * math.sin(math.pi * u) ** 0.85) for u in np.linspace(0, 1, k)]
    bot = [(L * u, -w * math.sin(math.pi * u) ** 0.85) for u in np.linspace(1, 0, k)[1:-1]]
    return top + bot


def minty():
    s = Slime("Minty", "Uncommon", 3.2)
    W = 3.2
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#8FF0C8")
    s.add("Body", B.piece())
    eyes(s, B, 0.55 * H, 0.16 * W, 0.068 * W, 0.092 * W)
    smile(s, B, 0.39 * H, 0.08 * W, 0.045 * W, 0.021 * W)
    cheeks(s, B, 0.43 * H, 0.28 * W, W, "#FF9EB5")
    L, lw, lt = 0.42 * W, 0.12 * W, 0.025 * W
    s.look("Leaf", "#2FA84F")
    s.look("LeafVein", "#1E7A38")
    s.look("Stem", "#6B4A2B")
    M = rot(Y, -20) @ rot(Z, 48)
    base = np.array([0.02 * W, H + 0.06 * W, 0.0])
    s.add("Leaf", tf(extrude(leaf_polygon(L, lw), lt), R=M, t=base))
    for zs in (-1, 1):
        vein = tube([(0.05 * L, 0, zs * lt * 0.5), (0.5 * L, 0.01 * L, zs * lt * 0.5),
                     (0.88 * L, 0, zs * lt * 0.5)], [0.012 * W, 0.009 * W, 0.0], 6)
        s.add("LeafVein", tf(vein, R=M, t=base))
    stem = bezier([base + np.array([0, -0.12 * W, 0]), base + np.array([0, -0.03 * W, 0]),
                   base + M @ np.array([0.06 * L, 0, 0])], 6)
    s.add("Stem", tube(stem, 0.02 * W, 8))
    return s


def top_hat():
    s = Slime("TopHat", "Rare", 3.6)
    W = 3.6
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#F2A65A")
    s.add("Body", B.piece())
    ey_y, sep, ex, ey = 0.56 * H, 0.16 * W, 0.068 * W, 0.092 * W
    eyes(s, B, ey_y, sep, ex, ey)
    s.look("Hat", "#1E1E26")
    s.look("HatBand", "#B71C2C")
    rc, hc, rb, hb = 0.17 * W, 0.3 * W, 0.29 * W, 0.022 * W
    M = rot(Z, -9) @ rot(X, 4)
    at = np.array([0.02 * W, B.y_at_radius(rc) + 0.01 * W, 0.0])
    s.add("Hat", tf(cylinder(rb, hb, 32), R=M, t=at))
    s.add("Hat", tf(cylinder(rc * 0.96, hc, 28, r_top=rc), R=M, t=at))
    s.add("HatBand", tf(ring_band(rc * 0.96 - 0.004 * W, rc * 0.97 + 0.006 * W, 0.055 * W, 28), R=M,
                        t=at + M @ Y * (hb + 0.003 * W)))
    # monocle on the viewer's left eye, chain running down the side
    s.look("Monocle", "#E8B830", "Metal")
    s.look("Lens", "#D8F3FF", "Glass", 0.55)
    mx = -sep
    s.add("Monocle", B.on_face(tf(torus(0.11 * W, 0.012 * W, 24, 6), R=rot(X, 90), t=(0, 0, 0.045 * W)),
                               mx, ey_y))
    s.add("Lens", B.on_face(tf(cylinder(0.104 * W, 0.006 * W, 24), R=rot(X, 90), t=(0, 0, 0.042 * W)),
                            mx, ey_y))
    chain = bezier([(mx, ey_y - 0.11 * W), (mx - 0.02 * W, ey_y - 0.3 * W), (mx - 0.2 * W, 0.42 * H),
                    (-0.4 * W, 0.3 * H)], 14)
    s.add("Monocle", tube(B.face_path(chain, 0.04 * W), 0.009 * W, 6))
    s.look("Mustache", "#3B2618")
    my = 0.4 * H
    for side in (-1, 1):
        pts = [(0, my), (side * 0.07 * W, my - 0.045 * W), (side * 0.15 * W, my - 0.035 * W),
               (side * 0.19 * W, my + 0.03 * W), (side * 0.14 * W, my + 0.035 * W)]
        path = B.face_path(bezier(pts, 12), 0.022 * W)
        s.add("Mustache", tube(path, np.linspace(0.032 * W, 0.012 * W, len(path)), 8))
    return s


def ninja():
    s = Slime("Ninja", "Rare", 3.6)
    W = 3.6
    B = Body(W, 0.8 * W)
    H = B.H
    body = "#23232B"
    s.look("Body", body)
    s.add("Body", B.piece())
    s.look("Eyes", WHITE)
    s.look("Pupils", INK)
    s.look("Brows", body)
    y, sep, ex, ey, ez = 0.53 * H, 0.165 * W, 0.085 * W, 0.07 * W, 0.035 * W
    for side in (-1, 1):
        s.add("Eyes", B.on_face(ellipsoid(ex, ey, ez), side * sep, y))
        s.add("Pupils", B.on_face(tf(ellipsoid(0.03 * W, 0.034 * W, 0.012 * W, 12, 8),
                                     t=(-side * 0.025 * W, -0.008 * W, ez * 0.85)), side * sep, y))
        brow = tf(box(0.24 * W, 0.07 * W, 0.08 * W), R=rot(Z, 22 * side), t=(-side * 0.03 * W, 0.075 * W, 0))
        s.add("Brows", B.on_face(brow, side * sep, y))
    s.look("Mouth", "#5A5A66")
    s.add("Mouth", tube(B.face_path([(fx, 0.37 * H - 0.012 * W * math.cos(fx / (0.05 * W) * 1.2))
                                      for fx in np.linspace(-0.05 * W, 0.05 * W, 8)], 0.006 * W),
                        0.014 * W, 6))
    # headband all the way round, knot and tails at the back
    s.look("Headband", "#D7263D")
    y1, y2 = 0.67 * H, 0.79 * H
    ys = np.linspace(y1, y2, 3)
    ro = [(float(B.R(v)) + 0.03 * W, v) for v in ys]
    ri = [(float(B.R(v)) - 0.02 * W, v) for v in ys]
    s.add("Headband", lathe([ro[::-1], [ro[0], ri[0]], ri, [ri[-1], ro[-1]]], 32))
    kp, kn = B.pt(BACK, (y1 + y2) / 2, 0.04 * W)
    s.add("Headband", tf(ellipsoid(0.07 * W, 0.06 * W, 0.05 * W), t=kp))
    for side in (-1, 1):
        pts = bezier([kp, kp + np.array([side * 0.06 * W, -0.04 * W, 0.12 * W]),
                      kp + np.array([side * 0.1 * W, -0.16 * W, 0.2 * W]),
                      kp + np.array([side * 0.16 * W, -0.26 * W, 0.24 * W])], 12)
        s.add("Headband", tube(pts, np.linspace(0.045 * W, 0.035 * W, 12), 8, aspect=0.22, ref=X))
    return s


def lava():
    s = Slime("Lava", "Rare", 3.6)
    W = 3.6
    B = Body(0.95 * W, 0.78 * W)
    H = B.H
    rng = random.Random(10)
    s.look("Core", "#FF5A00", "Neon")
    s.add("Core", Body(W * 0.9, H * 0.95).piece())
    s.look("Body", "#3A2B27", "Slate")

    def to_body(d):
        d = normalize(d)
        th = math.atan2(d[2], d[0])
        yy = min(max(H * (0.45 + 0.55 * d[1]), 0.015 * H), H * 0.999)
        return B.pt(th, yy)

    V, F = icosphere(1)
    for i, v in enumerate(V):
        if v[1] < -0.72:
            continue
        faces = [f for f in F if i in f]
        cents = [normalize(V[list(f)].mean(0)) for f in faces]
        tang = normalize(np.cross(Y if abs(v[1]) < 0.95 else X, v))
        bit = np.cross(v, tang)
        cents.sort(key=lambda c: math.atan2(np.dot(c - v, bit), np.dot(c - v, tang)))
        cp, cn = to_body(v)
        corners = [to_body(c)[0] for c in cents]
        corners = [cp + (q - cp) * 0.84 for q in corners]
        h = 0.03 * W * (0.7 + 0.6 * rng.random())
        top = [q + cn * h for q in corners]
        bot = [q - cn * 0.03 * W for q in corners]
        s.add("Body", prism(top, bot))
    s.look("Eyes", "#FFD23F", "Neon")
    for side in (-1, 1):
        s.add("Eyes", B.on_face(ellipsoid(0.075 * W, 0.055 * W, 0.04 * W), side * 0.16 * W, 0.56 * H,
                                0.05 * W, rot(Z, 14 * side)))
    s.look("Mouth", "#FF8C1A", "Neon")
    pts = [(-0.11 * W, 0.4 * H), (-0.05 * W, 0.36 * H), (0, 0.39 * H), (0.05 * W, 0.36 * H),
           (0.11 * W, 0.4 * H)]
    path = B.face_path(pts, 0.055 * W)
    s.add("Mouth", tube(path, 0.022 * W, 8))
    return s


def frost():
    s = Slime("Frost", "Epic", 4)
    W = 4.0
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#8FD8FF")
    s.add("Body", B.piece())
    eyes(s, B, 0.54 * H, 0.16 * W, 0.066 * W, 0.09 * W, color="#1A2E4A")
    smile(s, B, 0.39 * H, 0.08 * W, 0.045 * W, 0.02 * W, color="#1A2E4A")
    cheeks(s, B, 0.43 * H, 0.28 * W, W, "#FFB8D9")
    s.look("Snow", "#FFFFFF")
    yc = B.y_at_radius(0.27 * W)
    ys = np.linspace(H, yc, 6)[1:]
    outer = [(0.0, H + 0.03 * W)] + [(float(B.R(v)) + 0.03 * W, v) for v in ys]
    inner = [(float(B.R(v)) - 0.02 * W, v) for v in ys[::-1]] + [(0.0, H - 0.02 * W)]
    s.add("Snow", lathe([outer, [outer[-1], inner[0]], inner], 28))
    s.look("Crystals", "#DDF7FF", "Glass", 0.2)
    for ang, rad, h, r, tilt in ((0, 0.0, 0.34, 0.055, 4), (200, 0.12, 0.22, 0.045, 24),
                                 (330, 0.13, 0.25, 0.045, 26), (90, 0.12, 0.2, 0.04, 28),
                                 (140, 0.17, 0.15, 0.035, 34), (30, 0.17, 0.14, 0.034, 34),
                                 (260, 0.18, 0.12, 0.03, 38)):
        th = ang * DEG
        out = np.array([math.cos(th), 0, math.sin(th)])
        rr = rad * W
        base = np.array([rr * math.cos(th), B.y_at_radius(max(rr, 1e-3)) - 0.03 * W, rr * math.sin(th)])
        if rad == 0:
            base[1] = H - 0.04 * W
        direction = normalize(Y * math.cos(tilt * DEG) + out * math.sin(tilt * DEG))
        hh, rw = h * W, r * W
        cr = lathe([[(0, hh + rw * 1.6), (rw, hh)], [(rw, hh), (rw, 0)], [(rw, 0), (0, -rw * 0.8)]], 6,
                   flat=True)
        s.add("Crystals", tf(cr, R=rot_from_to(Y, direction) @ rot(Y, ang * 1.7), t=base))
    return s


def royal():
    s = Slime("Royal", "Epic", 4)
    W = 4.0
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#8A4FD8")
    s.add("Body", B.piece())
    eyes(s, B, 0.54 * H, 0.16 * W, 0.066 * W, 0.09 * W)
    smile(s, B, 0.39 * H, 0.08 * W, 0.04 * W, 0.02 * W)
    s.look("Crown", "#F2C230", "Metal")
    s.look("Gems", "#E0115F")
    tmp = Slime("tmp", "", 0)
    rc = 0.14 * W
    crown(tmp, "Crown", ["Gems"], rc, 0.055 * W, 5, 0.09 * W, 0.042 * W, 0.018 * W)
    M = rot(Z, -12) @ rot(X, 5)
    at = np.array([0.03 * W, B.y_at_radius(rc) - 0.015 * W, 0])
    for part, pieces in tmp.parts.items():
        for p in pieces:
            s.add(part, tf(p, R=M, t=at))
    # cape over the back, flaring toward the ground
    s.look("Cape", "#C1121F")
    y0, y1 = 0.04 * H, 0.8 * H
    loops = []
    for th in np.linspace(5 * DEG, 175 * DEG, 14):
        outer, inner = [], []
        for yy in np.linspace(y1, y0, 7):
            f = ((y1 - yy) / (y1 - y0)) ** 1.6
            o = 0.03 * W + 0.09 * W * f
            outer.append(B.pt(th, yy, o)[0])
            inner.append(B.pt(th, yy, o - 0.025 * W)[0])
        loop = np.array(outer + inner[::-1])
        loop[:, 1] = np.maximum(loop[:, 1], 0.0)
        loops.append(loop)
    V, F = ring_mesh(loops, cap="strip")
    s.add("Cape", closed(V, F))
    s.look("Collar", WHITE)
    path = [B.pt(th, y1, 0.04 * W)[0] for th in np.linspace(0, 180 * DEG, 14)]
    s.add("Collar", tube(path, 0.04 * W, 8))
    return s


class GhostBody(Body):
    def __init__(self, W, H):
        self.W, self.H = W, H
        self.Rd = (W / 2) / 1.12
        self.ys = 0.55 * H
        self.hd = H - self.ys
        self.amp = 0.1 * H

    def R(self, y):
        y = np.asarray(y, float)
        dome = self.Rd * np.sqrt(np.clip(1 - ((y - self.ys) / self.hd) ** 2, 0, 1))
        skirt = self.Rd * (1 + 0.12 * (self.ys - y) / self.ys)
        return np.where(y >= self.ys, dome, skirt)

    def hem(self, th):
        return self.amp * (0.5 + 0.5 * np.cos(6 * th))

    def piece(self, n=36):
        th = np.arange(n) * 2 * np.pi / n
        c, s_ = np.cos(th), np.sin(th)
        hem = self.hem(th)

        def ring(r, y):
            r = np.broadcast_to(r, (n,))
            y = np.broadcast_to(y, (n,))
            return np.stack([r * c, y, r * s_], 1)

        rings = [np.array([[0, self.H, 0]])]
        for phi in np.linspace(0, math.pi / 2, 9)[1:]:
            rings.append(ring(self.Rd * math.sin(phi), self.ys + self.hd * math.cos(phi)))
        for u in np.linspace(0, 1, 7)[1:]:
            y = self.ys - (self.ys - hem) * u
            rings.append(ring(self.R(y), y))
        # closed underside: the wavy hem curls in to a shallow dome
        r_hem = self.R(hem)
        for k, (f, lift) in enumerate(((0.9, 0.03), (0.65, 0.06), (0.35, 0.08))):
            rings.append(ring(r_hem * f, hem * (1 - k / 3) + lift * self.H))
        rings.append(np.array([[0, 0.09 * self.H, 0]]))
        V, F = ring_mesh(rings)
        return closed(V, F)


def ghost():
    s = Slime("Ghost", "Epic", 4)
    W = 4.0
    B = GhostBody(W, 1.05 * W)
    H = B.H
    s.look("Body", "#F2F5FF", transparency=0.3)
    s.add("Body", B.piece())
    s.look("Arms", "#F2F5FF", transparency=0.3)
    for side in (-1, 1):
        p, n = B.pt(math.pi if side > 0 else 0.0, 0.5 * H, 0.01 * W)
        s.add("Arms", tf(ellipsoid(0.12 * W, 0.06 * W, 0.07 * W), R=rot(Z, 25 * side), t=p))
    eyes(s, B, 0.66 * H, 0.15 * W, 0.06 * W, 0.1 * W, color="#2B2D42")
    s.look("Mouth", "#2B2D42")
    s.add("Mouth", B.on_face(ellipsoid(0.05 * W, 0.065 * W, 0.022 * W), 0.0, 0.5 * H))
    cheeks(s, B, 0.56 * H, 0.26 * W, W, "#C9D3FF")
    return s


def devil():
    s = Slime("Devil", "Legendary", 4.5)
    W = 4.5
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#D9263A")
    s.add("Body", B.piece())
    eyes(s, B, 0.55 * H, 0.16 * W, 0.064 * W, 0.085 * W)
    s.look("Brows", "#5E0A16")
    for side in (-1, 1):
        curve(s, "Brows", B, [(side * 0.09 * W, 0.66 * H), (side * 0.17 * W, 0.68 * H),
                               (side * 0.24 * W, 0.72 * H)], 0.02 * W, out=0.03 * W, k=8)
    s.look("Mouth", "#3A0A10")
    curve(s, "Mouth", B, [(-0.1 * W, 0.41 * H), (-0.02 * W, 0.36 * H), (0.08 * W, 0.38 * H),
                          (0.14 * W, 0.45 * H)], 0.021 * W)
    s.look("Fang", WHITE)
    s.add("Fang", B.on_face(cone(0.022 * W, 0.06 * W, 10), 0.05 * W, 0.385 * H, 0.012 * W, rot(Z, 180)))
    s.look("Horns", "#2A0B10")
    for side in (-1, 1):
        th = FRONT + side * 38 * DEG
        p, n = B.pt(th, 0.86 * H)
        inward = normalize([-p[0], 0, -p[2] * 0.3])
        path = bezier([p - n * 0.05 * W, p + n * 0.06 * W + Y * 0.12 * W,
                       p + n * 0.07 * W + Y * 0.26 * W + inward * 0.06 * W], 12)
        rad = 0.08 * W * (1 - np.linspace(0, 1, 12)) ** 0.85
        s.add("Horns", tube(path, rad, 10))
    s.look("Tail", "#C21F33")
    s.look("TailTip", "#2A0B10")
    pts = []
    for t in np.linspace(0, 1, 16):
        th = (90 - 80 * t) * DEG
        yy = 0.12 * H + 0.5 * H * t ** 1.5
        rr = float(B.R(yy)) - 0.03 * W + (0.22 * W) * math.sin(t * math.pi / 2) ** 0.8
        pts.append([rr * math.cos(th), yy, rr * math.sin(th)])
    pts = np.array(pts)
    s.add("Tail", tube(pts, np.linspace(0.04 * W, 0.026 * W, len(pts)), 8))
    tang = normalize(pts[-1] - pts[-3])
    radial = normalize([pts[-1][0], 0, pts[-1][2]])
    spade = [(0, 1), (0.45, 0.35), (0.35, 0.08), (0.12, 0.2), (0.1, -0.12), (-0.1, -0.12),
             (-0.12, 0.2), (-0.35, 0.08), (-0.45, 0.35)]
    sp = extrude([(x * 0.16 * W, y * 0.16 * W) for x, y in spade], 0.025 * W)
    s.add("TailTip", tf(sp, R=surface_frame(tang, radial), t=pts[-1]))
    return s


def angel():
    s = Slime("Angel", "Legendary", 4.5)
    W = 4.5
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#FFF0D4")
    s.add("Body", B.piece())
    s.look("Eyes", "#3A2A1A")
    for side in (-1, 1):
        fx = side * 0.16 * W
        curve(s, "Eyes", B, [(fx - 0.065 * W, 0.53 * H), (fx, 0.53 * H + 0.09 * W),
                             (fx + 0.065 * W, 0.53 * H)], 0.02 * W, k=12)
    cheeks(s, B, 0.45 * H, 0.27 * W, W, "#FFB3B3")
    smile(s, B, 0.4 * H, 0.07 * W, 0.04 * W, 0.019 * W, color="#3A2A1A")
    s.look("Halo", "#FFD84A", "Neon")
    s.add("Halo", tf(torus(0.2 * W, 0.026 * W, 40, 10), R=rot(X, -12), t=(0, H + 0.13 * W, 0.02 * W)))
    s.look("Wings", WHITE)
    top = bezier([(0.0, 0.25), (0.2, 0.85), (0.7, 0.95), (1.0, 0.7)], 10)
    under = []
    for t in np.linspace(0, 1, 26)[1:-1]:
        base = np.array([1.0 - 0.95 * t, 0.7 - 0.62 * t])
        under.append(base - np.array([0.25, 1.0]) * 0.11 * abs(math.sin(4 * math.pi * t)) ** 0.6)
    poly = [tuple(p) for p in top] + [tuple(p) for p in under]
    for side in (-1, 1):
        th = BACK - side * 50 * DEG
        p, n = B.pt(th, 0.55 * H)
        R = wing_frame(BACK - side * 75 * DEG, 15)
        s.add("Wings", tf(wing(poly, 0.45 * W, 0.4 * W, 0.04 * W), R=R, t=p - n * 0.07 * W))
    return s


def galaxy():
    s = Slime("Galaxy", "Legendary", 4.5)
    W = 4.5
    B = Body(W, 0.8 * W)
    H = B.H
    rng = random.Random(16)
    s.look("Body", "#2C1A5C", transparency=0.22)
    s.add("Body", B.piece())
    s.look("Nebula", "#9B3FE0", transparency=0.55)
    s.add("Nebula", tf(Body(0.62 * W, 0.58 * H).piece(24, 10), t=(0, 0.12 * H, 0)))
    s.look("Stars", "#FFF4B8", "Neon")
    s.look("Sparkles", "#9FE8FF", "Neon")
    for i in range(16):
        yy = rng.uniform(0.15, 0.85) * H
        th = rng.uniform(0, 2 * math.pi)
        rr = float(B.R(yy)) * rng.uniform(0.35, 0.82)
        size = rng.uniform(0.045, 0.08) * W
        R = rot(normalize([rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1)]), rng.uniform(0, 360))
        st = extrude(star_polygon(5, size, size * 0.45), 0.02 * W)
        s.add("Stars", tf(st, R=R, t=(rr * math.cos(th), yy, rr * math.sin(th))))
    for i in range(14):
        yy = rng.uniform(0.1, 0.9) * H
        th = rng.uniform(0, 2 * math.pi)
        rr = float(B.R(yy)) * rng.uniform(0.2, 0.88)
        s.add("Sparkles", tf(sphere(4, 2), s=rng.uniform(0.012, 0.022) * W,
                             t=(rr * math.cos(th), yy, rr * math.sin(th))))
    eyes(s, B, 0.56 * H, 0.16 * W, 0.064 * W, 0.088 * W, color="#FFFFFF", shine=False, material="Neon")
    smile(s, B, 0.4 * H, 0.07 * W, 0.04 * W, 0.019 * W, color="#FFFFFF")
    return s


def dragon():
    s = Slime("Dragon", "Mythic", 5.2)
    W = 5.2
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#3FA34D")
    s.add("Body", B.piece())
    s.look("Belly", "#F3DC8A")
    loops = []
    y0, y1 = 0.04 * H, 0.36 * H
    for u in np.linspace(-0.97, 0.97, 12):
        th = FRONT + u * 36 * DEG
        half = (y1 - y0) / 2 * math.sqrt(1 - u * u)
        mid = (y0 + y1) / 2
        ys = np.linspace(mid + half, mid - half, 6)
        outer = [B.pt(th, v, 0.014 * W)[0] for v in ys]
        inner = [B.pt(th, v, -0.01 * W)[0] for v in ys]
        loops.append(np.array(outer + inner[::-1]))
    V, F = ring_mesh(loops, cap="strip")
    s.add("Belly", closed(V, F))
    eyes(s, B, 0.63 * H, 0.17 * W, 0.064 * W, 0.088 * W)
    s.look("Nostrils", "#1F5C29")
    for side in (-1, 1):
        s.add("Nostrils", B.on_face(ellipsoid(0.014 * W, 0.01 * W, 0.008 * W, 6, 4), side * 0.04 * W, 0.52 * H))
    smile(s, B, 0.47 * H, 0.1 * W, 0.04 * W, 0.019 * W, color="#1F3F22")
    s.look("Fangs", WHITE)
    for side in (-1, 1):
        s.add("Fangs", B.on_face(cone(0.018 * W, 0.05 * W, 10), side * 0.065 * W, 0.445 * H, 0.01 * W,
                                 rot(Z, 180)))
    s.look("Horns", "#E8B530")
    for side in (-1, 1):
        th = FRONT + side * 48 * DEG
        p, n = B.pt(th, 0.87 * H)
        side_dir = normalize([p[0], 0, 0])
        path = bezier([p - n * 0.05 * W, p + n * 0.05 * W + Y * 0.1 * W,
                       p + Y * 0.2 * W + Z * 0.16 * W + side_dir * 0.04 * W], 10)
        rad = 0.07 * W * (1 - np.linspace(0, 1, 10)) ** 0.8
        s.add("Horns", tube(path, rad, 8))
    s.look("Spikes", "#E8B530")
    for i, yy in enumerate((0.93, 0.8, 0.65, 0.5, 0.35, 0.2)):
        p, n = B.pt(BACK, yy * H)
        d = normalize(n + Y * 0.25)
        k = 1 - i * 0.1
        s.add("Spikes", tf(cone(0.055 * W * k, 0.14 * W * k, 8), R=rot_from_to(Y, d), t=p - n * 0.02 * W))
    s.look("Wings", "#2F8A3E")
    s.look("WingBones", "#E8B530")
    poly = [(0, 0.15), (0.3, 0.6), (0.65, 0.78), (1.0, 0.8), (0.86, 0.62), (0.82, 0.44), (0.9, 0.3),
            (0.74, 0.25), (0.64, 0.15), (0.6, 0.02), (0.42, 0.08), (0.22, 0.0), (0.06, -0.04)]
    span, height = 0.5 * W, 0.45 * W
    bones = [[(0.03, 0.08), (0.3, 0.6), (0.65, 0.78)], [(0.65, 0.78), (1.0, 0.8)],
             [(0.65, 0.78), (0.9, 0.3)], [(0.65, 0.78), (0.6, 0.02)]]
    for side in (-1, 1):
        th = BACK - side * 50 * DEG
        p, n = B.pt(th, 0.58 * H)
        R = wing_frame(BACK - side * 70 * DEG, 22)
        at = p - n * 0.08 * W
        s.add("Wings", tf(wing(poly, span, height, 0.02 * W), R=R, t=at))
        for b in bones:
            pts = [(x * span, y * height, 0) for x, y in b]
            path = bezier(pts, 5) if len(pts) > 2 else np.linspace(pts[0], pts[1], 2)
            s.add("WingBones", tf(tube(path, 0.016 * W, 5), R=R, t=at))
    return s


def void():
    s = Slime("Void", "Mythic", 5.2)
    W = 5.2
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#050508")
    s.add("Body", B.piece())
    eyes(s, B, 0.56 * H, 0.16 * W, 0.08 * W, 0.05 * W, color="#B026FF", shine=False, tilt=16,
         material="Neon", depth=0.5)
    s.look("Orbs", "#7A1FD1", "Neon")
    for i, (th, yy, r) in enumerate(((20, 0.3, 0.06), (75, 0.8, 0.045), (140, 0.45, 0.065),
                                     (200, 0.9, 0.045), (235, 0.2, 0.05), (315, 0.75, 0.055))):
        a = th * DEG
        rr = 0.66 * W
        s.add("Orbs", tf(sphere(12, 8), s=r * W, t=(rr * math.cos(a), yy * H, rr * math.sin(a))))
    return s


def king():
    s = Slime("King", "Secret", 6.5)
    W = 6.5
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#F7C531")
    s.add("Body", B.piece())
    eyes(s, B, 0.52 * H, 0.16 * W, 0.064 * W, 0.088 * W)
    smile(s, B, 0.37 * H, 0.09 * W, 0.045 * W, 0.019 * W)
    cheeks(s, B, 0.41 * H, 0.27 * W, W, "#FF9A5A")
    s.look("Crown", "#E0A40E", "Metal")
    s.look("GemsRed", "#E0115F")
    s.look("GemsBlue", "#2D6CDF")
    s.look("GemsGreen", "#1FAF5A")
    s.look("Cushion", "#B01030")
    rc = 0.3 * W
    tmp = Slime("tmp", "", 0)
    crown(tmp, "Crown", ["GemsRed", "GemsBlue", "GemsGreen", "GemsBlue"], rc, 0.13 * W, 8, 0.17 * W,
          0.095 * W, 0.024 * W)
    tmp.add("Cushion", tf(ellipsoid(rc * 0.94, 0.16 * W, rc * 0.94, 16, 6), t=(0, 0.06 * W, 0)))
    at = np.array([0, B.y_at_radius(rc) - 0.04 * W, 0])
    M = rot(X, 4)
    for part, pieces in tmp.parts.items():
        for p in pieces:
            s.add(part, tf(p, R=M, t=at))
    return s


def glitch():
    s = Slime("Glitch", "Secret", 6.5, face="Face")
    W = 6.5
    B = Body(W, 0.8 * W)
    H = B.H
    v = W / 10
    nx, ny = 10, int(round(H / v))
    shift = {3: 1, 6: -1}
    cells = set()
    for j in range(ny):
        yy = (j + 0.5) * v
        for i in range(nx):
            for k in range(nx):
                x, z = (i - 4.5) * v, (k - 4.5) * v
                if x * x + z * z <= float(B.R(yy)) ** 2 * 0.9:
                    cells.add((i + shift.get(j, 0), j, k))
    # the face goes on the front-most cell of each column (front is -Z, small k)
    face_px = {(3, 5), (3, 4), (6, 5), (6, 4), (2, 2), (3, 1), (4, 1), (5, 1), (6, 1), (7, 2)}
    group = {}
    rng = random.Random(20)
    for c in sorted(cells):
        group[c] = "Chunks" if rng.random() < 0.2 else "Body"
    for (i, j) in face_px:
        col = [c for c in cells if c[0] == 9 - i and c[1] == j]
        if col:
            group[min(col, key=lambda c: c[2])] = "Face"
    s.look("Body", "#39FF14")
    s.look("Chunks", "#FF1FE0", "Neon")
    s.look("Face", "#0B0B0B")
    quads = {"Body": [], "Chunks": [], "Face": []}
    dirs = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
    for c in cells:
        ctr = np.array([(c[0] - 4.5) * v, (c[1] + 0.5) * v, (c[2] - 4.5) * v])
        for d in dirs:
            if (c[0] + d[0], c[1] + d[1], c[2] + d[2]) in cells:
                continue
            dn = np.array(d, float)
            a = np.roll(dn, 1)
            b = np.roll(dn, 2)
            q = [ctr + v / 2 * (dn + sa * a + sb * b) for sa, sb in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
            if np.dot(np.cross(q[1] - q[0], q[2] - q[0]), dn) < 0:
                q = q[::-1]
            quads[group[c]].append(q)
    for part, qs in quads.items():
        V = np.array([p for q in qs for p in q])
        F = []
        for n in range(len(qs)):
            F += [(4 * n, 4 * n + 1, 4 * n + 2), (4 * n, 4 * n + 2, 4 * n + 3)]
        s.add(part, Piece(V, F, flat=True))
    s.look("Bits", "#FF1FE0", "Neon")
    s.look("BitsGreen", "#39FF14", "Neon")
    for i, (x, y, z, sz) in enumerate(((0.68, 0.75, -0.1, 0.5), (0.8, 0.45, 0.2, 0.35), (-0.72, 0.62, 0.0, 0.45),
                                       (-0.86, 0.3, -0.2, 0.3), (0.3, 1.12, 0.1, 0.4), (-0.25, 1.2, -0.05, 0.3),
                                       (0.58, 0.18, -0.35, 0.3))):
        part = "Bits" if i % 2 == 0 else "BitsGreen"
        s.add(part, tf(box(1, 1, 1), s=sz * v, t=(x * W * 0.62, y * H, z * W * 0.5)))
    return s

# ---------------------------------------------------------------- new zones: Sugar Rush, Thunder Peaks, Star Core

def bolt_polygon(size):
    P = [(0.1, 1.0), (0.62, 1.0), (0.4, 0.58), (0.72, 0.58), (0.12, -0.25), (0.3, 0.36), (0.0, 0.36)]
    return [((x - 0.36) * size, (y - 0.4) * size) for x, y in P]


def flame(h, r, n=8):
    return lathe([[(0, h), (r * 0.35, h * 0.62), (r, h * 0.28), (r * 0.85, h * 0.08), (0, 0)]], n)


def gummy():
    s = Slime("Gummy", "Legendary", 4.5)
    W = 4.5
    B = Body(W, 0.8 * W)
    H = B.H
    rng = random.Random(41)
    s.look("Body", "#FF3B5C", transparency=0.25)
    s.add("Body", B.piece())
    s.look("Core", "#FF7A90")
    s.add("Core", Body(0.78 * W, 0.74 * H).piece(24, 10))
    s.look("Ears", "#FF3B5C", transparency=0.25)
    for side in (-1, 1):
        p, n = B.pt(FRONT + side * 62 * DEG, 0.9 * H)
        s.add("Ears", tf(ellipsoid(0.09 * W, 0.09 * W, 0.06 * W, 12, 8), t=p + n * 0.02 * W))
    eyes(s, B, 0.56 * H, 0.16 * W, 0.066 * W, 0.09 * W)
    smile(s, B, 0.4 * H, 0.08 * W, 0.045 * W, 0.02 * W)
    cheeks(s, B, 0.44 * H, 0.27 * W, W, "#FFB3C1")
    s.look("Sugar", "#FFFFFF")
    for i in range(26):
        th = rng.uniform(0, 2 * math.pi)
        yy = rng.uniform(0.72, 0.98) * H
        if abs(((th / DEG) % 360) - 270) < 30 and yy < 0.82 * H:
            continue
        p, n = B.pt(th, yy, 0.01 * W)
        R = rot(normalize([rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1)]), rng.uniform(0, 90))
        s.add("Sugar", tf(box(0.035 * W, 0.035 * W, 0.035 * W), R=R, t=p))
    return s


def cotton_candy():
    s = Slime("CottonCandy", "Mythic", 5.2)
    W = 5.2
    B = Body(W, 0.8 * W)
    H = B.H
    rng = random.Random(52)
    s.look("Body", "#FFC2E2")
    s.add("Body", B.piece())
    eyes(s, B, 0.5 * H, 0.16 * W, 0.064 * W, 0.088 * W)
    smile(s, B, 0.35 * H, 0.08 * W, 0.045 * W, 0.019 * W)
    cheeks(s, B, 0.39 * H, 0.27 * W, W, "#FF8CC6")
    s.look("Fluff", "#FFB0DA")
    s.look("FluffBlue", "#A8DDFF")
    s.look("FluffWhite", "#FFF2FA")
    parts = ["Fluff", "FluffBlue", "FluffWhite"]
    puffs = 0
    for k in range(20):
        th = k * 137.5 * DEG
        yy = (0.66 + 0.34 * math.sqrt((k + 0.5) / 20)) * H
        yy = min(yy, H * 0.99)
        if abs(((th / DEG) % 360) - 270) < 34 and yy < 0.76 * H:
            continue
        p, n = B.pt(th, yy, 0.02 * W)
        r = rng.uniform(0.11, 0.16) * W
        s.add(parts[puffs % 3], tf(sphere(7, 4), s=(r, r * 0.85, r), t=p))
        puffs += 1
    s.add("Fluff", tf(sphere(10, 6), s=(0.17 * W, 0.15 * W, 0.17 * W), t=(0, H + 0.06 * W, 0.02 * W)))
    s.look("Stick", "#F7E7C6")
    s.add("Stick", tube([[0.06 * W, H + 0.1 * W, 0.05 * W], [0.14 * W, H + 0.34 * W, 0.12 * W]], 0.022 * W, 8))
    s.look("Sprinkles", "#7CE3FF")
    s.look("SprinklesYellow", "#FFE066")
    for i in range(10):
        th = rng.uniform(0, 2 * math.pi)
        yy = rng.uniform(0.25, 0.6) * H
        if abs(((th / DEG) % 360) - 270) < 40:
            continue
        p, n = B.pt(th, yy, 0.004 * W)
        d = normalize(np.cross(n, [rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1)]))
        s.add("Sprinkles" if i % 2 else "SprinklesYellow",
              tube([p - d * 0.03 * W, p + d * 0.03 * W], 0.012 * W, 4))
    return s


def thunder():
    s = Slime("Thunder", "Secret", 6.5)
    W = 6.5
    B = Body(W, 0.8 * W)
    H = B.H
    rng = random.Random(65)
    s.look("Body", "#5B6B8C")
    s.add("Body", B.piece())
    eyes(s, B, 0.53 * H, 0.16 * W, 0.07 * W, 0.075 * W, color="#FFE14D", shine=False, tilt=12, material="Neon")
    smile(s, B, 0.37 * H, 0.08 * W, 0.035 * W, 0.019 * W, color="#2A3350")
    s.look("Cloud", "#D5DDEA")
    s.look("CloudDark", "#9AA6BC")
    for k, (th, yy, r) in enumerate(((0, 0.97, 0.2), (40, 0.88, 0.16), (100, 0.9, 0.17), (160, 0.88, 0.16),
                                     (220, 0.9, 0.15), (300, 0.9, 0.16), (70, 0.78, 0.13), (130, 0.76, 0.13),
                                     (20, 0.76, 0.12), (200, 0.77, 0.12))):
        p, n = B.pt(th * DEG, yy * H, 0.03 * W)
        if k == 0:
            p = np.array([0, H + 0.02 * W, 0.02 * W])
        s.add("Cloud" if k % 3 else "CloudDark", tf(sphere(9, 6), s=(r * W, r * W * 0.8, r * W), t=p))
    s.look("Bolts", "#FFE14D", "Neon")
    for side, lean in ((-1, -22), (1, 22), (0, 0)):
        size = 0.28 * W if side else 0.22 * W
        at = np.array([side * 0.24 * W, H + (0.08 if side else 0.2) * W, 0.04 * W])
        s.add("Bolts", tf(extrude(bolt_polygon(size), 0.035 * W), R=rot(Z, lean), t=at))
    s.look("Sparks", "#9FE8FF", "Neon")
    for i in range(6):
        a = i * 60 + 20
        s.add("Sparks", tf(bipyramid_small(0.035 * W), R=rot(Z, rng.uniform(0, 90)),
                           t=(0.62 * W * math.cos(a * DEG), rng.uniform(0.3, 0.9) * H, 0.62 * W * math.sin(a * DEG))))
    return s


def bipyramid_small(r):
    return lathe([[(0, r * 1.6), (r, 0)], [(r, 0), (0, -r * 1.6)]], 4, flat=True)


def phoenix():
    s = Slime("Phoenix", "Secret", 6.5)
    W = 6.5
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#FF6A2B")
    s.add("Body", B.piece())
    s.look("Belly", "#FFC857")
    s.add("Belly", B.on_face(ellipsoid(0.2 * W, 0.13 * W, 0.03 * W, 16, 8), 0, 0.17 * H))
    eyes(s, B, 0.56 * H, 0.16 * W, 0.064 * W, 0.088 * W, color="#3A1200")
    s.look("Beak", "#FFD23F")
    s.add("Beak", B.on_face(tf(cone(0.045 * W, 0.08 * W, 10), R=rot(X, 90)), 0, 0.44 * H, 0.0))
    s.look("Flames", "#FF8A00", "Neon")
    s.look("FlamesInner", "#FFE14D", "Neon")
    for k, (dx, dz, h, lean) in enumerate(((0, 0.02, 0.36, 0), (-0.09, 0.05, 0.26, -24), (0.09, 0.05, 0.26, 24),
                                          (-0.05, 0.12, 0.2, -12), (0.05, 0.12, 0.2, 12))):
        base = np.array([dx * W, H - 0.05 * W, dz * W])
        R = rot(Z, -lean) @ rot(X, 14)
        s.add("Flames", tf(flame(h * W, 0.07 * W), R=R, t=base))
        s.add("FlamesInner", tf(flame(h * W * 0.6, 0.04 * W), R=R, t=base + R @ Y * 0.02 * W - Z * 0.005 * W))
    s.look("Wings", "#FF4A1C")
    s.look("WingTips", "#FFD23F")
    feathers = [(0, 0.15), (0.2, 0.62), (0.55, 0.85), (1.0, 0.9), (0.9, 0.62), (0.98, 0.5), (0.78, 0.4), (0.86, 0.26),
                (0.62, 0.22), (0.66, 0.06), (0.42, 0.1), (0.36, -0.06), (0.16, 0.02)]
    for side in (-1, 1):
        p, n = B.pt(BACK - side * 55 * DEG, 0.55 * H)
        R = wing_frame(BACK - side * 72 * DEG, 20)
        s.add("Wings", tf(wing(feathers, 0.42 * W, 0.4 * W, 0.035 * W), R=R, t=p - n * 0.06 * W))
        tip = [(x * 0.42 * W, y * 0.4 * W) for x, y in [(0.62, 0.8), (1.0, 0.9), (0.9, 0.62), (0.98, 0.5), (0.7, 0.62)]]
        s.add("WingTips", tf(extrude(tip, 0.045 * W), R=R, t=p - n * 0.06 * W))
    s.look("Tail", "#FF8A00", "Neon")
    for k, a in enumerate((-30, 0, 30)):
        d = rot(Y, a) @ np.array([0, 0.3, 1.0])
        base, nb = B.pt(BACK, 0.22 * H, -0.02 * W)
        path = bezier([base, base + d * 0.15 * W, base + d * 0.3 * W + Y * 0.12 * W], 8)
        s.add("Tail", tube(path, np.linspace(0.05 * W, 0.0, 8), 7))
    return s


def supernova():
    s = Slime("Supernova", "Divine", 7)
    W = 7.0
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#FFE9A8")
    s.add("Body", B.piece())
    eyes(s, B, 0.55 * H, 0.16 * W, 0.064 * W, 0.088 * W, color="#4A2A00")
    smile(s, B, 0.39 * H, 0.08 * W, 0.045 * W, 0.019 * W, color="#4A2A00")
    cheeks(s, B, 0.43 * H, 0.27 * W, W, "#FF9E6B")
    s.look("Corona", "#FFB020", "Neon")
    s.add("Corona", tf(torus(0.58 * W, 0.025 * W, 36, 6), R=rot(X, 14), t=(0, 0.46 * H, 0)))
    s.look("RingPink", "#FF5CC8", "Neon")
    s.add("RingPink", tf(torus(0.66 * W, 0.016 * W, 36, 5), R=rot(Z, 28) @ rot(X, -10), t=(0, 0.5 * H, 0)))
    s.look("Rays", "#FFF4C2", "Neon")
    c = np.array([0, 0.62 * H, 0.3 * W])
    for k, a in enumerate(range(-15, 200, 30)):
        d = np.array([math.cos(a * DEG), math.sin(a * DEG), 0])
        ln = (0.26 if k % 2 else 0.17) * W
        s.add("Rays", tf(cone(0.045 * W, ln, 6), R=rot_from_to(Y, d), t=c + d * 0.36 * W))
    s.look("Stars", "#FFFFFF", "Neon")
    for k, (a, yy) in enumerate(((30, 1.05), (150, 1.1), (250, 0.85), (330, 0.95))):
        st = extrude(star_polygon(4, 0.06 * W, 0.02 * W), 0.01 * W)
        s.add("Stars", tf(st, t=(0.5 * W * math.cos(a * DEG), yy * H, 0.5 * W * math.sin(a * DEG) - 0.05 * W)))
    return s


def black_hole():
    s = Slime("BlackHole", "Divine", 7)
    W = 7.0
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#050507")
    s.add("Body", B.piece())
    eyes(s, B, 0.58 * H, 0.16 * W, 0.075 * W, 0.06 * W, color="#E9D2FF", shine=False, tilt=10, material="Neon")
    s.look("Mouth", "#B026FF", "Neon")
    smile(s, B, 0.42 * H, 0.06 * W, 0.03 * W, 0.016 * W, color="#B026FF")
    s.looks["Mouth"]["material"] = "Neon"
    M = rot(X, 12) @ rot(Z, -8)
    centre = np.array([0, 0.42 * H, 0])
    disk_in = lathe([[(0.52 * W, 0.012 * W), (0.66 * W, 0.0)], [(0.66 * W, 0.0), (0.52 * W, -0.012 * W)],
                     [(0.52 * W, -0.012 * W), (0.52 * W, 0.012 * W)]], 48)
    disk_out = lathe([[(0.66 * W, 0.008 * W), (0.86 * W, 0.0)], [(0.86 * W, 0.0), (0.66 * W, -0.008 * W)],
                      [(0.66 * W, -0.008 * W), (0.66 * W, 0.008 * W)]], 48)
    s.look("DiskInner", "#FF8A3D", "Neon")
    s.look("DiskOuter", "#9B30FF", "Neon", transparency=0.25)
    s.add("DiskInner", tf(disk_in, R=M, t=centre))
    s.add("DiskOuter", tf(disk_out, R=M, t=centre))
    s.look("PhotonRing", "#FFE7C2", "Neon")
    s.add("PhotonRing", tf(torus(0.42 * W, 0.014 * W, 40, 6), R=rot(X, 90), t=(0, 0.6 * H, 0.1 * W)))
    s.look("Debris", "#C77DFF", "Neon")
    for k in range(7):
        a = k * 51.4 * DEG
        p = centre + M @ np.array([0.76 * W * math.cos(a), 0.03 * W, 0.76 * W * math.sin(a)])
        s.add("Debris", tf(sphere(6, 4), s=0.025 * W, t=p))
    return s


def omega_symbol(size):
    """Path for the Greek capital omega, in the XY plane."""
    pts = [(-0.55, 0.0), (-0.28, 0.0)] + [(0.45 * math.cos(math.radians(a)), 0.48 + 0.45 * math.sin(math.radians(a)))
                                          for a in np.linspace(235, -55, 20)] + [(0.28, 0.0), (0.55, 0.0)]
    return [(x * size, y * size, 0) for x, y in pts]


def omega():
    s = Slime("Omega", "Divine", 7)
    W = 7.0
    B = Body(W, 0.8 * W)
    H = B.H
    s.look("Body", "#F4F0FF")
    s.add("Body", B.piece())
    eyes(s, B, 0.55 * H, 0.16 * W, 0.066 * W, 0.09 * W, color="#2A1F4A")
    smile(s, B, 0.39 * H, 0.08 * W, 0.04 * W, 0.019 * W, color="#2A1F4A")
    cheeks(s, B, 0.43 * H, 0.27 * W, W, "#C9B8FF")
    s.look("Emblem", "#FFD54A", "Neon")
    path = np.array(omega_symbol(0.26 * W)) + np.array([0, H + 0.14 * W, 0])
    s.add("Emblem", tube(path, 0.025 * W, 6))
    s.look("Halo", "#FFD54A", "Neon")
    s.add("Halo", tf(torus(0.24 * W, 0.016 * W, 32, 5), R=rot(X, 90), t=(0, H + 0.26 * W, 0.12 * W)))
    s.look("WingsCyan", "#8FF3FF", "Glass", 0.2)
    s.look("WingsPink", "#FF9EEA", "Glass", 0.2)
    wing_poly = [(0, 0.1), (0.25, 0.55), (0.6, 0.8), (1.0, 0.85), (0.8, 0.55), (0.92, 0.35), (0.6, 0.3), (0.62, 0.1),
                 (0.3, 0.05)]
    for side in (-1, 1):
        for k, (yy, up, span, part) in enumerate(((0.62, 28, 0.5, "WingsCyan"), (0.4, -6, 0.38, "WingsPink"))):
            p, n = B.pt(BACK - side * 52 * DEG, yy * H)
            R = wing_frame(BACK - side * 74 * DEG, up)
            s.add(part, tf(wing(wing_poly, span * W, span * 0.8 * W, 0.025 * W), R=R, t=p - n * 0.05 * W))
    s.look("OrbitRing", "#B9A7FF", "Neon")
    s.add("OrbitRing", tf(torus(0.62 * W, 0.012 * W, 36, 5), R=rot(X, -8) @ rot(Z, 10), t=(0, 0.3 * H, 0)))
    return s



# ---------------------------------------------------------------- the Overlord

def cape(s, part, B, W, H, y0f=0.04, y1f=0.8, flare=0.09, spread=85, nu=14, nv=7):
    """A cape over the back of a body, flaring toward the ground."""
    y0, y1 = y0f * H, y1f * H
    loops = []
    for th in np.linspace((90 - spread) * DEG, (90 + spread) * DEG, nu):
        outer, inner = [], []
        for yy in np.linspace(y1, y0, nv):
            f = ((y1 - yy) / (y1 - y0)) ** 1.6
            o = 0.03 * W + flare * W * f
            outer.append(B.pt(th, yy, o)[0])
            inner.append(B.pt(th, yy, o - 0.025 * W)[0])
        loop = np.array(outer + inner[::-1])
        loop[:, 1] = np.maximum(loop[:, 1], 0.0)
        loops.append(loop)
    V, F = ring_mesh(loops, cap="strip")
    s.add(part, closed(V, F))
    return y1


def overlord():
    s = Slime("Overlord", "Secret", 6.5)
    W = 6.5
    B = Body(W, 0.8 * W)
    H = B.H
    body = "#4CCB6E"
    s.look("Body", body)
    s.add("Body", B.piece(28, 12))
    # smug half-lidded eyes, raised brow, smirk with one fang
    eyes(s, B, 0.54 * H, 0.16 * W, 0.066 * W, 0.08 * W)
    s.look("Brows", "#1F5E33")
    curve(s, "Brows", B, [(-0.24 * W, 0.66 * H), (-0.16 * W, 0.69 * H), (-0.08 * W, 0.67 * H)], 0.018 * W, out=0.03 * W, k=6)
    curve(s, "Brows", B, [(0.08 * W, 0.65 * H), (0.16 * W, 0.66 * H), (0.24 * W, 0.7 * H)], 0.018 * W, out=0.03 * W, k=6)
    s.look("Mouth", "#173F24")
    curve(s, "Mouth", B, [(-0.08 * W, 0.4 * H), (0.0, 0.37 * H), (0.08 * W, 0.38 * H), (0.12 * W, 0.43 * H)], 0.019 * W, k=10)
    s.look("Fang", WHITE)
    s.add("Fang", B.on_face(cone(0.02 * W, 0.05 * W, 8), 0.06 * W, 0.37 * H, 0.01 * W, rot(Z, 180)))
    # tall spiked crown
    s.look("Crown", "#F2C230", "Metal")
    s.look("Gems", "#9B30FF", "Neon")
    tmp = Slime("tmp", "", 0)
    rc = 0.2 * W
    crown(tmp, "Crown", ["Gems"], rc, 0.08 * W, 5, 0.16 * W, 0.045 * W, 0.02 * W)
    at = np.array([0, B.y_at_radius(rc) - 0.03 * W, 0])
    M = rot(Z, -8)
    for part, pieces in tmp.parts.items():
        for p in pieces:
            s.add(part, tf(p, R=M, t=at))
    s.look("Cape", "#5B1E8C")
    y1 = cape(s, "Cape", B, W, H, y1f=0.74, flare=0.08)
    s.look("Collar", "#2E0F4A")
    path = [B.pt(th, y1, 0.045 * W)[0] + Y * 0.03 * W * math.sin(th) for th in np.linspace(0, 180 * DEG, 11)]
    s.add("Collar", tube(path, 0.045 * W, 6))
    s.look("Clasp", "#F2C230", "Metal")
    for th in (8 * DEG, 172 * DEG):
        s.add("Clasp", tf(sphere(8, 4), s=0.04 * W, t=B.pt(th, y1, 0.07 * W)[0]))
    return s



SLIMES = [lil_green, blue_blob, pinky, mudsy, bubblegum, lemon_drop, minty, top_hat, ninja, lava, frost,
          royal, ghost, devil, angel, galaxy, dragon, void, king, glitch, gummy, cotton_candy,
          thunder, phoenix, supernova, black_hole, omega, overlord]


# ---------------------------------------------------------------- outputs

def lua_color(hexstr):
    h = hexstr.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return f"Color3.fromRGB({r}, {g}, {b})"


def write_lua(path, specs):
    lines = []
    for sp in specs:
        lines.append(f'\t{sp["name"]} = {{ Rarity = "{sp["rarity"]}", Size = {sp["size"]}, '
                     f'BodyWidth = {sp["bodyWidth"]:.4f}, Face = "{sp["face"]}", Parts = {{')
        for part, lk in sp["looks"].items():
            lines.append(f'\t\t{part} = {{ {lua_color(lk["color"])}, Enum.Material.{lk["material"]}, '
                         f'{lk["transparency"]:g} }},')
        lines.append("\t} },")
    with open(os.path.join(os.path.dirname(__file__), "SlimeSetup.template.lua")) as f:
        template = f.read()
    with open(path, "w") as f:
        f.write(template.replace("--@SPECS@", "\n".join(lines)))


def export(builders, out_models, json_name, lua_name, max_tris):
    """Write each model's .glb, a json summary and the Roblox setup script."""
    out_lua = os.path.join(ROOT, "roblox")
    os.makedirs(out_models, exist_ok=True)
    os.makedirs(out_lua, exist_ok=True)
    specs, problems = [], []
    for build in builders:
        s = build()
        assert set(s.parts) == set(s.looks), (s.name, set(s.parts) ^ set(s.looks))
        allV = np.vstack([p.V for ps in s.parts.values() for p in ps])
        body = np.vstack([p.V for p in s.parts["Body"]])
        tris = write_glb(os.path.join(out_models, s.name + ".glb"), s.name, s.parts, s.looks)
        lo, hi = allV.min(0), allV.max(0)
        assert lo[1] > -1e-6, (s.name, "below ground", lo[1])
        if tris >= max_tris:
            problems.append(f"{s.name} has {tris} triangles")
        spec = {"name": s.name, "rarity": s.rarity, "size": s.size, "face": s.face, "triangles": int(tris),
                "bodyWidth": float(body[:, 0].max() - body[:, 0].min()),
                "bounds": [lo.round(3).tolist(), hi.round(3).tolist()], "looks": s.looks}
        specs.append(spec)
        print(f"{s.name:16s} {tris:5d} tris  body {spec['bodyWidth']:.2f} wide  "
              f"bounds {np.round(hi - lo, 2)}  bottom {lo[1]:.3f}")
    with open(os.path.join(out_models, json_name), "w") as f:
        json.dump(specs, f, indent=1)
    write_lua(os.path.join(out_lua, lua_name), specs)
    if problems:
        raise SystemExit("\n".join(problems))


def main():
    export(SLIMES, os.path.join(ROOT, "models"), "slimes.json", "SlimeSetup.lua", 3000)


if __name__ == "__main__":
    main()
