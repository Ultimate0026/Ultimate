"""Small mesh toolkit for the slime models: primitives, transforms and a GLB writer.

Units are studs. +Y is up and the front of every model faces -Z (Roblox's LookVector).
"""
import json
import math
import struct

import numpy as np

EPS = 1e-7
Y = np.array([0.0, 1.0, 0.0])


# ---------------------------------------------------------------- pieces

class Piece:
    """A triangle mesh. `flat` pieces are exported with faceted normals."""

    def __init__(self, V, F, flat=False):
        self.V = np.asarray(V, float).reshape(-1, 3)
        self.F = np.asarray(F, np.int64).reshape(-1, 3)
        self.flat = flat


def signed_volume(V, F):
    a, b, c = V[F[:, 0]], V[F[:, 1]], V[F[:, 2]]
    return np.einsum("ij,ij->i", a, np.cross(b, c)).sum() / 6.0


def closed(V, F, flat=False):
    """Build a piece from a closed surface, flipping it so the normals face out."""
    V = np.asarray(V, float)
    F = np.asarray(F, np.int64)
    if signed_volume(V, F) < 0:
        F = F[:, ::-1]
    return Piece(V, F, flat)


def normalize(v):
    v = np.asarray(v, float)
    n = np.linalg.norm(v, axis=-1, keepdims=True)
    return v / np.maximum(n, EPS)


def rot(axis, deg):
    axis = normalize(axis)
    x, y, z = axis
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    C = 1 - c
    return np.array([
        [c + x * x * C, x * y * C - z * s, x * z * C + y * s],
        [y * x * C + z * s, c + y * y * C, y * z * C - x * s],
        [z * x * C - y * s, z * y * C + x * s, c + z * z * C],
    ])


def rot_from_to(a, b):
    a, b = normalize(a), normalize(b)
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    if np.linalg.norm(v) < 1e-9:
        if c > 0:
            return np.eye(3)
        perp = normalize(np.cross(a, [1, 0, 0] if abs(a[0]) < 0.9 else [0, 0, 1]))
        return rot(perp, 180)
    return rot(v, math.degrees(math.atan2(np.linalg.norm(v), c)))


def tf(p, s=(1, 1, 1), R=None, t=(0, 0, 0)):
    """Scale, then rotate, then move a piece."""
    s = np.broadcast_to(np.asarray(s, float), (3,))
    R = np.eye(3) if R is None else np.asarray(R, float)
    V = (p.V * s) @ R.T + np.asarray(t, float)
    F = p.F if np.prod(s) * np.linalg.det(R) > 0 else p.F[:, ::-1]
    return Piece(V, F.copy(), p.flat)


def frame(n):
    """Columns: viewer-right, up, out. For n = -Z this is (-X, +Y, -Z)."""
    n = normalize(n)
    t = np.cross(Y, n)
    if np.linalg.norm(t) < 1e-6:
        t = np.array([-1.0, 0, 0])
    t = normalize(t)
    u = np.cross(n, t)
    return np.column_stack([t, u, n])


def place(p, pos, n, local=None):
    """Put a piece authored in surface-local coordinates (x right, y up, z out) at pos."""
    R = frame(n)
    if local is not None:
        R = R @ local
    return tf(p, R=R, t=pos)


# ---------------------------------------------------------------- builders

def _ring_faces(idx, wrap=False):
    F = []
    pairs = list(zip(idx[:-1], idx[1:]))
    if wrap:
        pairs.append((idx[-1], idx[0]))
    for A, B in pairs:
        if len(A) == 1 and len(B) == 1:
            continue
        if len(A) == 1:
            n = len(B)
            F += [(A[0], B[k], B[(k + 1) % n]) for k in range(n)]
        elif len(B) == 1:
            n = len(A)
            F += [(A[k], B[0], A[(k + 1) % n]) for k in range(n)]
        else:
            n = len(A)
            for k in range(n):
                a, b, c, d = A[k], B[k], B[(k + 1) % n], A[(k + 1) % n]
                F += [(a, b, c), (a, c, d)]
    return F


