#!/usr/bin/env python3
"""Builds the map props: the valley gate, zone props, street trees and the plaza statue.

    python3 slimes/tools/build_props.py

Writes slimes/props/<Name>.glb, slimes/props/props.json and slimes/roblox/PropSetup.lua. Studs, +Y up,
front faces -Z, bottom centre at the origin.
"""
import json
import math
import os
import random

import numpy as np

from build_slimes import (DEG, INK, ROOT, WHITE, Slime, eyes, king, leaf_polygon, lua_color, smile, X, Y, Z)
from build_guardians import bipyramid
from meshkit import (Body, bezier, box, closed, cone, cylinder, ellipsoid, extrude, icosphere, lathe, normalize,
                     place, ring_band, rot, rot_from_to, sphere, tf, torus, tube, write_glb, Piece)


class Prop(Slime):
    def __init__(self, name, zone, height, main):
        super().__init__(name, zone, height)
        self.zone, self.height, self.main = zone, height, main
        self.signs, self.lights = [], []
        self.kind = "Scatter" if zone in ("Meadow", "Swamp", "Lava", "Crystal", "Void") else "Landmark"

    def look(self, part, color, material="SmoothPlastic", transparency=0.0, collide=False):
        self.looks[part] = {"color": color, "material": material, "transparency": transparency,
                            "collide": collide}

    def merge(self, other, R=None, t=(0, 0, 0), s=1.0):
        for part, pieces in other.parts.items():
            if part not in self.looks:
                self.looks[part] = dict(other.looks[part], collide=other.looks[part].get("collide", False))
            for p in pieces:
                self.add(part, tf(p, s=s, R=R, t=t))


def rock(r, seed, sub=1, squash=0.7):
    """Faceted low-poly boulder sitting on y = 0."""
    rng = random.Random(seed)
    V, F = icosphere(sub)
    V = np.array([v * (1 + rng.uniform(-0.18, 0.18)) for v in V])
    V[:, 1] *= squash
    V = V * r
    V[:, 1] -= V[:, 1].min() + r * 0.25
    V[:, 1] = np.maximum(V[:, 1], 0.0)
    return closed(V, F, flat=True)


def flower_head(p, prop, petal_part, centre_part, r, n=6, tilt=None):
    R = tilt if tilt is not None else np.eye(3)
    for k in range(n):
        petal = tf(ellipsoid(r, r * 0.22, r * 0.5, 6, 3), t=(r * 0.95, 0, 0))
        prop.add(petal_part, tf(petal, R=R @ rot(Y, k * 360 / n) @ rot(Z, 12), t=p))
    prop.add(centre_part, tf(ellipsoid(r * 0.5, r * 0.32, r * 0.5, 10, 6), R=R, t=p + R @ Y * r * 0.08))


# ---------------------------------------------------------------- the gate

def valley_gate():
    s = Prop("ValleyGate", "Gate", 30, "Arch")
    goo = "#8BEA4F"
    s.look("Arch", goo, collide=True)
    s.look("Drips", goo)
    s.look("Eyes", INK)
    s.look("EyeShine", WHITE)
    s.look("Mouth", INK)
    stacks = {-1: ["#FF7BAC", "#5AB0FF", "#FFD54A"], 1: ["#8E6CFF", "#FF9F43", "#6EE07A"]}
    names = {"#FF7BAC": "SlimePink", "#5AB0FF": "SlimeBlue", "#FFD54A": "SlimeYellow", "#8E6CFF": "SlimePurple",
             "#FF9F43": "SlimeOrange", "#6EE07A": "SlimeGreen"}
    for side, colors in stacks.items():
        x = side * 39.0
        y = 0.0
        for (w, h, lift), color in zip(((22, 13, 10.4), (18, 11, 9.0), (14.5, 9.6, 0)), colors):
            part = names[color]
            s.look(part, color, collide=True)
            B = Body(w, h)
            s.add(part, tf(B.piece(32, 12), t=(x, y, 0)))
            if lift == 0:
                tmp = Slime("tmp", "", 0)
                eyes(tmp, B, 0.55 * h, 0.16 * w, 0.07 * w, 0.1 * w)
                smile(tmp, B, 0.38 * h, 0.1 * w, 0.055 * w, 0.022 * w)
                for part_, pieces in tmp.parts.items():
                    for p in pieces:
                        s.add(part_, tf(p, t=(x, y, 0)))
            y += lift
    # a fat goo arch between the stacks, wobbling a little in thickness
    xs = np.linspace(-37, 37, 44)
    path = np.array([[x, 22.0 + 3.8 * (1 - (x / 37) ** 2), 0] for x in xs])
    rad = 4.0 + 0.5 * np.sin(xs * 0.35) + 0.3 * np.sin(xs * 0.9)
    s.add("Arch", tube(path, rad, 14))
    rng = random.Random(30)
    for x in np.linspace(-31, 31, 17):
        if abs(x) < 24:
            continue
        top = 22.0 + 3.8 * (1 - (x / 37) ** 2) - 3.4
        ln = rng.uniform(2.5, 6.5)
        r = rng.uniform(0.9, 1.4)
        ys = np.linspace(top + 0.8, top - ln, 9)
        prof = r * np.array([1.0, 0.8, 0.62, 0.6, 0.64, 0.8, 1.0, 0.85, 0.0])
        zoff = rng.uniform(-1.2, 1.2)
        s.add("Drips", tube([[x, yy, zoff] for yy in ys], prof, 8))
    # hanging sign
    s.look("SignPanel", "#1E2440", collide=True)
    s.look("SignFrame", "#FF5C7A", collide=True)
    s.look("Straps", goo)
    y0, y1, w = 12.0, 20.5, 44.0
    s.add("SignPanel", tf(box(w, y1 - y0, 1.2), t=(0, (y0 + y1) / 2, 0)))
    outer = [(-w / 2 - 0.8, y0 - 0.8), (w / 2 + 0.8, y0 - 0.8), (w / 2 + 0.8, y1 + 0.8), (-w / 2 - 0.8, y1 + 0.8)]
    for P in ([(-w / 2 - 0.8, y0 - 0.8), (w / 2 + 0.8, y0 - 0.8), (w / 2 + 0.8, y0), (-w / 2 - 0.8, y0)],
              [(-w / 2 - 0.8, y1), (w / 2 + 0.8, y1), (w / 2 + 0.8, y1 + 0.8), (-w / 2 - 0.8, y1 + 0.8)],
              [(-w / 2 - 0.8, y0), (-w / 2, y0), (-w / 2, y1), (-w / 2 - 0.8, y1)],
              [(w / 2, y0), (w / 2 + 0.8, y0), (w / 2 + 0.8, y1), (w / 2, y1)]):
        s.add("SignFrame", extrude(P, 1.6))
    for x in (-15, 15):
        top = 22.0 + 3.8 * (1 - (x / 37) ** 2)
        s.add("Straps", tube([[x, y1 + 0.5, 0], [x, (y1 + top) / 2, 0], [x, top, 0]], 0.9, 10))
    s.signs.append({"part": "SignPanel", "text": "SLIME VALLEY", "color": "#FFD54A", "ppu": 20})
    return s


# ---------------------------------------------------------------- meadow

