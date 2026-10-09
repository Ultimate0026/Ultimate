#!/usr/bin/env python3
"""Builds the zone guardian models.

    python3 slimes/tools/build_guardians.py

Writes slimes/guardians/Guardian_<Zone>.glb, slimes/guardians/guardians.json and
slimes/roblox/GuardianSetup.lua. Same conventions as the slimes: studs, facing -Z, bottom centre at
the origin. Each guardian stays under 5k triangles.
"""
import math
import os
import random

import numpy as np

from build_slimes import (BACK, DEG, FRONT, ROOT, WHITE, Slime, bolt_polygon, cape, crown, curve, drip, export,
                          leaf_polygon,
                          X, Y, Z)
from meshkit import (Body, box, cone, star_polygon, cylinder, ellipsoid, extrude, icosphere, lathe, normalize, place, prism, rot,
                     rot_from_to, sphere, tf, torus, tube, bezier)


def angry_face(s, B, W, H, y, sep, ex, ey, body_color, sclera=WHITE, pupil="#16161C", brow="#1E1E24",
               glow=False, mouth_y=None, mouth_w=None, mouth_color="#2A1A1A", mouth_glow=False,
               teeth="#FFFFFF", mouth_r=None):
    """Kid-friendly angry face: big eyes with heavy lids, slanted brows and a frown with two fangs."""
    s.look("Eyes", sclera, "Neon" if glow else "SmoothPlastic")
    if pupil:
        s.look("Pupils", pupil)
    s.look("Lids", body_color)
    s.look("Brows", brow)
    ez = 0.42 * ex
    for side in (-1, 1):
        fx = side * sep
        s.add("Eyes", B.on_face(ellipsoid(ex, ey, ez, 14, 8), fx, y))
        if pupil:
            pu = tf(ellipsoid(0.42 * ex, 0.5 * ey, 0.16 * ex, 10, 6), t=(-side * 0.2 * ex, -0.12 * ey, ez * 0.85))
            s.add("Pupils", B.on_face(pu, fx, y))
        lid = tf(ellipsoid(1.5 * ex, 0.75 * ey, 0.62 * ex, 12, 6), R=rot(Z, 20 * side),
                 t=(-side * 0.15 * ex, 0.95 * ey, 0))
        s.add("Lids", B.on_face(lid, fx, y))
        curve(s, "Brows", B, [(side * (sep - 1.25 * ex), y + 0.72 * ey), (side * sep, y + 1.15 * ey),
                              (side * (sep + 1.25 * ex), y + 1.55 * ey)], 0.026 * W, out=0.04 * W, k=10)
    my = 0.36 * H if mouth_y is None else mouth_y
    mw = 0.13 * W if mouth_w is None else mouth_w
    mr = 0.024 * W if mouth_r is None else mouth_r
    s.look("Mouth", mouth_color, "Neon" if mouth_glow else "SmoothPlastic")
    pts = [(mw * t, my + 0.05 * W * (1 - t * t)) for t in np.linspace(-1, 1, 14)]
    path = B.face_path(pts, mr * 0.4)
    rad = np.full(len(path), mr)
    rad[0] = rad[-1] = mr * 0.7
    s.add("Mouth", tube(path, rad, 8))
    s.look("Teeth", teeth)
    for side in (-1, 1):
        t = 0.5 * side
        fx, fy = mw * t, my + 0.05 * W * (1 - t * t) + mr * 0.6
        s.add("Teeth", B.on_face(cone(0.03 * W, 0.065 * W, 10), fx, fy, mr * 0.5))


def bipyramid(r, h, tip, n=6):
    return lathe([[(0, h + tip), (r, h)], [(r, h), (r, 0)], [(r, 0), (0, -tip * 0.6)]], n, flat=True)


# ---------------------------------------------------------------- guardians