def ring_mesh(rings, cap="none", wrap=False, flat=False):
    """Skin a list of rings. A ring with one point is a pole.

    cap: "none", "fan" (flat fan to the ring centre) or "strip" (for slab loops made of an
    outer row followed by the inner row reversed).
    """
    V, idx = [], []
    for r in rings:
        r = np.asarray(r, float).reshape(-1, 3)
        idx.append(list(range(len(V), len(V) + len(r))))
        V.extend(r)
    F = _ring_faces(idx, wrap)
    if cap != "none" and not wrap:
        for ring, end in ((idx[0], False), (idx[-1], True)):
            n = len(ring)
            if n < 3:
                continue
            if cap == "fan":
                c = len(V)
                V.append(np.mean([V[i] for i in ring], axis=0))
                for k in range(n):
                    a, b = ring[k], ring[(k + 1) % n]
                    F.append((c, b, a) if end else (c, a, b))
            else:
                for j in range(n // 2 - 1):
                    A, B, C, D = ring[j], ring[j + 1], ring[n - 2 - j], ring[n - 1 - j]
                    if end:
                        F += [(A, C, B), (A, D, C)]
                    else:
                        F += [(A, B, C), (A, C, D)]
    return np.array(V), np.array(F)


def merge_raw(parts):
    V, F, off = [], [], 0
    for v, f in parts:
        V.append(v)
        F.append(f + off)
        off += len(v)
    return np.vstack(V), np.vstack(F)


def lathe(strips, n=32, flat=False):
    """Surface of revolution around Y. Each strip is a list of (radius, y) from one
    corner of the profile to the next; strips meet at hard edges."""
    raws = []
    th = np.arange(n) * 2 * np.pi / n
    for prof in strips:
        rings = []
        for r, y in prof:
            if r < 1e-6:
                rings.append(np.array([[0.0, y, 0.0]]))
            else:
                rings.append(np.stack([r * np.cos(th), np.full(n, y), r * np.sin(th)], 1))
        raws.append(ring_mesh(rings))
    V, F = merge_raw(raws)
    return closed(V, F, flat)


def sphere(nu=16, nv=10):
    phi = np.linspace(0, np.pi, nv + 1)
    return lathe([[(math.sin(p), math.cos(p)) for p in phi]], nu)


def ellipsoid(rx, ry, rz, nu=14, nv=8):
    return tf(sphere(nu, nv), s=(rx, ry, rz))


def cylinder(r, h, n=24, r_top=None):
    rt = r if r_top is None else r_top
    return lathe([[(0, h), (rt, h)], [(rt, h), (r, 0)], [(r, 0), (0, 0)]], n)


def cone(r, h, n=12):
    return lathe([[(0, h), (r, 0)], [(r, 0), (0, 0)]], n)


def ring_band(r_in, r_out, h, n=32):
    return lathe([[(r_out, h), (r_out, 0)], [(r_out, 0), (r_in, 0)],
                  [(r_in, 0), (r_in, h)], [(r_in, h), (r_out, h)]], n)


def torus(R, r, n=32, m=10):
    rings = []
    for i in range(n):
        a = 2 * np.pi * i / n
        c, s = math.cos(a), math.sin(a)
        b = np.linspace(0, 2 * np.pi, m, endpoint=False)
        rr = R + r * np.cos(b)
        rings.append(np.stack([rr * c, r * np.sin(b), rr * s], 1))
    V, F = ring_mesh(rings, wrap=True)
    return closed(V, F)


def tube(path, radius, n=8, aspect=1.0, ref=None, cap="fan"):
    """Sweep a circle (or ellipse) along a path. Radius 0 at an end makes a point."""
    P = np.asarray(path, float)
    m = len(P)
    rad = np.broadcast_to(np.asarray(radius, float), (m,))
    T = np.gradient(P, axis=0)
    T = normalize(T)
    if ref is None:
        seed = np.cross(T[0], [0, 1, 0] if abs(T[0][1]) < 0.9 else [1, 0, 0])
        N = [normalize(seed)]
        for i in range(1, m):
            v = N[-1] - np.dot(N[-1], T[i]) * T[i]
            N.append(normalize(v) if np.linalg.norm(v) > 1e-6 else N[-1])
    else:
        ref = np.asarray(ref, float)
        N = [normalize(ref - np.dot(ref, t) * t) for t in T]
    a = np.linspace(0, 2 * np.pi, n, endpoint=False)
    rings = []
    for i in range(m):
        if rad[i] < 1e-6:
            rings.append(P[i][None])
            continue
        B = np.cross(T[i], N[i])
        ring = P[i] + rad[i] * (np.outer(np.cos(a), B) + aspect * np.outer(np.sin(a), N[i]))
        rings.append(ring)
    V, F = ring_mesh(rings, cap=cap)
    return closed(V, F)


def bezier(pts, k=12):
    pts = np.asarray(pts, float)
    out = []
    for t in np.linspace(0, 1, k):
        q = pts.copy()
        while len(q) > 1:
            q = (1 - t) * q[:-1] + t * q[1:]
        out.append(q[0])
    return np.array(out)


def _poly_area(P):
    x, y = P[:, 0], P[:, 1]
    return 0.5 * np.sum(x * np.roll(y, -1) - np.roll(x, -1) * y)


def triangulate(P):
    """Ear clipping for a simple polygon. Returns triangles in counter-clockwise order."""
    P = np.asarray(P, float)
    idx = list(range(len(P)))
    if _poly_area(P) < 0:
        idx.reverse()

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    tris = []
    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        n = len(idx)
        for i in range(n):
            a, b, c = idx[i - 1], idx[i], idx[(i + 1) % n]
            if cross(P[a], P[b], P[c]) <= 1e-12:
                continue
            inside = False
            for j in idx:
                if j in (a, b, c):
                    continue
                p = P[j]
                if (cross(P[a], P[b], p) >= 0 and cross(P[b], P[c], p) >= 0
                        and cross(P[c], P[a], p) >= 0):
                    inside = True
                    break
            if not inside:
                tris.append((a, b, c))
                idx.pop(i)
                break
        else:
            tris.append((idx[-1], idx[0], idx[1]))
            idx.pop(0)
    tris.append(tuple(idx))
    return tris, (_poly_area(P) < 0)


def extrude(poly, depth):
    """Prism from a 2D polygon in XY, centred on z = 0."""
    P = np.asarray(poly, float)
    if _poly_area(P) < 0:
        P = P[::-1]
    n = len(P)
    tris, _ = triangulate(P)
    d = depth / 2
    V, F = [], []
    top = [(x, y, d) for x, y in P]
    bot = [(x, y, -d) for x, y in P]
    V += top + bot
    for a, b, c in tris:
        F.append((a, b, c))
        F.append((n + c, n + b, n + a))
    for i in range(n):
        j = (i + 1) % n
        base = len(V)
        V += [bot[i], bot[j], top[j], top[i]]
        F += [(base, base + 1, base + 2), (base, base + 2, base + 3)]
    return closed(np.array(V), np.array(F), flat=True)


def box(sx, sy, sz):
    return tf(extrude([(-0.5, -0.5), (0.5, -0.5), (0.5, 0.5), (-0.5, 0.5)], 1.0), s=(sx, sy, sz))


def star_polygon(points, r_out, r_in):
    out = []
    for i in range(points * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.pi / 2 + i * math.pi / points
        out.append((r * math.cos(a), r * math.sin(a)))
    return out


def icosphere(sub=1):
    t = (1 + 5 ** 0.5) / 2
    V = [(-1, t, 0), (1, t, 0), (-1, -t, 0), (1, -t, 0), (0, -1, t), (0, 1, t), (0, -1, -t), (0, 1, -t),
         (t, 0, -1), (t, 0, 1), (-t, 0, -1), (-t, 0, 1)]
    V = [tuple(normalize(v)) for v in V]
    F = [(0, 11, 5), (0, 5, 1), (0, 1, 7), (0, 7, 10), (0, 10, 11), (1, 5, 9), (5, 11, 4), (11, 10, 2),
         (10, 7, 6), (7, 1, 8), (3, 9, 4), (3, 4, 2), (3, 2, 6), (3, 6, 8), (3, 8, 9), (4, 9, 5),
         (2, 4, 11), (6, 2, 10), (8, 6, 7), (9, 8, 1)]
    for _ in range(sub):
        cache = {}

        def mid(a, b):
            key = (min(a, b), max(a, b))
            if key not in cache:
                cache[key] = len(V)
                V.append(tuple(normalize(np.add(V[a], V[b]) / 2)))
            return cache[key]

        nf = []
        for a, b, c in F:
            ab, bc, ca = mid(a, b), mid(b, c), mid(c, a)
            nf += [(a, ab, ca), (b, bc, ab), (c, ca, bc), (ab, bc, ca)]
        F = nf
    return np.array(V), np.array(F)


def prism(top, bottom):
    """Closed prism between two matching polygons (lists of 3D points)."""
    top, bottom = np.asarray(top, float), np.asarray(bottom, float)
    V, F = ring_mesh([top, bottom], cap="fan")
    return closed(V, F, flat=True)


# ---------------------------------------------------------------- body shape

class Body:
    """A slime blob: a squashed dome with a flat bottom at y = 0, `W` studs wide."""

    def __init__(self, W, H, yc=0.33, sag=0.2):
        self.W, self.H = W, H
        self._yc, self._b = yc * H, H - yc * H
        self._sag = sag
        ys = np.linspace(0, H, 4001)
        self.a = 1.0
        self.a = (W / 2) / self.R(ys).max()

    def R(self, y):
        y = np.asarray(y, float)
        c = np.clip((y - self._yc) / self._b, -1, 1)
        return self.a * np.sqrt(1 - c * c) * (1 + self._sag * (1 - y / self.H) ** 2)

    def dR(self, y, h=1e-4):
        return (self.R(y + h) - self.R(y - h)) / (2 * h)

    def piece(self, n=32, rings=14):
        phi = np.linspace(0, math.acos(-self._yc / self._b), rings + 1)
        side = [(float(self.R(self._yc + self._b * math.cos(p))), self._yc + self._b * math.cos(p))
                for p in phi]
        side[0] = (0.0, self.H)
        side[-1] = (float(self.R(0.0)), 0.0)
        return lathe([side, [(float(self.R(0.0)), 0.0), (0.0, 0.0)]], n)

    def y_at_radius(self, r):
        lo, hi = self._yc, self.H
        for _ in range(60):
            mid = (lo + hi) / 2
            if self.R(mid) > r:
                lo = mid
            else:
                hi = mid
        return (lo + hi) / 2

    def pt(self, theta, y, out=0.0):
        """Surface point at angle theta (radians; 270 deg is the front) and height y."""
        R = float(self.R(y))
        c, s = math.cos(theta), math.sin(theta)
        n = normalize([c, -float(self.dR(y)), s])
        return np.array([R * c, y, R * s]) + n * out, n

    def front(self, fx, y, out=0.0):
        """Surface point on the face. fx is to the viewer's right (world -X)."""
        x = -fx
        R = float(self.R(y))
        x = max(-R + 1e-4, min(R - 1e-4, x))
        z = -math.sqrt(R * R - x * x)
        return self.pt(math.atan2(z, x), y, out)

    def on_face(self, piece, fx, y, out=0.0, local=None):
        p, n = self.front(fx, y)
        return place(piece, p + n * out, n, local)

    def face_path(self, pts, out=0.0):
        return np.array([self.front(fx, y, out)[0] for fx, y in pts])


# ---------------------------------------------------------------- export

ROBLOX_LOOK = {
    # Roblox material -> (metallic, roughness)
    "SmoothPlastic": (0.0, 0.45),
    "Plastic": (0.0, 0.6),
    "Neon": (0.0, 0.6),
    "Glass": (0.0, 0.08),
    "Metal": (0.85, 0.3),
    "Slate": (0.0, 0.95),
    "Ice": (0.0, 0.2),
    "Marble": (0.0, 0.35),
    "Wood": (0.0, 0.8),
    "Grass": (0.0, 0.9),
}


def srgb_to_linear(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def bake(pieces):
    """Merge pieces into positions, normals and indices."""
    Ps, Ns, Is, off = [], [], [], 0
    for p in pieces:
        V, F = p.V, p.F
        if p.flat:
            V = V[F].reshape(-1, 3)
            F = np.arange(len(V)).reshape(-1, 3)
            fn = normalize(np.cross(V[F[:, 1]] - V[F[:, 0]], V[F[:, 2]] - V[F[:, 0]]))
            N = np.repeat(fn, 3, axis=0)
        else:
            fn = np.cross(V[F[:, 1]] - V[F[:, 0]], V[F[:, 2]] - V[F[:, 0]])
            N = np.zeros_like(V)
            for k in range(3):
                np.add.at(N, F[:, k], fn)
            N = normalize(N)
        # drop degenerate triangles
        area = np.linalg.norm(np.cross(V[F[:, 1]] - V[F[:, 0]], V[F[:, 2]] - V[F[:, 0]]), axis=1)
        F = F[area > 1e-10]
        Ps.append(V)
        Ns.append(N)
        Is.append(F + off)
        off += len(V)
    return np.vstack(Ps), np.vstack(Ns), np.vstack(Is)


def write_glb(path, name, parts, looks):
    """parts: {part name: [pieces]}; looks: {part name: dict(color, material, transparency)}."""
    gltf = {
        "asset": {"version": "2.0", "generator": "slimes/tools/build_slimes.py"},
        "scene": 0,
        "scenes": [{"name": name, "nodes": []}],
        "nodes": [], "meshes": [], "materials": [], "accessors": [], "bufferViews": [],
        "buffers": [],
    }
    blob = bytearray()

    def add_view(data, target):
        while len(blob) % 4:
            blob.append(0)
        gltf["bufferViews"].append({"buffer": 0, "byteOffset": len(blob), "byteLength": len(data),
                                    "target": target})
        blob.extend(data)
        return len(gltf["bufferViews"]) - 1

    tri_total = 0
    for pname, pieces in parts.items():
        P, N, I = bake(pieces)
        tri_total += len(I)
        P32, N32 = P.astype(np.float32), N.astype(np.float32)
        big = len(P) > 65535
        I_arr = I.astype(np.uint32 if big else np.uint16).ravel()
        pv = add_view(P32.tobytes(), 34962)
        nv = add_view(N32.tobytes(), 34962)
        iv = add_view(I_arr.tobytes(), 34963)
        acc = gltf["accessors"]
        acc.append({"bufferView": pv, "componentType": 5126, "count": len(P), "type": "VEC3",
                    "min": P32.min(0).tolist(), "max": P32.max(0).tolist()})
        acc.append({"bufferView": nv, "componentType": 5126, "count": len(N), "type": "VEC3"})
        acc.append({"bufferView": iv, "componentType": 5125 if big else 5123,
                    "count": len(I_arr), "type": "SCALAR"})
        look = looks[pname]
        rgb = hex_rgb(look["color"])
        lin = [srgb_to_linear(c) for c in rgb]
        metal, rough = ROBLOX_LOOK[look["material"]]
        mat = {"name": pname, "pbrMetallicRoughness": {
            "baseColorFactor": lin + [1.0 - look.get("transparency", 0.0)],
            "metallicFactor": metal, "roughnessFactor": rough}}
        if look["material"] == "Neon":
            mat["emissiveFactor"] = lin
        if look.get("transparency", 0.0) > 0:
            mat["alphaMode"] = "BLEND"
        gltf["materials"].append(mat)
        a0 = len(acc) - 3
        gltf["meshes"].append({"name": pname, "primitives": [{
            "attributes": {"POSITION": a0, "NORMAL": a0 + 1}, "indices": a0 + 2,
            "material": len(gltf["materials"]) - 1}]})
        gltf["nodes"].append({"name": pname, "mesh": len(gltf["meshes"]) - 1})
        gltf["scenes"][0]["nodes"].append(len(gltf["nodes"]) - 1)

    while len(blob) % 4:
        blob.append(0)
    gltf["buffers"].append({"byteLength": len(blob)})
    js = json.dumps(gltf, separators=(",", ":")).encode()
    while len(js) % 4:
        js += b" "
    out = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(blob))
    out += struct.pack("<II", len(js), 0x4E4F534A) + js
    out += struct.pack("<II", len(blob), 0x004E4942) + bytes(blob)
    with open(path, "wb") as f:
        f.write(out)
    return tri_total