def meadow_flowers():
    s = Prop("Meadow_FlowerClump", "Meadow", 4, "Stems")
    s.look("Stems", "#3E9B3A")
    s.look("Leaves", "#58B84A")
    s.look("Grass", "#4CA843")
    s.look("Centres", "#FFC93D")
    colors = {"PetalsPink": "#FF8FC4", "PetalsWhite": "#FFFFFF", "PetalsPurple": "#B48CFF", "PetalsRed": "#FF5A5A"}
    for k, v in colors.items():
        s.look(k, v)
    rng = random.Random(3)
    keys = list(colors)
    for i in range(8):
        a = i * 137.5 * DEG
        r = 0.4 + 0.95 * math.sqrt(i / 8) + rng.uniform(0, 0.2)
        base = np.array([r * math.cos(a), 0, r * math.sin(a)])
        h = rng.uniform(1.6, 3.5)
        lean = normalize([base[0] + 0.01, 3.0, base[2]])
        top = base + lean * h
        mid = base + Y * h * 0.5 + (top - base) * 0.2
        stem = bezier([base - Y * 0.1, mid, top], 6)
        s.add("Stems", tube(stem, np.linspace(0.08, 0.05, 6), 5))
        tilt = rot_from_to(Y, normalize(lean + np.array([0, 0, -0.6])))
        flower_head(top, s, keys[i % 4], "Centres", rng.uniform(0.32, 0.45), 6, tilt)
        if i % 2 == 0:
            lf = tf(extrude(leaf_polygon(0.7, 0.18, 5), 0.04), R=rot(Y, a / DEG) @ rot(Z, 35))
            s.add("Leaves", tf(lf, t=base + Y * h * 0.35))
    for i in range(14):
        a = rng.uniform(0, 2 * math.pi)
        r = rng.uniform(0.3, 1.8)
        h = rng.uniform(0.5, 1.1)
        d = normalize([math.cos(a) * 0.4, 1, math.sin(a) * 0.4])
        s.add("Grass", tf(cone(0.09, h, 4), R=rot_from_to(Y, d), t=(r * math.cos(a), 0, r * math.sin(a))))
    return s


def mushroom(prop, p, h, r, cap_part, spot_part, rng, lean=(0, 0, 0)):
    d = normalize(np.array([0, 1.0, 0]) + np.array(lean))
    R = rot_from_to(Y, d)
    prop.add("Stems", tf(cylinder(r * 0.32, h, 14, r_top=r * 0.26), R=R, t=p))
    cap = lathe([[(0, r * 0.75), (r * 0.6, r * 0.62), (r * 0.95, r * 0.25), (r, 0.0)],
                 [(r, 0.0), (r * 0.3, -r * 0.08), (0, -r * 0.08)]], 20)
    cb = p + d * h * 0.96
    prop.add(cap_part, tf(cap, R=R, t=cb))
    for j in range(7):
        a = j * 2 * math.pi / 7 + rng.uniform(-0.3, 0.3)
        rr = r * (0.45 if j % 2 else 0.75)
        yy = r * (0.6 if j % 2 else 0.34)
        local = np.array([rr * math.cos(a), yy, rr * math.sin(a)])
        n = normalize([local[0], 0.9 * r, local[2]])
        prop.add(spot_part, place(ellipsoid(r * 0.13, r * 0.13, r * 0.03, 8, 4), cb + R @ local, R @ n))


def meadow_mushrooms():
    s = Prop("Meadow_Mushrooms", "Meadow", 6, "Stems")
    s.look("Stems", "#F3E9D2", collide=True)
    s.look("CapsRed", "#E2453C", collide=True)
    s.look("CapsOrange", "#F28C28")
    s.look("Spots", "#FFFFFF")
    s.look("Grass", "#4CA843")
    rng = random.Random(6)
    mushroom(s, np.array([0, 0, 0.2]), 4.3, 2.4, "CapsRed", "Spots", rng, (0.05, 0, 0.02))
    mushroom(s, np.array([2.3, 0, -0.9]), 2.0, 1.3, "CapsOrange", "Spots", rng, (0.25, 0, -0.1))
    mushroom(s, np.array([-1.9, 0, -1.4]), 1.3, 0.9, "CapsRed", "Spots", rng, (-0.3, 0, -0.1))
    for i in range(10):
        a = rng.uniform(0, 2 * math.pi)
        r = rng.uniform(0.8, 2.6)
        s.add("Grass", tf(cone(0.1, rng.uniform(0.5, 1.0), 4), R=rot_from_to(Y, normalize([math.cos(a) * .4, 1, math.sin(a) * .4])),
                          t=(r * math.cos(a), 0, r * math.sin(a))))
    return s


def meadow_sunflower():
    s = Prop("Meadow_Sunflower", "Meadow", 10, "Stem")
    s.look("Stem", "#3E9B3A", collide=True)
    s.look("Leaves", "#58B84A")
    s.look("Petals", "#FFD23F")
    s.look("Seeds", "#6B3E1F")
    path = bezier([[0, 0, 0], [0.3, 4, 0.2], [-0.2, 7, 0], [0.1, 8.6, -0.5]], 10)
    s.add("Stem", tube(path, np.linspace(0.3, 0.2, 10), 8))
    for y, a in ((3.0, 20), (5.2, 200), (6.6, 80)):
        lf = tf(extrude(leaf_polygon(2.0, 0.55, 7), 0.06), R=rot(Y, a) @ rot(Z, 25))
        s.add("Leaves", tf(lf, t=(0, y, 0)))
    head = np.array([0.1, 8.6, -0.7])
    R = rot(X, 20)  # face looks forward and a little up
    for k in range(16):
        petal = tf(ellipsoid(0.75, 0.32, 0.12, 8, 4), t=(1.35, 0, 0))
        s.add("Petals", tf(petal, R=R @ rot(Z, k * 22.5), t=head))
    s.add("Seeds", tf(ellipsoid(1.0, 1.0, 0.35, 18, 8), R=R, t=head - R @ Z * 0.1))
    return s


# ---------------------------------------------------------------- swamp

def swamp_dead_tree():
    s = Prop("Swamp_DeadTree", "Swamp", 11, "Trunk")
    s.look("Trunk", "#6B5B4E", collide=True)
    s.look("Branches", "#6B5B4E")
    s.look("Moss", "#7E9A55")
    rng = random.Random(11)
    trunk = bezier([[0, -0.2, 0], [0.4, 3, 0.2], [-0.6, 6.5, -0.3], [0.3, 9.4, 0.1]], 14)
    s.add("Trunk", tube(trunk, np.linspace(1.0, 0.35, 14) ** 1.0, 10))
    for a in (20, 110, 200, 290):
        d = np.array([math.cos(a * DEG), 0, math.sin(a * DEG)])
        root = bezier([d * 0.3 + Y * 0.8, d * 1.4 + Y * 0.3, d * 2.2 - Y * 0.1], 6)
        s.add("Trunk", tube(root, np.linspace(0.5, 0.0, 6), 7))
    branches = [(7.8, 30, 3.0, 2.2), (6.0, 150, 2.6, 1.8), (8.6, 240, 2.2, 2.4), (4.6, 320, 2.0, 1.2),
                (9.2, 100, 1.6, 1.8)]
    for y, a, ln, up in branches:
        p0 = trunk[np.argmin(np.abs(trunk[:, 1] - y))]
        d = np.array([math.cos(a * DEG), 0, math.sin(a * DEG)])
        p1 = p0 + d * ln * 0.5 + Y * up * 0.3
        p2 = p0 + d * ln + Y * up + Z * rng.uniform(-0.3, 0.3)
        br = bezier([p0, p1, p2], 7)
        s.add("Branches", tube(br, np.linspace(0.32, 0.0, 7), 6))
        twig = bezier([p1, p1 + d * 0.5 + Y * 0.9 + np.cross(Y, d) * 0.4], 4)
        s.add("Branches", tube(twig, np.linspace(0.14, 0.0, 4), 5))
        for j in range(2):
            q = p1 + (p2 - p1) * (0.3 + 0.4 * j)
            ln_m = rng.uniform(1.0, 2.2)
            strand = [q + np.array([0, -t * ln_m, 0]) + np.array([math.sin(t * 5) * 0.12, 0, 0]) for t in np.linspace(0, 1, 6)]
            s.add("Moss", tube(strand, np.linspace(0.12, 0.03, 6), 5))
    return s