def meadow():
    s = Slime("Guardian_Meadow", "Guardian", 9)
    W = 9.0
    B = Body(W, 0.78 * W)
    H = B.H
    body = "#2F6B2E"
    s.look("Body", body)
    s.add("Body", B.piece(40, 18))
    angry_face(s, B, W, H, 0.55 * H, 0.17 * W, 0.075 * W, 0.08 * W, body, brow="#173A16")
    # thorny vines wrapping round the sides and back, never across the face
    s.look("Vines", "#1E4A1D")
    s.look("Thorns", "#C9B27A")
    s.look("Leaves", "#4FA84A")
    for k, (th0, y0, y1) in enumerate(((-55, 0.12, 0.62), (-30, 0.24, 0.86), (-70, 0.1, 0.42))):
        n = 22
        ts = np.linspace(0, 1, n)
        ths = (th0 + ts * 255) * DEG
        ys = (y0 + (y1 - y0) * ts) * H
        path = np.array([B.pt(a, yy, 0.012 * W)[0] for a, yy in zip(ths, ys)])
        s.add("Vines", tube(path, 0.026 * W, 6))
        for i in range(2, n - 1, 3):
            p, nrm = B.pt(ths[i], ys[i], 0.03 * W)
            s.add("Thorns", tf(cone(0.018 * W, 0.06 * W, 5), R=rot_from_to(Y, normalize(nrm + Y * 0.3)), t=p))
        for i in (5, 13):
            p, nrm = B.pt(ths[i], ys[i], 0.03 * W)
            lf = extrude(leaf_polygon(0.12 * W, 0.04 * W, 6), 0.012 * W)
            s.add("Leaves", tf(lf, R=rot_from_to(X, normalize(nrm + Y * 0.6)) @ rot(X, 90), t=p))
    # flower on the head
    s.look("Petals", "#FF7BAC")
    s.look("FlowerCentre", "#FFD23F")
    s.look("Stem", "#3C8C3A")
    top = np.array([0.04 * W, H, 0.02 * W])
    M = rot(Z, -14) @ rot(X, -10)
    head = top + M @ np.array([0, 0.13 * W, 0])
    s.add("Stem", tube([top - Y * 0.05 * W, top + M @ np.array([0, 0.05 * W, 0]), head], 0.018 * W, 8))
    for k in range(6):
        a = k * 60
        petal = tf(ellipsoid(0.11 * W, 0.022 * W, 0.06 * W, 8, 4), t=(0.11 * W, 0, 0))
        s.add("Petals", tf(petal, R=M @ rot(Y, a) @ rot(Z, 14), t=head))
    s.add("FlowerCentre", tf(ellipsoid(0.06 * W, 0.035 * W, 0.06 * W, 12, 8), R=M, t=head + M @ Y * 0.012 * W))
    return s


