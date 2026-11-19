"""Tiny dependency-free mesh toolkit used to author the original UrbanX vehicle.

All geometry is generated procedurally from primitives so the asset is 100% original
and reproducible. Coordinates: metres, +Y up, +Z vehicle front, +X vehicle left (glTF).
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

Vec3 = tuple[float, float, float]


def sub(a: Vec3, b: Vec3) -> Vec3:
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def add(a: Vec3, b: Vec3) -> Vec3:
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def cross(a: Vec3, b: Vec3) -> Vec3:
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a: Vec3, b: Vec3) -> float:
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def normalize(a: Vec3) -> Vec3:
    length = math.sqrt(dot(a, a))
    if length < 1e-12:
        return (0.0, 1.0, 0.0)
    return (a[0] / length, a[1] / length, a[2] / length)


@dataclass
class Mesh:
    positions: list[Vec3] = field(default_factory=list)
    normals: list[Vec3] = field(default_factory=list)
    indices: list[int] = field(default_factory=list)

    # ---- building blocks -------------------------------------------------
    def add_triangle(self, a: Vec3, b: Vec3, c: Vec3, normal: Vec3 | None = None) -> None:
        n = normal or normalize(cross(sub(b, a), sub(c, a)))
        base = len(self.positions)
        self.positions += [a, b, c]
        self.normals += [n, n, n]
        self.indices += [base, base + 1, base + 2]

    def add_smooth_triangle(self, verts: list[Vec3], norms: list[Vec3]) -> None:
        base = len(self.positions)
        self.positions += verts
        self.normals += norms
        self.indices += [base, base + 1, base + 2]

    def add_quad(self, a: Vec3, b: Vec3, c: Vec3, d: Vec3, outward_hint: Vec3 | None = None) -> None:
        """Adds a flat quad a-b-c-d. If outward_hint is given, winding is fixed so the
        face normal points along the hint (counter-clockwise when seen from outside)."""
        n = normalize(cross(sub(b, a), sub(c, a)))
        if outward_hint is not None and dot(n, outward_hint) < 0:
            a, b, c, d = d, c, b, a
            n = (-n[0], -n[1], -n[2])
        self.add_triangle(a, b, c, n)
        self.add_triangle(a, c, d, n)

    def extend(self, other: "Mesh") -> "Mesh":
        base = len(self.positions)
        self.positions += other.positions
        self.normals += other.normals
        self.indices += [i + base for i in other.indices]
        return self

    # ---- transforms --------------------------------------------------------
    def transformed(self, fn_point, fn_normal) -> "Mesh":
        return Mesh([fn_point(p) for p in self.positions], [normalize(fn_normal(n)) for n in self.normals], list(self.indices))

    def translated(self, t: Vec3) -> "Mesh":
        return self.transformed(lambda p: add(p, t), lambda n: n)

    def rotated(self, axis: str, degrees: float, origin: Vec3 = (0.0, 0.0, 0.0)) -> "Mesh":
        r = math.radians(degrees)
        c, s = math.cos(r), math.sin(r)

        def rot(v: Vec3) -> Vec3:
            x, y, z = v
            if axis == "x":
                return (x, y * c - z * s, y * s + z * c)
            if axis == "y":
                return (x * c + z * s, y, -x * s + z * c)
            return (x * c - y * s, x * s + y * c, z)

        return self.transformed(lambda p: add(rot(sub(p, origin)), origin), rot)

    def mirrored_x(self) -> "Mesh":
        """Mirror across the YZ plane (vehicle left <-> right), keeping faces outward."""
        idx: list[int] = []
        for i in range(0, len(self.indices), 3):
            a, b, c = self.indices[i:i + 3]
            idx += [a, c, b]
        return Mesh([(-p[0], p[1], p[2]) for p in self.positions], [(-n[0], n[1], n[2]) for n in self.normals], idx)

    @property
    def triangle_count(self) -> int:
        return len(self.indices) // 3

    def bounds(self) -> tuple[Vec3, Vec3]:
        xs = [p[0] for p in self.positions]
        ys = [p[1] for p in self.positions]
        zs = [p[2] for p in self.positions]
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


# ---- primitives -------------------------------------------------------------

def hexahedron(corners: list[Vec3], skip: set[str] | None = None) -> Mesh:
    """8 corners: bottom ring (y-) then top ring (y+), each ring ordered
    (-x,-z), (+x,-z), (+x,+z), (-x,+z). Faces: bottom, top, back(-z), front(+z), right(-x), left(+x)."""
    skip = skip or set()
    b0, b1, b2, b3, t0, t1, t2, t3 = corners
    center = tuple(sum(c[i] for c in corners) / 8.0 for i in range(3))
    faces = {
        "bottom": (b0, b1, b2, b3),
        "top": (t0, t3, t2, t1),
        "back": (b0, t0, t1, b1),
        "front": (b3, b2, t2, t3),
        "right": (b0, b3, t3, t0),  # -x side
        "left": (b1, t1, t2, b2),  # +x side
    }
    m = Mesh()
    for name, (a, b, c, d) in faces.items():
        if name in skip:
            continue
        face_center = tuple((a[i] + b[i] + c[i] + d[i]) / 4.0 for i in range(3))
        m.add_quad(a, b, c, d, outward_hint=sub(face_center, center))
    return m


def box(center: Vec3, size: Vec3, skip: set[str] | None = None) -> Mesh:
    cx, cy, cz = center
    hx, hy, hz = size[0] / 2, size[1] / 2, size[2] / 2
    return box_minmax((cx - hx, cy - hy, cz - hz), (cx + hx, cy + hy, cz + hz), skip)


def box_minmax(lo: Vec3, hi: Vec3, skip: set[str] | None = None) -> Mesh:
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    return hexahedron([
        (x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1),
        (x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1),
    ], skip)


def tapered_block(z0: float, z1: float, y0: float, y1: float,
                  bottom_half_width: float, top_half_width: float,
                  top_z0: float | None = None, top_z1: float | None = None,
                  skip: set[str] | None = None) -> Mesh:
    """A block whose top face can be narrower and shifted along z (used for cabins, bumpers)."""
    tz0 = z0 if top_z0 is None else top_z0
    tz1 = z1 if top_z1 is None else top_z1
    bw, tw = bottom_half_width, top_half_width
    return hexahedron([
        (-bw, y0, z0), (bw, y0, z0), (bw, y0, z1), (-bw, y0, z1),
        (-tw, y1, tz0), (tw, y1, tz0), (tw, y1, tz1), (-tw, y1, tz1),
    ], skip)


def _axis_frame(axis: str):
    if axis == "x":
        return lambda u, v, w: (w, u, v)
    if axis == "y":
        # right-handed: u -> z, v -> x, w -> y (u x v = w)
        return lambda u, v, w: (v, w, u)
    return lambda u, v, w: (u, v, w)


def cylinder(axis: str, center: Vec3, radius: float, length: float, segments: int = 24,
             caps: bool = True, smooth: bool = True, radius_end: float | None = None) -> Mesh:
    """Cylinder (or frustum when radius_end differs) along a principal axis."""
    to_world = _axis_frame(axis)
    r0, r1 = radius, (radius if radius_end is None else radius_end)
    h = length / 2.0
    m = Mesh()
    ring = [(math.cos(2 * math.pi * i / segments), math.sin(2 * math.pi * i / segments)) for i in range(segments)]
    c = center
    slope = (r0 - r1) / length if length else 0.0

    def P(u, v, w):
        p = to_world(u, v, w)
        return (p[0] + c[0], p[1] + c[1], p[2] + c[2])

    for i in range(segments):
        (ca, sa), (cb, sb) = ring[i], ring[(i + 1) % segments]
        a0, b0 = P(ca * r0, sa * r0, -h), P(cb * r0, sb * r0, -h)
        a1, b1 = P(ca * r1, sa * r1, h), P(cb * r1, sb * r1, h)
        if smooth:
            na = normalize(to_world(ca, sa, slope))
            nb = normalize(to_world(cb, sb, slope))
            m.add_smooth_triangle([a0, b0, b1], [na, nb, nb])
            m.add_smooth_triangle([a0, b1, a1], [na, nb, na])
        else:
            m.add_quad(a0, b0, b1, a1, outward_hint=to_world((ca + cb) / 2, (sa + sb) / 2, 0))
        if caps:
            m.add_triangle(P(0, 0, h), a1, b1) if dot(cross(sub(a1, P(0, 0, h)), sub(b1, P(0, 0, h))), to_world(0, 0, 1)) > 0 \
                else m.add_triangle(P(0, 0, h), b1, a1)
            m.add_triangle(P(0, 0, -h), b0, a0) if dot(cross(sub(b0, P(0, 0, -h)), sub(a0, P(0, 0, -h))), to_world(0, 0, -1)) > 0 \
                else m.add_triangle(P(0, 0, -h), a0, b0)
    return m


def torus(axis: str, center: Vec3, major: float, minor: float, segments: int = 24, sides: int = 8) -> Mesh:
    to_world = _axis_frame(axis)
    m = Mesh()

    def point(i, j):
        u = 2 * math.pi * i / segments
        v = 2 * math.pi * j / sides
        cu, su, cv, sv = math.cos(u), math.sin(u), math.cos(v), math.sin(v)
        p = to_world((major + minor * cv) * cu, (major + minor * cv) * su, minor * sv)
        n = normalize(to_world(cv * cu, cv * su, sv))
        return (p[0] + center[0], p[1] + center[1], p[2] + center[2]), n

    for i in range(segments):
        for j in range(sides):
            (a, na), (b, nb) = point(i, j), point(i + 1, j)
            (c, nc), (d, nd) = point(i + 1, j + 1), point(i, j + 1)
            m.add_smooth_triangle([a, b, c], [na, nb, nc])
            m.add_smooth_triangle([a, c, d], [na, nc, nd])
    return m


def uv_sphere(center: Vec3, radius: float, rings: int = 6, segments: int = 10) -> Mesh:
    m = Mesh()

    def point(r, s):
        theta = math.pi * r / rings
        phi = 2 * math.pi * s / segments
        n = (math.sin(theta) * math.cos(phi), math.cos(theta), math.sin(theta) * math.sin(phi))
        return (center[0] + radius * n[0], center[1] + radius * n[1], center[2] + radius * n[2]), n

    for r in range(rings):
        for s in range(segments):
            (a, na), (b, nb) = point(r, s), point(r + 1, s)
            (c, nc), (d, nd) = point(r + 1, s + 1), point(r, s + 1)
            if r != 0:
                m.add_smooth_triangle([a, d, c], [na, nd, nc])
            if r != rings - 1:
                m.add_smooth_triangle([a, c, b], [na, nc, nb])
    return m