def swamp_reeds():
    s = Prop("Swamp_Reeds", "Swamp", 5, "Stalks")
    s.look("Stalks", "#6E8F3A")
    s.look("Blades", "#86A84A")
    s.look("Cattails", "#7A4B2A")
    rng = random.Random(5)
    for i in range(10):
        a = i * 137.5 * DEG
        r = 0.2 + 0.9 * math.sqrt(i / 10)
        base = np.array([r * math.cos(a), 0, r * math.sin(a)])
        h = rng.uniform(3.0, 5.2)
        lean = normalize([base[0] * 0.25, 1, base[2] * 0.25])
        top = base + lean * h
        s.add("Stalks", tube([base, base + lean * h * 0.5, top], [0.07, 0.06, 0.03], 5))
        if i % 2 == 0:
            s.add("Cattails", tf(lathe([[(0, 0.75), (0.17, 0.62), (0.18, 0.12), (0, 0.0)]], 8),
                                 R=rot_from_to(Y, lean), t=base + lean * (h * 0.78)))
    for i in range(9):
        a = rng.uniform(0, 2 * math.pi)
        r = rng.uniform(0.2, 1.2)
        h = rng.uniform(1.8, 3.6)
        d = np.array([math.cos(a), 0, math.sin(a)])
        path = bezier([d * r, d * (r + 0.3) + Y * h * 0.6, d * (r + 0.9) + Y * h], 6)
        s.add("Blades", tube(path, np.linspace(0.12, 0.0, 6), 4, aspect=0.25,
                             ref=np.cross(Y, d) if np.linalg.norm(np.cross(Y, d)) > 0 else X))
    return s


# ---------------------------------------------------------------- lava

def lava_spire():
    s = Prop("Lava_Spire", "Lava", 10, "Rock")
    s.look("Rock", "#3A2E2B", "Slate", collide=True)
    s.look("Magma", "#FF6A00", "Neon")
    s.look("Rubble", "#2B2220", "Slate")
    rng = random.Random(10)
    y, r = 0.0, 1.8
    off = np.zeros(3)
    for i in range(6):
        h = rng.uniform(1.3, 1.9)
        r_top = r * rng.uniform(0.72, 0.82)
        seg = lathe([[(0, h), (r_top, h)], [(r_top, h), (r, 0)], [(r, 0), (0, 0)]], 6, flat=True)
        s.add("Rock", tf(seg, R=rot(Y, rng.uniform(0, 60)) @ rot(normalize([1, 0, rng.uniform(-1, 1)]), rng.uniform(-5, 5)),
                         t=off + Y * y))
        s.add("Magma", tf(cylinder(r * 0.86, h + 0.4, 8, r_top=r_top * 0.86), t=off + Y * (y - 0.05)))
        y += h + 0.32
        r = r_top
        off = off + np.array([rng.uniform(-0.12, 0.12), 0, rng.uniform(-0.12, 0.12)])
    s.add("Rock", tf(cone(r, 1.0, 6), R=rot(Y, 15), t=off + Y * y))
    for i in range(5):
        a = i * 72 + rng.uniform(-15, 15)
        d = np.array([math.cos(a * DEG), 0, math.sin(a * DEG)])
        s.add("Rubble", tf(rock(rng.uniform(0.5, 0.9), i), t=d * rng.uniform(2.1, 2.7)))
    return s


def lava_vent():
    s = Prop("Lava_Vent", "Lava", 6, "Rock")
    s.look("Rock", "#3A2E2B", "Slate", collide=True)
    s.look("Pool", "#FF7A00", "Neon")
    s.look("Embers", "#FFC23D", "Neon")
    s.look("Smoke", "#9A8F8C", transparency=0.45)
    prof = [(3.6, 0.0), (3.0, 0.9), (2.0, 2.3), (1.45, 3.0), (1.2, 3.05), (1.0, 2.5)]
    s.add("Rock", lathe([prof + [(0, 2.5)], [(0, 0.0), (3.6, 0.0)]], 9, flat=True))
    s.add("Pool", cylinder(1.08, 2.62, 16))
    rng = random.Random(8)
    for i in range(6):
        a = rng.uniform(0, 2 * math.pi)
        s.add("Embers", tf(sphere(6, 4), s=rng.uniform(0.12, 0.2),
                           t=(0.6 * math.cos(a), 3.2 + rng.uniform(0, 1.8), 0.6 * math.sin(a))))
    for y, r, x in ((4.0, 0.9, 0.0), (4.9, 1.1, 0.3), (5.6, 0.8, -0.2)):
        s.add("Smoke", tf(ellipsoid(r, r * 0.8, r, 12, 8), t=(x, y, 0)))
    return s


# ---------------------------------------------------------------- crystal

def crystal_cluster(name, height, glass, core, seed, scale):
    s = Prop(name, "Crystal", height, "Crystals")
    s.look("Crystals", glass, "Glass", 0.15, collide=True)
    s.look("Cores", core, "Neon")
    s.look("Base", "#5E6178", "Slate", collide=True)
    rng = random.Random(seed)
    s.add("Base", rock(1.9 * scale, seed, squash=0.45))
    spec = [(0, 0, 1.0), (40, 0.7, 0.72), (130, 0.75, 0.6), (210, 0.7, 0.66), (290, 0.75, 0.5), (85, 1.2, 0.4),
            (250, 1.25, 0.35)]
    for a, r, k in spec:
        d = np.array([math.cos(a * DEG), 0, math.sin(a * DEG)])
        lean = normalize(Y + d * (0.15 + r * 0.45))
        h = height * 0.78 * k
        rw = 0.55 * scale * (0.6 + k * 0.6)
        R = rot_from_to(Y, lean) @ rot(Y, rng.uniform(0, 60))
        base = d * r * scale + Y * 0.3 * scale
        s.add("Crystals", tf(bipyramid(rw, h, rw * 1.6), R=R, t=base))
        s.add("Cores", tf(bipyramid(rw * 0.42, h * 0.85, rw * 0.7), R=R, t=base))
    return s


def crystal_big():
    return crystal_cluster("Crystal_Cluster", 9, "#B9A7FF", "#E7DEFF", 12, 1.0)


def crystal_small():
    return crystal_cluster("Crystal_Shards", 4.5, "#8FE9FF", "#E0FBFF", 13, 0.55)


# ---------------------------------------------------------------- void