def swamp():
    s = Slime("Guardian_Swamp", "Guardian", 10)
    W = 10.0
    B = Body(W, 0.76 * W, sag=0.24)
    H = B.H
    body = "#4F7363"
    s.look("Body", body)
    s.add("Body", B.piece(40, 18))
    angry_face(s, B, W, H, 0.55 * H, 0.17 * W, 0.075 * W, 0.075 * W, body, sclera="#F2EFA0",
               brow="#22362C", mouth_color="#1E2A22", teeth="#F2EFD8")
    s.look("Goo", "#8DBB5E")
    for th, yt, ln in ((205, 0.86, 0.6), (232, 0.7, 0.45), (308, 0.74, 0.5), (338, 0.88, 0.66),
                       (160, 0.82, 0.6)):
        s.add("Goo", drip(B, th * DEG, yt * H, ln * H, 0.045 * W))
    s.look("Puddle", "#3C5A4A")
    r0 = float(B.R(0.0))
    s.add("Puddle", lathe([[(0, 0.02 * W), (r0 + 0.05 * W, 0.018 * W), (r0 + 0.09 * W, 0.008 * W),
                            (r0 + 0.1 * W, 0.0)], [(r0 + 0.1 * W, 0.0), (0, 0.0)]], 32))
    # lily pad hat with a little flower
    s.look("LilyPad", "#3F8F3A")
    s.look("LilyFlower", "#FFB3D1")
    pad = [(0.24 * W * math.cos(a), 0.24 * W * math.sin(a)) for a in np.linspace(0.22, 2 * math.pi - 0.22, 26)]
    pad = [(0.0, 0.0)] + pad
    M = rot(Z, 8) @ rot(X, 90 - 6)
    at = np.array([0, H - 0.02 * W, 0])
    s.add("LilyPad", tf(extrude(pad, 0.02 * W), R=M @ rot(Z, 120), t=at))
    fl = at + np.array([0.08 * W, 0.035 * W, 0.04 * W])
    for k in range(6):
        s.add("LilyFlower", tf(ellipsoid(0.06 * W, 0.016 * W, 0.024 * W, 8, 4), R=rot(Y, k * 60) @ rot(Z, 35),
                               t=fl + rot(Y, k * 60) @ np.array([0.04 * W, 0.016 * W, 0])))
    # mushrooms growing out of the back
    s.look("MushroomStems", "#E8DCC0")
    s.look("MushroomCaps", "#C2552D")
    s.look("MushroomSpots", "#FFF4E0")
    rng = random.Random(4)
    for th, yy, size in ((60, 0.55, 1.0), (95, 0.72, 1.2), (125, 0.5, 0.9), (88, 0.36, 0.75), (40, 0.8, 0.7)):
        p, nrm = B.pt(th * DEG, yy * H)
        d = normalize(nrm + Y * 0.5)
        R = rot_from_to(Y, d)
        k = size * W
        s.add("MushroomStems", tf(cylinder(0.022 * k, 0.07 * k, 10, r_top=0.018 * k), R=R, t=p - d * 0.02 * k))
        cap_base = p + d * 0.065 * k
        cap = lathe([[(0, 0.055 * k), (0.045 * k, 0.045 * k), (0.07 * k, 0.012 * k), (0.072 * k, 0.0)],
                     [(0.072 * k, 0.0), (0, 0.0)]], 12)
        s.add("MushroomCaps", tf(cap, R=R, t=cap_base))
        for j in range(3):
            a = rng.uniform(0, 2 * math.pi)
            local = np.array([0.04 * k * math.cos(a), 0.047 * k, 0.04 * k * math.sin(a)])
            sp_n = normalize([local[0], 0.06 * k, local[2]])
            s.add("MushroomSpots", place(ellipsoid(0.012 * k, 0.012 * k, 0.004 * k, 6, 3),
                                         cap_base + R @ local, R @ sp_n))
    return s


def lava():
    s = Slime("Guardian_Lava", "Guardian", 11)
    W = 11.0
    B = Body(0.95 * W, 0.78 * W)
    H = B.H
    rng = random.Random(11)
    rock = "#2B211F"
    s.look("Core", "#FF5A00", "Neon")
    s.add("Core", Body(W * 0.9, H * 0.95).piece(40, 16))
    s.look("Body", rock, "Slate")

    def to_body(d):
        d = normalize(d)
        th = math.atan2(d[2], d[0])
        yy = min(max(H * (0.45 + 0.55 * d[1]), 0.015 * H), H * 0.999)
        return B.pt(th, yy)

    V, F = icosphere(1)
    V = V @ rot(Y, 17).T
    for i, v in enumerate(V):
        if v[1] < -0.72:
            continue
        faces = [f for f in F if i in f]
        cents = [normalize(V[list(f)].mean(0)) for f in faces]
        tang = normalize(np.cross(Y if abs(v[1]) < 0.95 else X, v))
        bit = np.cross(v, tang)
        cents.sort(key=lambda c: math.atan2(np.dot(c - v, bit), np.dot(c - v, tang)))
        cp, cn = to_body(v)
        corners = [cp + (to_body(c)[0] - cp) * 0.86 for c in cents]
        h = 0.028 * W * (0.7 + 0.6 * rng.random())
        s.add("Body", prism([q + cn * h for q in corners], [q - cn * 0.03 * W for q in corners]))
    # face sits on top of the plates
    s.look("Eyes", "#FFE14D", "Neon")
    s.look("Lids", rock, "Slate")
    for side in (-1, 1):
        fx, y = side * 0.17 * W, 0.56 * H
        s.add("Eyes", B.on_face(ellipsoid(0.08 * W, 0.065 * W, 0.035 * W, 16, 10), fx, y, 0.05 * W))
        lid = tf(box(0.24 * W, 0.08 * W, 0.08 * W), R=rot(Z, 22 * side), t=(-side * 0.02 * W, 0.07 * W, 0))
        s.add("Lids", B.on_face(lid, fx, y, 0.05 * W))
    s.look("Mouth", "#FF8C1A", "Neon")
    pts = [(-0.15 * W, 0.37 * H), (-0.09 * W, 0.42 * H), (-0.04 * W, 0.38 * H), (0.0, 0.43 * H),
           (0.04 * W, 0.38 * H), (0.09 * W, 0.42 * H), (0.15 * W, 0.37 * H)]
    s.add("Mouth", tube(B.face_path(pts, 0.055 * W), 0.026 * W, 8))
    # horns and jagged spikes
    s.look("Horns", "#1A1413", "Slate")
    for side in (-1, 1):
        th = FRONT + side * 40 * DEG
        p, n = B.pt(th, 0.84 * H)
        outward = normalize([p[0], 0, 0])
        path = bezier([p - n * 0.05 * W, p + n * 0.06 * W + outward * 0.08 * W + Y * 0.06 * W,
                       p + outward * 0.12 * W + Y * 0.24 * W, p + outward * 0.06 * W + Y * 0.34 * W], 12)
        rad = 0.075 * W * (1 - np.linspace(0, 1, 12)) ** 0.8
        s.add("Horns", tube(path, rad, 9))
    s.look("Spikes", "#1A1413", "Slate")
    s.look("SpikeTips", "#FF7A1A", "Neon")
    spikes = [(90, 0.95, 1.0), (90, 0.8, 1.1), (90, 0.62, 1.0), (90, 0.44, 0.85), (55, 0.82, 0.8),
              (125, 0.82, 0.8), (40, 0.6, 0.7), (140, 0.6, 0.7), (10, 0.72, 0.6), (170, 0.72, 0.6)]
    for th, yy, k in spikes:
        p, n = B.pt(th * DEG, yy * H)
        d = normalize(n + Y * 0.35)
        R = rot_from_to(Y, d) @ rot(Y, th * 2.3)
        h = 0.17 * W * k
        s.add("Spikes", tf(cone(0.06 * W * k, h * 0.78, 5), R=R, t=p - d * 0.03 * W))
        s.add("SpikeTips", tf(cone(0.06 * W * k * 0.25, h * 0.25, 5), R=R, t=p - d * 0.03 * W + d * h * 0.74))
    return s


def crystal():
    s = Slime("Guardian_Crystal", "Guardian", 12)
    W = 12.0
    B = Body(W, 0.78 * W)
    H = B.H
    body = "#6C7FE6"
    s.look("Body", body)
    s.add("Body", B.piece(40, 18))
    angry_face(s, B, W, H, 0.55 * H, 0.17 * W, 0.075 * W, 0.07 * W, body, sclera="#8FF7FF", pupil=None,
               brow="#262B6B", glow=True, mouth_color="#262B6B", teeth="#D9D0FF")
    s.look("Crystals", "#C3B6FF", "Glass", 0.15)
    s.look("CrystalCores", "#E7DEFF", "Neon")
    rows = [(90, 0.97, 0.3, 0.0), (90, 0.84, 0.36, 0.0), (90, 0.68, 0.32, 0.0), (90, 0.5, 0.26, 0.0),
            (90, 0.32, 0.18, 0.0), (62, 0.86, 0.22, -14), (118, 0.86, 0.22, 14), (55, 0.62, 0.18, -18),
            (125, 0.62, 0.18, 18), (300, 0.94, 0.14, 0), (240, 0.94, 0.12, 0)]
    for th, yy, h, lean in rows:
        p, n = B.pt(th * DEG, yy * H)
        d = normalize(n * 0.8 + Y * 0.6)
        R = rot_from_to(Y, d) @ rot(Y, th + lean * 3)
        hh, rw = h * W, 0.045 * W * (0.6 + h * 1.6)
        base = p - d * 0.04 * W
        s.add("Crystals", tf(bipyramid(rw, hh, rw * 1.8), R=R, t=base))
        s.add("CrystalCores", tf(bipyramid(rw * 0.45, hh * 0.85, rw * 0.8), R=R, t=base))
    return s