def void_obelisk():
    s = Prop("Void_Obelisk", "Void", 12, "Obelisk")
    s.look("Obelisk", "#1A1024", "Slate", collide=True)
    s.look("Runes", "#B026FF", "Neon")
    s.look("Capstone", "#C77DFF", "Neon")
    s.look("Steps", "#120B1A", "Slate", collide=True)
    s.add("Steps", tf(box(4.6, 0.6, 4.6), t=(0, 0.3, 0)))
    s.add("Steps", tf(box(3.6, 0.5, 3.6), t=(0, 0.85, 0)))
    ob = lathe([[(0, 9.4), (1.0, 9.4)], [(1.0, 9.4), (1.5, 1.1)], [(1.5, 1.1), (0, 1.1)]], 4, flat=True)
    s.add("Obelisk", tf(ob, R=rot(Y, 45)))
    for k in range(4):
        R = rot(Y, k * 90)
        for y, h in ((3.0, 2.2), (6.2, 1.6)):
            # glowing rune marks on each face of the obelisk
            r = 1.5 - (y + h / 2 - 1.1) / 8.3 * 0.5
            z = -r * 0.7071 - 0.03
            for j, k in enumerate((0.15, 0.5, 0.85)):
                size = 0.42 if j == 1 else 0.28
                s.add("Runes", tf(box(size, size, 0.08), R=R @ rot(Z, 45), t=R @ np.array([0, y + h * k, z])))
    s.add("Capstone", tf(bipyramid(0.8, 0.2, 1.2, 4), R=rot(Y, 45), t=(0, 10.4, 0)))
    s.lights.append("Capstone")
    return s


def void_shards():
    s = Prop("Void_Shards", "Void", 7, "Shards")
    s.look("Shards", "#5B21A8", "Glass", 0.25)
    s.look("Cores", "#C77DFF", "Neon")
    s.look("GlowRing", "#B026FF", "Neon")
    s.look("Rubble", "#120B1A", "Slate", collide=True)
    s.add("GlowRing", tf(torus(2.2, 0.08, 40, 6), t=(0, 0.1, 0)))
    rng = random.Random(7)
    for i, (a, r, y, h) in enumerate(((0, 0, 3.4, 3.0), (60, 1.6, 2.2, 1.8), (170, 1.7, 4.4, 1.6),
                                      (250, 1.5, 1.6, 1.4), (320, 1.8, 5.4, 1.2))):
        d = np.array([math.cos(a * DEG), 0, math.sin(a * DEG)])
        R = rot(normalize([rng.uniform(-1, 1), 0.3, rng.uniform(-1, 1)]), rng.uniform(10, 30))
        p = d * r + Y * y
        s.add("Shards", tf(bipyramid(h * 0.22, h * 0.45, h * 0.5, 4), R=R, t=p))
        s.add("Cores", tf(bipyramid(h * 0.09, h * 0.4, h * 0.25, 4), R=R, t=p))
    for i in range(4):
        a = i * 90 + 30
        d = np.array([math.cos(a * DEG), 0, math.sin(a * DEG)])
        s.add("Rubble", tf(rock(0.45, 40 + i), t=d * 2.4))
    return s


# ---------------------------------------------------------------- street trees

def tree_a():
    s = Prop("Tree_A", "Street", 20, "Trunk")
    s.look("Trunk", "#8B5A2B", "Wood", collide=True)
    s.look("Leaves", "#4CBF4F", collide=True)
    s.look("LeavesDark", "#38A040", collide=True)
    trunk = bezier([[0, -0.2, 0], [0.5, 5, 0.2], [-0.4, 9, 0], [0, 12.5, 0]], 10)
    s.add("Trunk", tube(trunk, np.linspace(1.3, 0.75, 10), 10))
    for a in (30, 160, 280):
        d = np.array([math.cos(a * DEG), 0, math.sin(a * DEG)])
        s.add("Trunk", tube(bezier([d * 0.6 + Y * 0.6, d * 1.6 + Y * 0.1, d * 2.3 - Y * 0.1], 5),
                            np.linspace(0.55, 0.0, 5), 7))
    rng = random.Random(21)
    lobes = [((0, 14.5, 0), 5.2), ((3.6, 12.6, 1.0), 3.6), ((-3.5, 13.0, -0.6), 3.8), ((0.6, 12.2, -3.4), 3.4),
             ((-0.8, 12.6, 3.3), 3.5), ((1.4, 17.2, 0.4), 3.4), ((-1.8, 16.4, -1.0), 3.0)]
    for i, (c, r) in enumerate(lobes):
        V, F = icosphere(2)
        V = V * np.array([1, 0.9, 1]) * r * np.array([1 + rng.uniform(-0.05, 0.05)] * 3)
        s.add("Leaves" if i % 2 == 0 else "LeavesDark", closed(V + np.array(c), F, flat=True))
    return s


def tree_b():
    s = Prop("Tree_B", "Street", 24, "Trunk")
    s.look("Trunk", "#7A4A24", "Wood", collide=True)
    s.look("Needles", "#2E8B57", collide=True)
    s.look("NeedlesLight", "#3FA66A", collide=True)
    s.add("Trunk", cylinder(1.0, 6.5, 12, r_top=0.8))
    tiers = [(4.5, 7.6, 7.0), (8.4, 6.2, 6.4), (12.0, 4.8, 5.8), (15.4, 3.4, 5.2), (18.4, 2.0, 5.4)]
    for i, (y, r, h) in enumerate(tiers):
        n = 10
        prof = [(0, h)] + [(r * 0.55, h * 0.45), (r, 0.15 * h / 6), (r * 0.92, 0.0)]
        tier = lathe([prof, [(r * 0.92, 0.0), (r * 0.35, -0.6), (0, -0.6)]], n, flat=True)
        s.add("Needles" if i % 2 == 0 else "NeedlesLight", tf(tier, R=rot(Y, i * 17), t=(0, y, 0)))
    return s


# ---------------------------------------------------------------- plaza statue

def plaza_statue():
    s = Prop("PlazaStatue", "Plaza", 12, "Plinth")
    s.look("Plinth", "#EDE7DA", "Marble", collide=True)
    s.look("Trim", "#D4A93A", "Metal", collide=True)
    s.look("Plaque", "#C99A2E", "Metal")
    s.add("Plinth", tf(box(9.5, 1.0, 9.5), t=(0, 0.5, 0)))
    s.add("Trim", tf(box(8.4, 0.4, 8.4), t=(0, 1.2, 0)))
    s.add("Plinth", tf(box(7.6, 3.0, 7.6), t=(0, 2.9, 0)))
    s.add("Trim", tf(box(8.3, 0.5, 8.3), t=(0, 4.65, 0)))
    s.add("Plaque", tf(box(5.2, 1.5, 0.2), t=(0, 2.9, -3.85)))
    s.signs.append({"part": "Plaque", "text": "KING SLIME", "color": "#3B2A0A", "ppu": 60, "front_only": True})
    k = king()
    restyle = {"Body": ("#E3B53B", "Metal", True), "Crown": ("#C9921C", "Metal", False),
               "Eyes": ("#5A3E12", "Metal", False), "EyeShine": ("#FFF1C2", "Metal", False),
               "Mouth": ("#5A3E12", "Metal", False), "Cheeks": ("#E3B53B", "Metal", False),
               "Cushion": ("#B01030", "SmoothPlastic", False)}
    for part, (color, mat, col) in restyle.items():
        k.looks[part] = {"color": color, "material": mat, "transparency": 0.0, "collide": col}
    s.merge(k, s=1.12, t=(0, 4.9, 0))
    return s


# ---------------------------------------------------------------- zone cliffs
# One 24 x 32 x 14 stud piece per zone. The front (-Z) is the rocky face that looks into the valley;
# the sides are flat so pieces tile along a wall. CliffDresser.lua stretches them to fit your wall blocks.