def void():
    s = Slime("Guardian_Void", "Guardian", 14)
    W = 14.0
    B = Body(W, 0.8 * W)
    H = B.H
    body = "#050508"
    s.look("Body", body)
    s.add("Body", B.piece(40, 18))
    s.look("Eyes", "#B026FF", "Neon")
    s.look("Lids", body)
    s.look("Brows", "#3A0D5C", "Neon")
    eyes = [(-0.17, 0.56, 0.075, 0.06), (0.17, 0.56, 0.075, 0.06), (0.0, 0.74, 0.045, 0.045),
            (-0.3, 0.72, 0.042, 0.034), (0.3, 0.72, 0.042, 0.034), (-0.32, 0.45, 0.036, 0.028),
            (0.32, 0.45, 0.036, 0.028)]
    for i, (fx, yy, ex, ey) in enumerate(eyes):
        side = 1 if fx > 0 else -1
        tilt = rot(Z, 18 * side) if fx else np.eye(3)
        s.add("Eyes", B.on_face(ellipsoid(ex * W, ey * W, 0.4 * ex * W, 12, 6), fx * W, yy * H, 0, tilt))
        if i < 2:
            lid = tf(ellipsoid(1.5 * ex * W, 0.75 * ey * W, 0.62 * ex * W, 10, 5), R=rot(Z, 22 * side),
                     t=(-side * 0.15 * ex * W, 0.95 * ey * W, 0))
            s.add("Lids", B.on_face(lid, fx * W, yy * H))
    for side in (-1, 1):
        curve(s, "Brows", B, [(side * 0.06 * W, 0.6 * H + 0.06 * W), (side * 0.17 * W, 0.6 * H + 0.08 * W),
                              (side * 0.27 * W, 0.6 * H + 0.12 * W)], 0.016 * W, out=0.03 * W, k=8)
    s.look("Mouth", "#B026FF", "Neon")
    pts = [(-0.14 * W, 0.34 * H), (-0.09 * W, 0.38 * H), (-0.045 * W, 0.35 * H), (0.0, 0.39 * H),
           (0.045 * W, 0.35 * H), (0.09 * W, 0.38 * H), (0.14 * W, 0.34 * H)]
    s.add("Mouth", tube(B.face_path(pts, 0.012 * W), 0.016 * W, 8))
    # floating shards and orbs circling the body
    s.look("Shards", "#9B30FF", "Neon")
    s.look("ShardsDark", "#2A0B45", "Glass", 0.1)
    s.look("Orbs", "#C77DFF", "Neon")
    rng = random.Random(14)
    for k in range(10):
        a = (k * 36 + rng.uniform(-8, 8)) * DEG
        if abs(((a / DEG) % 360) - 270) < 25:
            a += 40 * DEG
        r = 0.66 * W + rng.uniform(-0.04, 0.06) * W
        yy = rng.uniform(0.2, 1.05) * H
        h = rng.uniform(0.08, 0.15) * W
        R = rot(normalize([rng.uniform(-1, 1), rng.uniform(-0.3, 0.3), rng.uniform(-1, 1)]), rng.uniform(15, 40))
        part = "Shards" if k % 2 == 0 else "ShardsDark"
        s.add(part, tf(bipyramid(h * 0.22, h * 0.25, h * 0.6, 4), R=R, t=(r * math.cos(a), yy, r * math.sin(a))))
    for a, yy, rr in ((25, 0.3, 0.03), (120, 0.95, 0.025), (200, 0.6, 0.035), (335, 0.85, 0.028)):
        s.add("Orbs", tf(sphere(10, 6), s=rr * W,
                         t=(0.72 * W * math.cos(a * DEG), yy * H, 0.72 * W * math.sin(a * DEG))))
    return s