CW, CH, CD = 24.0, 32.0, 14.0


def strata(s, rng, tones, layers=6, gap=0.0, glow_part=None, front_noise=1.3):
    """Stacked rock layers with a jagged front edge, stepping back a little as they rise."""
    heights = np.array([rng.uniform(0.8, 1.2) for _ in range(layers)])
    heights = heights / heights.sum() * (CH - gap * (layers - 1))
    y = 0.0
    xs = np.linspace(-CW / 2, CW / 2, 13)
    tops = []
    for k, h in enumerate(heights):
        inset = 0.55 * k + rng.uniform(-0.5, 0.5)
        front = [(x, -CD / 2 + inset + (rng.uniform(-front_noise, front_noise) if 0 < i < len(xs) - 1 else 0.4))
                 for i, x in enumerate(xs)]
        poly = front + [(CW / 2, CD / 2), (-CW / 2, CD / 2)]
        bot = [(x, y, z) for x, z in poly]
        top = [(x, y + h, z + rng.uniform(0.0, 0.35)) for x, z in poly]
        s.add(tones[k % len(tones)], prism(top[::-1], bot[::-1]))
        if glow_part and k < layers - 1:
            seam = [(x, z + 0.35) for x, z in front] + [(CW / 2, CD / 2 - 1), (-CW / 2, CD / 2 - 1)]
            sb = [(x, y + h - 0.05, z) for x, z in seam]
            st = [(x, y + h + gap + 0.05, z) for x, z in seam]
            s.add(glow_part, prism(st[::-1], sb[::-1]))
        tops.append((y + h, front))
        y += h + gap
    return tops


def prism(top, bottom):
    from meshkit import prism as _prism
    return _prism(top, bottom)


def cap_slab(s, part, top_y, front, thick, overhang, rng):
    poly = [(x, z - overhang - rng.uniform(0, 0.4)) for x, z in front] + [(CW / 2, CD / 2), (-CW / 2, CD / 2)]
    bot = [(x, top_y - 0.2, z) for x, z in poly]
    top = [(x, top_y + thick, z + 0.2) for x, z in poly]
    s.add(part, prism(top[::-1], bot[::-1]))


def cliff_meadow():
    s = Prop("Cliff_Meadow", "Meadow", CH + 2, "Rock")
    s.kind = "Cliff"
    rng = random.Random(101)
    s.look("Rock", "#8C7B66", "Slate", collide=True)
    s.look("RockLight", "#A8957C", "Slate", collide=True)
    s.look("Grass", "#5CC24A", "Grass", collide=True)
    s.look("GrassBlades", "#4CAE3E")
    s.look("Centres", "#FFC93D")
    s.look("PetalsPink", "#FF8FC4")
    s.look("PetalsWhite", "#FFFFFF")
    tops = strata(s, rng, ["Rock", "RockLight"])
    top_y, front = tops[-1]
    cap_slab(s, "Grass", top_y, front, 1.2, 0.8, rng)
    # grass tufts hanging over the edge and flowers on top
    for i in range(16):
        x = rng.uniform(-CW / 2 + 1, CW / 2 - 1)
        z = -CD / 2 + 3.2 + rng.uniform(-0.4, 0.6)
        d = normalize([rng.uniform(-0.3, 0.3), 1, rng.uniform(-0.6, 0.1)])
        s.add("GrassBlades", tf(cone(0.25, rng.uniform(1.0, 1.8), 4), R=rot_from_to(Y, d), t=(x, top_y + 1.0, z)))
    for i in range(7):
        x = rng.uniform(-CW / 2 + 1.5, CW / 2 - 1.5)
        z = rng.uniform(-CD / 2 + 3, CD / 2 - 2)
        p = np.array([x, top_y + 1.0, z])
        s.add("GrassBlades", tube([p, p + Y * 0.7, p + Y * 1.4], 0.06, 4))
        flower_head(p + Y * 1.45, s, "PetalsPink" if i % 2 else "PetalsWhite", "Centres", 0.45, 5)
    return s


def cliff_swamp():
    s = Prop("Cliff_Swamp", "Swamp", CH + 1, "Rock")
    s.kind = "Cliff"
    rng = random.Random(102)
    s.look("Rock", "#4A5A4C", "Slate", collide=True)
    s.look("RockDark", "#3A4840", "Slate", collide=True)
    s.look("Moss", "#5E8C3A", "Grass", collide=True)
    s.look("HangingMoss", "#7E9A55")
    s.look("Goo", "#8DBB5E")
    s.look("Stems", "#E8DCC0")
    s.look("Caps", "#C2552D")
    s.look("Spots", "#FFF4E0")
    tops = strata(s, rng, ["Rock", "RockDark"], layers=5)
    top_y, front = tops[-1]
    cap_slab(s, "Moss", top_y, front, 0.8, 1.0, rng)
    for i in range(10):
        x = rng.uniform(-CW / 2 + 1, CW / 2 - 1)
        fz = np.interp(x, [f[0] for f in front], [f[1] for f in front]) - 1.0
        ln = rng.uniform(3, 9)
        strand = [[x + math.sin(t * 4 + i) * 0.25, top_y + 0.4 - t * ln, fz - 0.15] for t in np.linspace(0, 1, 7)]
        s.add("HangingMoss", tube(strand, np.linspace(0.28, 0.06, 7), 5))
    for i in range(4):
        x = rng.uniform(-CW / 2 + 2, CW / 2 - 2)
        ly, lf = tops[rng.randint(1, len(tops) - 2)]
        fz = np.interp(x, [f[0] for f in lf], [f[1] for f in lf]) + 0.3
        ys = np.linspace(ly, ly - rng.uniform(3, 6), 8)
        prof = np.array([0.6, 0.5, 0.42, 0.42, 0.48, 0.6, 0.5, 0.0])
        s.add("Goo", tube([[x, yy, fz - 0.2] for yy in ys], prof, 7))
    for i in range(3):
        x = rng.uniform(-CW / 2 + 2, CW / 2 - 2)
        mushroom_into(s, np.array([x, top_y + 0.7, rng.uniform(-CD / 2 + 3, 0)]), rng.uniform(1.2, 2.2), rng)
    return s


def mushroom_into(s, p, k, rng):
    s.add("Stems", tf(cylinder(0.25 * k, 0.9 * k, 10, r_top=0.2 * k), t=p))
    cap = lathe([[(0, 0.55 * k), (0.45 * k, 0.45 * k), (0.7 * k, 0.12 * k), (0.72 * k, 0.0)],
                 [(0.72 * k, 0.0), (0, 0.0)]], 12)
    s.add("Caps", tf(cap, t=p + Y * 0.85 * k))
    for j in range(3):
        a = rng.uniform(0, 2 * math.pi)
        local = np.array([0.4 * k * math.cos(a), 0.47 * k, 0.4 * k * math.sin(a)])
        n = normalize([local[0], 0.6 * k, local[2]])
        s.add("Spots", place(ellipsoid(0.12 * k, 0.12 * k, 0.04 * k, 6, 3), p + Y * 0.85 * k + local, n))


def cliff_lava():
    s = Prop("Cliff_Lava", "Lava", CH + 3, "Rock")
    s.kind = "Cliff"
    rng = random.Random(103)
    s.look("Rock", "#2E2422", "Slate", collide=True)
    s.look("RockLight", "#3D302C", "Slate", collide=True)
    s.look("Magma", "#FF6A00", "Neon")
    s.look("Spikes", "#1A1413", "Slate")
    tops = strata(s, rng, ["Rock", "RockLight"], layers=6, gap=0.5, glow_part="Magma")
    top_y, front = tops[-1]
    for i in range(6):
        x = -CW / 2 + 2 + i * (CW - 4) / 5 + rng.uniform(-1, 1)
        z = rng.uniform(-CD / 2 + 3, CD / 2 - 3)
        d = normalize([rng.uniform(-0.2, 0.2), 1, rng.uniform(-0.3, 0.1)])
        s.add("Spikes", tf(cone(rng.uniform(0.8, 1.3), rng.uniform(2.5, 4.5), 5), R=rot_from_to(Y, d),
                           t=(x, top_y - 0.2, z)))
    # a lava fall down the face
    x = rng.uniform(-4, 4)
    path = [[x + math.sin(t * 3) * 0.6, top_y - t * (top_y - 0.5), np.interp(x, [f[0] for f in tops[-1 - int(t * 4)][1]],
             [f[1] for f in tops[-1 - int(t * 4)][1]]) - 0.4] for t in np.linspace(0, 1, 10)]
    s.add("Magma", tube(path, np.linspace(0.9, 1.3, 10), 7))
    return s


def cliff_crystal():
    s = Prop("Cliff_Crystal", "Crystal", CH + 6, "Rock")
    s.kind = "Cliff"
    rng = random.Random(104)
    s.look("Rock", "#6F7394", "Slate", collide=True)
    s.look("RockLight", "#8A8FB3", "Slate", collide=True)
    s.look("Snow", "#F4F8FF", "SmoothPlastic", collide=True)
    s.look("Crystals", "#B9A7FF", "Glass", 0.15)
    s.look("Cores", "#E7DEFF", "Neon")
    tops = strata(s, rng, ["Rock", "RockLight"])
    top_y, front = tops[-1]
    cap_slab(s, "Snow", top_y, front, 0.7, 0.6, rng)

    def crystal(p, d, h):
        rw = 0.35 * h ** 0.8
        R = rot_from_to(Y, d) @ rot(Y, rng.uniform(0, 60))
        s.add("Crystals", tf(bipyramid(rw, h, rw * 1.6), R=R, t=p))
        s.add("Cores", tf(bipyramid(rw * 0.4, h * 0.85, rw * 0.7), R=R, t=p))

    for i in range(5):
        x = rng.uniform(-CW / 2 + 2, CW / 2 - 2)
        crystal(np.array([x, top_y + 0.3, rng.uniform(-CD / 2 + 3, CD / 2 - 3)]),
                normalize([rng.uniform(-0.4, 0.4), 1, rng.uniform(-0.5, 0.2)]), rng.uniform(2.5, 5.5))
    for i in range(3):
        x = rng.uniform(-CW / 2 + 2, CW / 2 - 2)
        ly, lf = tops[rng.randint(1, len(tops) - 2)]
        fz = np.interp(x, [f[0] for f in lf], [f[1] for f in lf]) + 1.0
        crystal(np.array([x, ly - 1.5, fz]), normalize([rng.uniform(-0.3, 0.3), 0.6, -1]), rng.uniform(2, 3.5))
    return s


def cliff_void():
    s = Prop("Cliff_Void", "Void", CH + 9, "Rock")
    s.kind = "Cliff"
    rng = random.Random(105)
    s.look("Rock", "#1C1426", "Slate", collide=True)
    s.look("RockLight", "#271C35", "Slate", collide=True)
    s.look("Veins", "#9B30FF", "Neon")
    s.look("Floaters", "#271C35", "Slate")
    s.look("FloaterGlow", "#C77DFF", "Neon")
    tops = strata(s, rng, ["Rock", "RockLight"], layers=6, gap=0.35, glow_part="Veins")
    top_y, front = tops[-1]
    for i, (x, h, r) in enumerate(((-7, 4.5, 1.6), (1, 7.0, 1.2), (8, 5.2, 1.4))):
        rk = rock(r, 300 + i, squash=0.9)
        V = rk.V.copy()
        V[:, 1] = V[:, 1] - V[:, 1].max() * 0.5
        flip = Piece(np.vstack([V, V * np.array([1, -0.6, 1])]),
                     np.vstack([rk.F, rk.F[:, ::-1] + len(V)]), True)
        s.add("Floaters", tf(flip, t=(x, top_y + h, rng.uniform(-2, 2))))
        s.add("FloaterGlow", tf(sphere(6, 4), s=0.25 * r, t=(x, top_y + h - r * 0.9, 0)))
    return s


# ---------------------------------------------------------------- zone arches
# A themed arch per zone to replace the plain banners: 96 wide and about 30 tall, with a sign panel
# that PropSetup fills with the zone name and the rarity line.

AW = 96.0
ZONE_TEXT = {
    "Meadow": ("SUNNY MEADOW", "Common + Uncommon slimes", "#F2D03B"),
    "Swamp": ("GLOOP SWAMP", "Uncommon + Rare slimes", "#7CE38B"),
    "Lava": ("MAGMA PITS", "Rare + Epic slimes", "#FF9A3D"),
    "Crystal": ("CRYSTAL PEAKS", "Epic + Legendary slimes", "#A99BFF"),
    "Void": ("THE VOID", "Legendary + Mythic slimes", "#C77DFF"),
}


def arch_sign(s, zone, frame_color, y0=15.5, y1=24.5, w=62.0):
    title, sub, accent = ZONE_TEXT[zone]
    s.look("SignPanel", "#1E2440", collide=True)
    s.look("SignFrame", frame_color, collide=True)
    s.add("SignPanel", tf(box(w, y1 - y0, 1.0), t=(0, (y0 + y1) / 2, 0)))
    b = 0.9
    for P in ([(-w / 2 - b, y0 - b), (w / 2 + b, y0 - b), (w / 2 + b, y0), (-w / 2 - b, y0)],
              [(-w / 2 - b, y1), (w / 2 + b, y1), (w / 2 + b, y1 + b), (-w / 2 - b, y1 + b)],
              [(-w / 2 - b, y0), (-w / 2, y0), (-w / 2, y1), (-w / 2 - b, y1)],
              [(w / 2, y0), (w / 2 + b, y0), (w / 2 + b, y1), (w / 2, y1)]):
        s.add("SignFrame", extrude(P, 1.5))
    s.signs.append({"part": "SignPanel", "text": title, "sub": sub, "color": accent, "ppu": 16})
    return y0, y1, w