def candy():
    s = Slime("Guardian_Candy", "Guardian", 15)
    W = 15.0
    B = Body(W, 0.78 * W)
    H = B.H
    rng = random.Random(15)
    body = "#FF6FB5"
    s.look("Body", body)
    s.add("Body", B.piece(36, 16))
    angry_face(s, B, W, H, 0.5 * H, 0.17 * W, 0.075 * W, 0.075 * W, body, brow="#7A1F4F", mouth_color="#5A1235")
    # dripping icing cap with sprinkles
    s.look("Icing", "#FFF5FA")
    yc = B.y_at_radius(0.36 * W)
    ys = np.linspace(H, yc, 5)[1:]
    outer = [(0.0, H + 0.02 * W)] + [(float(B.R(v)) + 0.02 * W, v) for v in ys]
    inner = [(float(B.R(v)) - 0.02 * W, v) for v in ys[::-1]] + [(0.0, H - 0.02 * W)]
    s.add("Icing", lathe([outer, [outer[-1], inner[0]], inner], 32))
    for k in range(5):
        th = (k * 72 + 36 + rng.uniform(-8, 8)) * DEG
        if abs(((th / DEG) % 360) - 270) < 25:
            continue
        s.add("Icing", drip(B, th, yc + 0.01 * W, rng.uniform(0.08, 0.18) * H, 0.035 * W))
    colors = {"SprinklesBlue": "#5AC8FF", "SprinklesYellow": "#FFE066", "SprinklesGreen": "#7CE38B",
              "SprinklesPurple": "#B48CFF"}
    for k, v in colors.items():
        s.look(k, v)
    keys = list(colors)
    for i in range(20):
        th = rng.uniform(0, 2 * math.pi)
        yy = rng.uniform(yc + 0.02 * W, H * 0.985)
        p, n = B.pt(th, yy, 0.025 * W)
        d = normalize(np.cross(n, [rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-1, 1)]))
        s.add(keys[i % 4], tube([p - d * 0.018 * W, p + d * 0.018 * W], 0.007 * W, 4))
    # candy cane horns, striped
    s.look("CaneRed", "#E3263B")
    s.look("CaneWhite", "#FFFFFF")
    for side in (-1, 1):
        p, n = B.pt(FRONT + side * 42 * DEG, 0.88 * H)
        out = normalize([p[0], 0, 0])
        path = bezier([p - n * 0.03 * W, p + Y * 0.16 * W + out * 0.02 * W, p + Y * 0.26 * W + out * 0.08 * W,
                       p + Y * 0.24 * W + out * 0.15 * W, p + Y * 0.18 * W + out * 0.16 * W], 18)
        s.add("CaneWhite", tube(path, 0.028 * W, 6))
        for j in range(1, len(path) - 1, 4):
            s.add("CaneRed", tube([path[j], path[j + 1]], 0.03 * W, 6))
    # gumdrops along the back
    gum = {"GumdropRed": "#FF4D6D", "GumdropOrange": "#FF9F43", "GumdropGreen": "#5BD66B"}
    for k, v in gum.items():
        s.look(k, v)
    for i, (th, yy) in enumerate(((90, 0.6), (60, 0.44), (120, 0.44), (90, 0.3))):
        p, n = B.pt(th * DEG, yy * H, -0.01 * W)
        d = lathe([[(0, 0.07 * W), (0.035 * W, 0.06 * W), (0.055 * W, 0.02 * W), (0.055 * W, 0.0)],
                   [(0.055 * W, 0.0), (0, 0.0)]], 10)
        s.add(list(gum)[i % 3], tf(d, R=rot_from_to(Y, n), t=p))
    return s