def arch_meadow():
    s = Prop("ZoneArch_Meadow", "Meadow", 30, "Posts")
    s.kind = "Arch"
    rng = random.Random(201)
    s.look("Posts", "#8B5A2B", "Wood", collide=True)
    s.look("Vines", "#3E9B3A")
    s.look("Leaves", "#58B84A")
    s.look("Centres", "#FFC93D")
    s.look("PetalsPink", "#FF8FC4")
    s.look("PetalsWhite", "#FFFFFF")
    s.look("PetalsPurple", "#B48CFF")
    y0, y1, w = arch_sign(s, "Meadow", "#C98A4B")
    for side in (-1, 1):
        x = side * (AW / 2 - 3)
        s.add("Posts", tf(cylinder(3.0, 28.5, 14, r_top=2.6), t=(x, 0, 0)))
        s.add("Posts", tf(cylinder(3.5, 1.4, 14), t=(x, 28.0, 0)))
        path = [[x + 3.1 * math.cos(t * 9), t * 27 + 0.5, 3.1 * math.sin(t * 9)] for t in np.linspace(0, 1, 40)]
        s.add("Vines", tube(path, 0.3, 6))
        for i in range(6):
            t = (i + 0.5) / 6
            p = np.array([x + 3.4 * math.cos(t * 9), t * 27 + 0.5, 3.4 * math.sin(t * 9)])
            flower_head(p, s, ["PetalsPink", "PetalsWhite", "PetalsPurple"][i % 3], "Centres", 0.8, 6,
                        rot_from_to(Y, normalize([math.cos(t * 9), 0.4, math.sin(t * 9)])))
    s.add("Posts", tube([[-AW / 2 + 3, 27.2, 0], [0, 28.6, 0], [AW / 2 - 3, 27.2, 0]], 1.5, 12))
    for x in (-w / 2 + 6, w / 2 - 6):
        s.add("Vines", tube([[x, 27.6, -0.2], [x, (y1 + 27.6) / 2, -0.2], [x, y1 + 0.5, -0.2]], 0.35, 6))
    for i in range(14):
        x = rng.uniform(-AW / 2 + 4, AW / 2 - 4)
        lf = tf(extrude(leaf_polygon(2.2, 0.7, 6), 0.1), R=rot(Y, rng.uniform(0, 360)) @ rot(Z, rng.uniform(-30, 30)))
        s.add("Leaves", tf(lf, t=(x, 28.2 + rng.uniform(-0.5, 1.2), rng.uniform(-1, 1))))
    for i, x in enumerate(np.linspace(-AW / 2 + 8, AW / 2 - 8, 9)):
        p = np.array([x, 29.6, -0.8])
        flower_head(p, s, ["PetalsPink", "PetalsWhite", "PetalsPurple"][i % 3], "Centres", 0.9, 6, rot(X, -60))
    return s


def arch_swamp():
    s = Prop("ZoneArch_Swamp", "Swamp", 30, "Logs")
    s.kind = "Arch"
    rng = random.Random(202)
    s.look("Logs", "#5C4A3A", "Wood", collide=True)
    s.look("Moss", "#5E8C3A", "Grass")
    s.look("HangingMoss", "#7E9A55")
    s.look("Stems", "#E8DCC0")
    s.look("Caps", "#C2552D")
    s.look("Spots", "#FFF4E0")
    y0, y1, w = arch_sign(s, "Swamp", "#4F7363")
    for side in (-1, 1):
        x = side * (AW / 2 - 3)
        path = [[x + math.sin(t * 7) * 0.9, t * 28.5, math.cos(t * 5) * 0.7] for t in np.linspace(0, 1, 16)]
        s.add("Logs", tube(path, np.linspace(3.4, 2.4, 16), 10))
        for a in (0, 120, 240):
            d = np.array([math.cos(a * DEG), 0, math.sin(a * DEG)])
            s.add("Logs", tube(bezier([[x, 1.5, 0] + d * 1.2, [x, 0.4, 0] + d * 3.0, [x, 0, 0] + d * 4.2], 5),
                               np.linspace(1.0, 0.0, 5), 7))
        s.add("Moss", tf(ellipsoid(2.6, 0.8, 2.6, 12, 6), t=(x, 28.4, 0)))
        mushroom_into(s, np.array([x - side * 0.8, 28.8, 0.3]), 2.4, rng)
        mushroom_into(s, np.array([x + side * 1.2, 28.6, -0.8]), 1.5, rng)
    beam = [[x, 27.8 + 0.8 * math.sin(x * 0.11), 0.3 * math.sin(x * 0.07)] for x in np.linspace(-AW / 2 + 3, AW / 2 - 3, 24)]
    s.add("Logs", tube(beam, 1.5, 10))
    for x in np.linspace(-AW / 2 + 6, AW / 2 - 6, 26):
        if abs(x) < w / 2 + 1:
            ln = rng.uniform(0.8, 2.5)
        else:
            ln = rng.uniform(3, 8)
        strand = [[x + math.sin(t * 5) * 0.2, 27.4 - t * ln, -1.1] for t in np.linspace(0, 1, 6)]
        s.add("HangingMoss", tube(strand, np.linspace(0.3, 0.06, 6), 5))
    for x in (-w / 2 + 6, w / 2 - 6):
        s.add("Logs", tube([[x, 27.4, 0], [x, y1 + 0.6, 0]], 0.4, 6))
    return s


def arch_lava():
    s = Prop("ZoneArch_Lava", "Lava", 30, "Basalt")
    s.kind = "Arch"
    rng = random.Random(203)
    s.look("Basalt", "#2E2422", "Slate", collide=True)
    s.look("BasaltLight", "#3D302C", "Slate", collide=True)
    s.look("Magma", "#FF6A00", "Neon")
    s.look("Horns", "#1A1413", "Slate")
    y0, y1, w = arch_sign(s, "Lava", "#FF7A1A")
    for side in (-1, 1):
        x0 = side * (AW / 2 - 4)
        for j, (dx, dz, r, h) in enumerate(((0, 0, 3.4, 27), (side * -3.4, 2.0, 2.3, 22), (side * 2.4, -2.6, 2.1, 18),
                                              (side * -2.0, -3.2, 1.8, 14))):
            y = 0.0
            while y < h - 0.5:
                seg = min(rng.uniform(3.5, 6), h - y)
                s.add("Basalt" if (int(y) // 4) % 2 == 0 else "BasaltLight",
                      tf(cylinder(r, seg - 0.25, 6), R=rot(Y, rng.uniform(-6, 6)), t=(x0 + dx, y, dz)))
                s.add("Magma", tf(cylinder(r * 0.9, 0.3, 6), t=(x0 + dx, y + seg - 0.27, dz)))
                y += seg
        s.add("Horns", tube(bezier([[x0, 26.5, 0], [x0 + side * 2.5, 30, 0], [x0 + side * 1.0, 33.5, -0.5]], 9),
                            np.linspace(1.4, 0.0, 9), 8))
    s.add("Basalt", tf(box(AW - 6, 2.6, 3.2), t=(0, 27.3, 0)))
    s.add("Magma", tf(box(AW - 8, 0.4, 3.4), t=(0, 26.1, 0)))
    for x in (-w / 2 + 6, w / 2 - 6):
        s.add("Basalt", tf(box(0.8, 26.0 - y1, 0.8), t=(x, (26.0 + y1) / 2, 0)))
    for i in range(10):
        x = rng.uniform(-AW / 2 + 8, AW / 2 - 8)
        d = normalize([rng.uniform(-0.3, 0.3), 1, rng.uniform(-0.2, 0.2)])
        s.add("Horns", tf(cone(rng.uniform(0.6, 1.0), rng.uniform(1.8, 3.2), 5), R=rot_from_to(Y, d), t=(x, 28.4, 0)))
    s.lights.append("Magma")
    return s


def arch_crystal():
    s = Prop("ZoneArch_Crystal", "Crystal", 30, "Ice")
    s.kind = "Arch"
    rng = random.Random(204)
    s.look("Ice", "#CFE9FF", "Ice", collide=True)
    s.look("Crystals", "#B9A7FF", "Glass", 0.15, collide=True)
    s.look("Cores", "#E7DEFF", "Neon")
    y0, y1, w = arch_sign(s, "Crystal", "#A99BFF")
    for side in (-1, 1):
        x0 = side * (AW / 2 - 4)
        for dx, dz, h, lean in ((0, 0, 28, 0), (side * -3.4, 1.4, 19, -10), (side * 2.8, -1.8, 15, 14),
                                (side * -1.4, -3.0, 11, -16)):
            rw = 0.36 * h ** 0.75
            R = rot(Z, lean * side) @ rot(Y, rng.uniform(0, 60))
            s.add("Crystals", tf(bipyramid(rw, h * 0.82, h * 0.18), R=R, t=(x0 + dx, 0, dz)))
            s.add("Cores", tf(bipyramid(rw * 0.4, h * 0.75, h * 0.12), R=R, t=(x0 + dx, 0, dz)))
    s.add("Ice", tf(box(AW - 8, 2.2, 2.8), t=(0, 26.6, 0)))
    for x in np.linspace(-AW / 2 + 8, AW / 2 - 8, 15):
        ln = rng.uniform(1.0, 3.2)
        s.add("Ice", tf(cone(0.45, ln, 5), R=rot(X, 180), t=(x, 25.6, rng.uniform(-0.8, 0.8))))
    for x in (-w / 2 + 6, w / 2 - 6):
        s.add("Ice", tf(box(0.7, 25.5 - y1, 0.7), t=(x, (25.5 + y1) / 2, 0)))
    for i in range(8):
        x = rng.uniform(-AW / 2 + 8, AW / 2 - 8)
        h = rng.uniform(2, 4.5)
        R = rot_from_to(Y, normalize([rng.uniform(-0.4, 0.4), 1, rng.uniform(-0.3, 0.3)]))
        s.add("Crystals", tf(bipyramid(0.4 * h ** 0.7, h, 0.5 * h), R=R, t=(x, 27.4, 0)))
    return s


def arch_void():
    s = Prop("ZoneArch_Void", "Void", 30, "Obelisks")
    s.kind = "Arch"
    rng = random.Random(205)
    s.look("Obelisks", "#1A1024", "Slate", collide=True)
    s.look("Runes", "#B026FF", "Neon")
    s.look("Shards", "#5B21A8", "Glass", 0.25)
    s.look("ShardCores", "#C77DFF", "Neon")
    y0, y1, w = arch_sign(s, "Void", "#9B30FF")
    for side in (-1, 1):
        x0 = side * (AW / 2 - 4)
        ob = lathe([[(0, 25), (2.8, 25)], [(2.8, 25), (4.2, 0)], [(4.2, 0), (0, 0)]], 4, flat=True)
        s.add("Obelisks", tf(ob, R=rot(Y, 45), t=(x0, 0, 0)))
        for k, y in enumerate(np.linspace(3, 21, 7)):
            r = 4.2 - y / 25 * 1.4
            size = 0.7 if k % 2 else 0.45
            s.add("Runes", tf(box(size, size, 0.1), R=rot(Z, 45), t=(x0, y, -r * 0.7071 - 0.05)))
        for j, (dy, a, h) in enumerate(((27.5, 0, 3.2), (31.0, 120, 2.0), (29.5, 240, 1.6))):
            d = np.array([math.cos(a * DEG), 0, math.sin(a * DEG)]) * (0 if j == 0 else 2.0)
            R = rot(normalize([rng.uniform(-1, 1), 0.2, rng.uniform(-1, 1)]), rng.uniform(5, 20))
            p = np.array([x0, dy, 0]) + d
            s.add("Shards", tf(bipyramid(h * 0.22, h * 0.45, h * 0.5, 4), R=R, t=p))
            s.add("ShardCores", tf(bipyramid(h * 0.09, h * 0.4, h * 0.25, 4), R=R, t=p))
    s.add("Obelisks", tf(box(AW - 10, 2.0, 2.6), t=(0, 26.0, 0)))
    s.add("Runes", tf(box(AW - 12, 0.3, 2.8), t=(0, 25.1, 0)))
    for x in (-w / 2 + 6, w / 2 - 6):
        s.add("Obelisks", tf(box(0.7, 25.0 - y1, 0.7), t=(x, (25.0 + y1) / 2, 0)))
    s.lights.append("ShardCores")
    return s



PROPS = [valley_gate, meadow_flowers, meadow_mushrooms, meadow_sunflower, swamp_dead_tree, swamp_reeds,
         lava_spire, lava_vent, crystal_big, crystal_small, void_obelisk, void_shards, tree_a, tree_b, plaza_statue,
         cliff_meadow, cliff_swamp, cliff_lava, cliff_crystal, cliff_void,
         arch_meadow, arch_swamp, arch_lava, arch_crystal, arch_void]


def write_lua(path, specs):
    lines = []
    for sp in specs:
        signs = ", ".join(
            f'{{ Part = "{g["part"]}", Text = "{g["text"]}", Sub = "{g.get("sub", "")}", Color = {lua_color(g["color"])}, '
            f'PixelsPerStud = {g["ppu"]}, FrontOnly = {"true" if g.get("front_only") else "false"} }}'
            for g in sp["signs"])
        lights = ", ".join(f'"{l}"' for l in sp["lights"])
        lines.append(f'\t{sp["name"]} = {{ Zone = "{sp["zone"]}", Kind = "{sp["kind"]}", Height = {sp["height"]:.4f}, '
                     f'Main = "{sp["main"]}", Signs = {{ {signs} }}, Lights = {{ {lights} }}, Parts = {{')
        for part, lk in sp["looks"].items():
            lines.append(f'\t\t{part} = {{ {lua_color(lk["color"])}, Enum.Material.{lk["material"]}, '
                         f'{lk["transparency"]:g}, {"true" if lk.get("collide") else "false"} }},')
        lines.append("\t} },")
    with open(os.path.join(os.path.dirname(__file__), "PropSetup.template.lua")) as f:
        template = f.read()
    with open(path, "w") as f:
        f.write(template.replace("--@SPECS@", "\n".join(lines)))


def main():
    out = os.path.join(ROOT, "props")
    os.makedirs(out, exist_ok=True)
    specs = []
    for build in PROPS:
        s = build()
        assert set(s.parts) == set(s.looks), (s.name, set(s.parts) ^ set(s.looks))
        assert s.main in s.parts, (s.name, s.main)
        allV = np.vstack([p.V for ps in s.parts.values() for p in ps])
        lo, hi = allV.min(0), allV.max(0)
        # every prop sits on the ground at its bottom centre
        shift = np.array([-(lo[0] + hi[0]) / 2, -lo[1], -(lo[2] + hi[2]) / 2])
        shift[0] = shift[0] if abs(shift[0]) > 0.05 else 0
        shift[2] = shift[2] if abs(shift[2]) > 0.05 else 0
        parts = {k: [Piece(p.V + shift, p.F, p.flat) for p in v] for k, v in s.parts.items()}
        tris = write_glb(os.path.join(out, s.name + ".glb"), s.name, parts, s.looks)
        size = hi - lo
        specs.append({"name": s.name, "zone": s.zone, "kind": s.kind, "height": float(size[1]), "main": s.main, "signs": s.signs,
                      "lights": s.lights, "triangles": int(tris), "size": size.round(2).tolist(), "looks": s.looks})
        print(f"{s.name:20s} {tris:6d} tris  size {np.round(size, 1)}")
    with open(os.path.join(out, "props.json"), "w") as f:
        json.dump(specs, f, indent=1)
    write_lua(os.path.join(ROOT, "roblox", "PropSetup.lua"), specs)


if __name__ == "__main__":
    main()