def storm():
    s = Slime("Guardian_Storm", "Guardian", 16)
    W = 16.0
    B = Body(W, 0.78 * W)
    H = B.H
    rng = random.Random(16)
    body = "#4A5A78"
    s.look("Body", body)
    s.add("Body", B.piece(40, 18))
    angry_face(s, B, W, H, 0.5 * H, 0.17 * W, 0.075 * W, 0.065 * W, body, sclera="#FFF27A", pupil=None,
               brow="#1E2638", glow=True, mouth_color="#1E2638")
    s.look("Cloud", "#D5DDEA")
    s.look("CloudDark", "#8C98B0")
    pts = [(0, 1.0, 0.15)] + [(a, 0.86, 0.12) for a in range(0, 360, 45)] + [(a, 0.72, 0.1) for a in range(20, 360, 60)]
    for k, (a, yy, r) in enumerate(pts):
        if abs(a - 270) < 30 and yy < 0.8:
            continue
        p, n = B.pt(a * DEG, yy * H, 0.04 * W)
        if k == 0:
            p = np.array([0, H + 0.02 * W, 0.02 * W])
        s.add("Cloud" if k % 3 else "CloudDark", tf(sphere(10, 6), s=(r * W, r * W * 0.75, r * W), t=p))
    s.look("Bolts", "#FFE14D", "Neon")
    for side, lean, size in ((-1, -24, 0.24), (1, 24, 0.24), (-1, -55, 0.16), (1, 55, 0.16)):
        at = np.array([side * (0.26 if abs(lean) < 40 else 0.4) * W, H * (0.98 if abs(lean) < 40 else 0.82), 0.02 * W])
        s.add("Bolts", tf(extrude(bolt_polygon(size * W), 0.03 * W), R=rot(Z, lean), t=at))
    s.look("Sparks", "#9FE8FF", "Neon")
    for i in range(8):
        a = i * 45 + 10
        p = (0.66 * W * math.cos(a * DEG), rng.uniform(0.2, 0.9) * H, 0.66 * W * math.sin(a * DEG))
        s.add("Sparks", tf(lathe([[(0, 0.03 * W), (0.018 * W, 0)], [(0.018 * W, 0), (0, -0.03 * W)]], 4, flat=True),
                           R=rot(Z, rng.uniform(0, 90)), t=p))
    return s


def cosmic():
    s = Slime("Guardian_Cosmic", "Guardian", 18)
    W = 18.0
    B = Body(W, 0.8 * W)
    H = B.H
    rng = random.Random(18)
    body = "#241A5C"
    s.look("Body", body, transparency=0.12)
    s.add("Body", B.piece(40, 18))
    angry_face(s, B, W, H, 0.55 * H, 0.17 * W, 0.075 * W, 0.065 * W, body, sclera="#9FE8FF", pupil=None,
               brow="#0E0A2A", glow=True, mouth_color="#C77DFF", mouth_glow=True, teeth="#E7F4FF")
    s.look("Stars", "#FFF4B8", "Neon")
    for i in range(18):
        th = rng.uniform(0, 2 * math.pi)
        yy = rng.uniform(0.15, 0.9) * H
        if abs(((th / DEG) % 360) - 270) < 35 and 0.3 * H < yy < 0.75 * H:
            continue
        p, n = B.pt(th, yy, 0.004 * W)
        size = rng.uniform(0.018, 0.035) * W
        s.add("Stars", place(extrude(star_polygon(5, size, size * 0.45), 0.006 * W), p, n, rot(Z, rng.uniform(0, 72))))
    # a planet ring around the body and little moons
    s.look("Ring", "#F2C38B", transparency=0.15)
    s.look("RingInner", "#C7A2FF", "Neon", transparency=0.2)
    M = rot(X, -26) @ rot(Z, -10)
    ring = lathe([[(0.6 * W, 0.006 * W), (0.8 * W, 0.0)], [(0.8 * W, 0.0), (0.6 * W, -0.006 * W)],
                  [(0.6 * W, -0.006 * W), (0.6 * W, 0.006 * W)]], 56)
    inner = lathe([[(0.54 * W, 0.004 * W), (0.59 * W, 0.0)], [(0.59 * W, 0.0), (0.54 * W, -0.004 * W)],
                   [(0.54 * W, -0.004 * W), (0.54 * W, 0.004 * W)]], 56)
    c = np.array([0, 0.48 * H, 0])
    s.add("Ring", tf(ring, R=M, t=c))
    s.add("RingInner", tf(inner, R=M, t=c))
    moons = {"MoonGrey": "#B7BDD1", "MoonPink": "#FF9EC7", "MoonTeal": "#6EE0D0"}
    for k, v in moons.items():
        s.look(k, v)
    for i, (a, r, yy) in enumerate(((40, 0.05, 1.05), (160, 0.035, 0.85), (300, 0.04, 0.95))):
        s.add(list(moons)[i], tf(sphere(14, 10), s=r * W,
                                 t=(0.62 * W * math.cos(a * DEG), yy * H, 0.62 * W * math.sin(a * DEG))))
    s.look("Crown", "#FFD54A", "Neon")
    for k in range(5):
        a = (k - 2) * 18
        p, n = B.pt(FRONT + a * DEG, 0.93 * H)
        d = normalize(n * 0.4 + Y)
        s.add("Crown", tf(extrude(star_polygon(4, 0.035 * W, 0.012 * W), 0.008 * W),
                          R=rot_from_to(Z, normalize([d[0], 0, -1])), t=p + d * 0.06 * W))
    return s


def boss():
    s = Slime("Guardian_Boss", "Guardian", 20)
    W = 20.0
    B = Body(W, 0.8 * W)
    H = B.H
    rng = random.Random(20)
    body = "#2E9E5B"
    s.look("Body", body)
    s.add("Body", B.piece(36, 16))
    angry_face(s, B, W, H, 0.53 * H, 0.17 * W, 0.075 * W, 0.06 * W, body, sclera="#FF4040", pupil=None,
               brow="#123D24", glow=True, mouth_color="#123D24")
    # huge spiked crown
    s.look("Crown", "#F2C230", "Metal")
    s.look("Gems", "#FF2D55", "Neon")
    tmp = Slime("tmp", "", 0)
    rc = 0.24 * W
    crown(tmp, "Crown", ["Gems"], rc, 0.07 * W, 7, 0.17 * W, 0.05 * W, 0.016 * W)
    at = np.array([0, B.y_at_radius(rc) - 0.025 * W, 0])
    for part, pieces in tmp.parts.items():
        for p in pieces:
            s.add(part, tf(p, R=rot(X, 4), t=at))
    # royal cape and collar
    s.look("Cape", "#4A1673")
    y1 = cape(s, "Cape", B, W, H, y1f=0.72, flare=0.1, nu=12, nv=6)
    s.look("Collar", "#2A0B45")
    path = [B.pt(th, y1, 0.04 * W)[0] + Y * 0.035 * W * math.sin(th) for th in np.linspace(-10 * DEG, 190 * DEG, 12)]
    s.add("Collar", tube(path, 0.035 * W, 6))
    # gold shoulder plates with spikes
    s.look("Pauldrons", "#F2C230", "Metal")
    s.look("Spikes", "#2A2A33", "Metal")
    for side in (-1, 1):
        th = (0 if side > 0 else 180) * DEG
        p, n = B.pt(th, 0.6 * H, -0.01 * W)
        R = rot_from_to(Y, n)
        dome = lathe([[(0, 0.06 * W), (0.08 * W, 0.045 * W), (0.12 * W, 0.0)], [(0.12 * W, 0.0), (0, 0.0)]], 16)
        s.add("Pauldrons", tf(dome, R=R, t=p))
        for k in (-1, 0, 1):
            d = normalize(n + Y * 0.6 + np.cross(Y, n) * 0.5 * k)
            s.add("Spikes", tf(cone(0.022 * W, 0.08 * W, 6), R=rot_from_to(Y, d), t=p + n * 0.04 * W + d * 0.01 * W))
    # power orbs circling the boss
    s.look("Orbs", "#7CFF9E", "Neon")
    for k in range(5):
        a = k * 72 + 20
        s.add("Orbs", tf(sphere(10, 6), s=0.03 * W,
                         t=(0.66 * W * math.cos(a * DEG), (0.35 + 0.12 * (k % 3)) * H, 0.66 * W * math.sin(a * DEG))))
    return s


GUARDIANS = [meadow, swamp, lava, crystal, void, candy, storm, cosmic, boss]


def main():
    export(GUARDIANS, os.path.join(ROOT, "guardians"), "guardians.json", "GuardianSetup.lua", 5000)


if __name__ == "__main__":
    main()
