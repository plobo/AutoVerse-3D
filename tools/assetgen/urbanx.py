"""UrbanX — an original, fictional compact SUV authored procedurally for AutoVerse 3D.

The design is deliberately generic (boxy two-box SUV proportions, simple lamps, no grille
signature) so it does not imitate the trade dress of any production vehicle.
Node names, pivots and materials follow docs/ASSET_PIPELINE.md.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

from mesh import Mesh, box_minmax, cylinder, hexahedron, tapered_block, torus, uv_sphere
from scene import Material, Node

WHEEL_RADIUS = 0.36
WHEEL_WIDTH = 0.24
TRACK_HALF = 0.80
AXLE_Z = 1.30

MATERIALS = [
    Material("Paint", "#E9ECEF", 0.1, 0.35),
    Material("Accent", "#1C1E21", 0.1, 0.6),
    Material("Trim", "#17191C", 0.0, 0.75),
    Material("Grille", "#0F1113", 0.2, 0.5),
    Material("Glass", "#1A252E", 0.0, 0.05, opacity=0.38, double_sided=True),
    Material("HeadlightLens", "#F5F7FA", 0.2, 0.1, emissive_hex="#FFF8E7"),
    Material("TaillightLens", "#A11616", 0.1, 0.2, emissive_hex="#D01818"),
    Material("Tire", "#151515", 0.0, 0.9),
    Material("Rim", "#C8CCD0", 0.95, 0.25),
    Material("RimDark", "#34373C", 0.8, 0.35),
    Material("Chrome", "#E0E3E6", 1.0, 0.12),
    Material("InteriorFabric", "#2B2D31", 0.0, 0.9),
    Material("InteriorDash", "#232528", 0.0, 0.7),
    Material("InteriorAccent", "#5A5F66", 0.5, 0.4),
    Material("Screen", "#0B1622", 0.3, 0.1, emissive_hex="#123A55"),
    Material("EngineMetal", "#6E7378", 0.8, 0.45),
    Material("EngineHead", "#A7ADB3", 0.9, 0.35),
    Material("EngineCover", "#0B7A75", 0.2, 0.4),
    Material("TransMetal", "#8B8F94", 0.8, 0.5),
    Material("Radiator", "#3B4046", 0.7, 0.5),
    Material("Battery", "#1A1C1F", 0.0, 0.6),
    Material("TerminalRed", "#C62828", 0.3, 0.4),
    Material("ChassisMetal", "#2E3135", 0.6, 0.6),
    Material("FuelTank", "#4D5257", 0.3, 0.6),
    Material("Exhaust", "#8A7F72", 0.9, 0.55),
    Material("Spring", "#E8B931", 0.3, 0.4),
    Material("BrakeDisc", "#9AA0A6", 1.0, 0.4),
    Material("Caliper", "#C62828", 0.3, 0.35),
    Material("Hotspot", "#F28C28", 0.0, 0.4, emissive_hex="#F28C28"),
    Material("StudioFloor", "#B9C0C7", 0.0, 0.8),
    Material("StudioRing", "#4FD1C5", 0.2, 0.4, emissive_hex="#1E6E68"),
]


def quad_slab(p0, p1, p2, p3, thickness: float, inward=(0.0, 0.0, 0.0)) -> Mesh:
    """A thin slab from a (roughly planar) quad, extruded by `thickness` toward `inward`."""
    ix, iy, iz = inward
    off = lambda p: (p[0] + ix * thickness, p[1] + iy * thickness, p[2] + iz * thickness)
    # hexahedron expects bottom ring then top ring; we treat p* as the outer ring.
    return hexahedron([off(p0), off(p1), off(p2), off(p3), p0, p1, p2, p3])


def both_sides(build_left) -> tuple[Mesh, Mesh]:
    left = build_left()
    return left, left.mirrored_x()


# --------------------------------------------------------------------------- body
def build_body(root: Node) -> None:
    body = root.group("Body")
    m = Mesh()
    # Front bumper (painted upper) + dark lower valance
    m.extend(tapered_block(1.92, 2.15, 0.45, 0.74, 0.88, 0.86, top_z1=2.10))
    # Front fenders: block ahead of the wheel arch and above the arch (both sides)
    for s in (1, -1):
        x0, x1 = sorted((s * 0.70, s * 0.90))
        m.extend(box_minmax((x0, 0.45, 1.72), (x1, 0.95, 1.95)))
        m.extend(box_minmax((x0, 0.80, 0.90), (x1, 0.95, 1.72)))
        # rear quarter: above rear arch and behind it
        m.extend(box_minmax((x0, 0.80, -1.72), (x1, 0.97, -0.88)))
        m.extend(box_minmax((x0, 0.45, -2.08), (x1, 0.97, -1.72)))
    # Cabin floor, rear cargo floor and engine-bay front panel
    m.extend(box_minmax((-0.86, 0.30, -0.90), (0.86, 0.40, 0.92)))
    m.extend(box_minmax((-0.86, 0.36, -2.06), (0.86, 0.45, -0.90)))
    # Rear bumper upper
    m.extend(tapered_block(-2.15, -1.95, 0.45, 0.66, 0.88, 0.87, top_z0=-2.12))
    # Pillars: A (slanted), B, D (slanted) — both sides
    for s in (1, -1):
        def pillar(z_bot, z_top, zw, s=s):
            b0, b1 = sorted((0.86 * s, 0.80 * s))
            t0, t1 = sorted((0.75 * s, 0.69 * s))
            return hexahedron([
                (b0, 0.96, z_bot - zw), (b1, 0.96, z_bot - zw), (b1, 0.96, z_bot + zw), (b0, 0.96, z_bot + zw),
                (t0, 1.53, z_top - zw), (t1, 1.53, z_top - zw), (t1, 1.53, z_top + zw), (t0, 1.53, z_top + zw),
            ])
        m.extend(pillar(0.94, 0.40, 0.05))   # A-pillar
        m.extend(pillar(0.05, 0.05, 0.05))   # B-pillar
        m.extend(pillar(-1.95, -1.80, 0.07))  # D-pillar
    # Roof panel
    m.extend(box_minmax((-0.76, 1.52, -1.86), (0.76, 1.58, 0.42)))
    body.part("Shell", m, "Paint")

    # Dark plastic: lower valances, wheel-arch cladding, sills
    t = Mesh()
    t.extend(box_minmax((-0.86, 0.28, 1.90), (0.86, 0.45, 2.12)))
    t.extend(box_minmax((-0.86, 0.28, -2.12), (0.86, 0.45, -1.95)))
    for s in (1, -1):
        for zc in (AXLE_Z, -AXLE_Z):
            for k in range(8):
                a0, a1 = math.pi * k / 8, math.pi * (k + 1) / 8
                r0, r1 = 0.42, 0.48
                pts = []
                for (a, r) in ((a0, r0), (a1, r0), (a1, r1), (a0, r1)):
                    pts.append((zc + math.cos(a) * r, WHEEL_RADIUS + math.sin(a) * r))
                xa, xb = sorted((s * 0.88, s * 0.935))
                ring = [(xa, y, z) for (z, y) in pts]
                ring_o = [(xb, y, z) for (z, y) in pts]
                t.extend(hexahedron([ring[0], ring[1], ring_o[1], ring_o[0], ring[3], ring[2], ring_o[2], ring_o[3]]))
        xa, xb = sorted((s * 0.84, s * 0.91))
        t.extend(box_minmax((xa, 0.28, -0.88), (xb, 0.37, 0.90)))
    body.part("Cladding", t, "Trim")

    g = Mesh()
    g.extend(box_minmax((-0.50, 0.50, 2.06), (0.50, 0.70, 2.13)))  # lower air intake
    g.extend(box_minmax((-0.84, 0.40, 0.92), (0.84, 0.96, 0.98)))  # firewall
    g.extend(box_minmax((-0.86, 0.74, 1.95), (0.86, 0.89, 2.07)))  # front fascia behind the lamps
    g.extend(box_minmax((-0.86, 0.66, -2.09), (0.86, 0.80, -1.97)))  # rear fascia under the tail lamps
    body.part("Grille", g, "Grille")

    a = Mesh()
    a.extend(box_minmax((-0.40, 0.29, 2.12), (0.40, 0.34, 2.16)))  # front skid plate
    a.extend(box_minmax((-0.40, 0.29, -2.16), (0.40, 0.34, -2.12)))  # rear skid plate
    for s in (1, -1):
        xa, xb = sorted((s * 0.60, s * 0.64))
        a.extend(box_minmax((xa, 1.58, -1.70), (xb, 1.63, 0.25)))  # roof rails
    body.part("AccentTrim", a, "Accent")


def build_glass(root: Node) -> None:
    glass = root.group("Glass")
    m = Mesh()
    # Windshield
    m.extend(quad_slab((-0.80, 0.97, 0.93), (0.80, 0.97, 0.93), (0.71, 1.52, 0.40), (-0.71, 1.52, 0.40), 0.01, (0, 0, -1)))
    # Rear quarter windows (fixed)
    for s in (1, -1):
        x0, x1 = 0.865 * s, 0.76 * s
        m.extend(quad_slab((x0, 0.98, -0.90), (x0, 0.98, -1.88), (x1, 1.50, -1.76), (x1, 1.50, -0.90), 0.01, (-s, 0, 0)))
    glass.part("Fixed", m, "Glass")


def build_doors(root: Node) -> None:
    doors = root.group("Doors")
    specs = [("FrontLeftDoor", 1, 0.90, 0.06, True), ("RearLeftDoor", 1, 0.04, -0.87, False),
             ("FrontRightDoor", -1, 0.90, 0.06, True), ("RearRightDoor", -1, 0.04, -0.87, False)]
    for name, s, z_front, z_rear, is_front in specs:
        hinge = (0.88 * s, 0.65, z_front)
        door = doors.group(name, hinge)
        xa, xb = sorted((0.85 * s, 0.91 * s))
        panel = box_minmax((xa, 0.37, z_rear), (xb, 0.97, z_front - 0.01))
        door.part("Panel", panel, "Paint")
        x0, x1 = 0.88 * s, 0.77 * s
        if is_front:
            win = quad_slab((x0, 0.98, z_front - 0.05), (x0, 0.98, z_rear + 0.02), (x1, 1.50, z_rear + 0.02), (x1, 1.50, 0.42), 0.01, (-s, 0, 0))
        else:
            win = quad_slab((x0, 0.98, z_front - 0.02), (x0, 0.98, z_rear + 0.03), (x1, 1.50, z_rear + 0.05), (x1, 1.50, z_front - 0.02), 0.01, (-s, 0, 0))
        door.part("Window", win, "Glass")
        h = Mesh()
        hx0, hx1 = sorted((0.91 * s, 0.93 * s))
        h.extend(box_minmax((hx0, 0.84, z_rear + 0.12), (hx1, 0.875, z_rear + 0.26)))  # handle
        h.extend(box_minmax((hx0, 0.955, z_rear), (hx1, 0.975, z_front - 0.01)))  # beltline strip
        if is_front:
            mx0, mx1 = sorted((0.90 * s, 1.02 * s))
            h.extend(box_minmax((mx0, 0.99, z_front - 0.14), (mx1, 1.09, z_front - 0.04)))  # mirror cap
        door.part("Trim", h, "Accent")
        c = Mesh()
        cx0, cx1 = sorted((0.905 * s, 0.925 * s))
        c.extend(box_minmax((cx0, 0.37, z_rear), (cx1, 0.47, z_front - 0.01)))
        door.part("Cladding", c, "Trim")


def build_hood(root: Node) -> None:
    hood = root.group("Hood", (0.0, 0.97, 0.98))
    hood.part("Panel", hexahedron([
        (-0.88, 0.935, 0.98), (0.88, 0.935, 0.98), (0.88, 0.89, 2.10), (-0.88, 0.89, 2.10),
        (-0.88, 0.975, 0.98), (0.88, 0.975, 0.98), (0.86, 0.93, 2.08), (-0.86, 0.93, 2.08),
    ]), "Paint")


def build_trunk(root: Node) -> None:
    trunk = root.group("Trunk", (0.0, 1.53, -1.90))
    p = Mesh()
    p.extend(hexahedron([
        (-0.84, 0.45, -2.10), (0.84, 0.45, -2.10), (0.84, 0.45, -2.04), (-0.84, 0.45, -2.04),
        (-0.82, 1.00, -2.08), (0.82, 1.00, -2.08), (0.82, 1.00, -2.02), (-0.82, 1.00, -2.02),
    ]))
    p.extend(box_minmax((-0.76, 1.48, -1.98), (0.76, 1.57, -1.86)))  # spoiler lip
    trunk.part("Panel", p, "Paint")
    trunk.part("Window", quad_slab((-0.80, 1.00, -2.06), (0.80, 1.00, -2.06), (0.72, 1.48, -1.95), (-0.72, 1.48, -1.95), 0.01, (0, 0, 1)), "Glass")


def build_lights(root: Node) -> None:
    lights = root.group("Lights")
    f = Mesh()
    r = Mesh()
    for s in (1, -1):
        xa, xb = sorted((0.48 * s, 0.82 * s))
        f.extend(hexahedron([(xa, 0.76, 2.06), (xb, 0.76, 2.06), (xb, 0.76, 2.11), (xa, 0.76, 2.11),
                             (xa, 0.86, 2.04), (xb, 0.86, 2.04), (xb, 0.86, 2.085), (xa, 0.86, 2.085)]))
        ta, tb = sorted((0.56 * s, 0.88 * s))
        r.extend(box_minmax((ta, 0.80, -2.13), (tb, 0.94, -2.07)))
    f.extend(box_minmax((-0.46, 0.83, 2.08), (0.46, 0.845, 2.105)))  # light bar
    r.extend(box_minmax((-0.25, 1.50, -1.995), (0.25, 1.52, -1.975)))  # high brake light
    lights.part("Headlights", f, "HeadlightLens")
    lights.part("Taillights", r, "TaillightLens")


# --------------------------------------------------------------------------- interior
def build_interior(root: Node) -> None:
    interior = root.group("Interior")
    dash = interior.group("Dashboard")
    dash.part("Panel", tapered_block(0.78, 0.98, 0.70, 0.98, 0.82, 0.80, top_z0=0.84), "InteriorDash")
    dash.part("Cluster", box_minmax((0.24, 0.97, 0.80), (0.52, 1.03, 0.86)), "Screen")
    disp = interior.group("InfotainmentDisplay")
    disp.part("Screen", box_minmax((-0.18, 0.99, 0.86), (0.18, 1.13, 0.885)).rotated("x", 12, (0, 0.99, 0.87)), "Screen")

    wheel = interior.group("SteeringWheel", (0.38, 0.98, 0.72))
    w = torus("z", (0.38, 0.98, 0.72), 0.17, 0.018, 24, 6)
    w.extend(box_minmax((0.33, 0.95, 0.715), (0.43, 1.01, 0.745)))
    w.extend(box_minmax((0.215, 0.97, 0.725), (0.545, 0.99, 0.735)))
    w = w.rotated("x", 20, (0.38, 0.98, 0.72))
    w.extend(cylinder("z", (0.38, 0.95, 0.80), 0.03, 0.16, 10))
    wheel.part("Rim", w, "InteriorAccent")

    seats = interior.group("FrontSeats")
    for name, xc in (("Driver", 0.38), ("Passenger", -0.38)):
        s = Mesh()
        s.extend(box_minmax((xc - 0.24, 0.48, -0.05), (xc + 0.24, 0.60, 0.45)))
        back = box_minmax((xc - 0.24, 0.60, -0.14), (xc + 0.24, 1.15, -0.03))
        back.extend(box_minmax((xc - 0.12, 1.17, -0.13), (xc + 0.12, 1.32, -0.05)))
        s.extend(back.rotated("x", -12, (0, 0.60, -0.08)))
        seats.part(name, s, "InteriorFabric")
        seats.part(f"{name}Base", box_minmax((xc - 0.18, 0.40, 0.0), (xc + 0.18, 0.48, 0.40)), "Trim")

    rear = interior.group("RearSeats")
    rb = Mesh()
    rb.extend(box_minmax((-0.74, 0.46, -1.15), (0.74, 0.58, -0.66)))
    back = box_minmax((-0.74, 0.58, -1.24), (0.74, 1.10, -1.13))
    for xc in (-0.48, 0.0, 0.48):
        back.extend(box_minmax((xc - 0.11, 1.12, -1.22), (xc + 0.11, 1.26, -1.15)))
    rb.extend(back.rotated("x", -10, (0, 0.58, -1.18)))
    rear.part("Bench", rb, "InteriorFabric")

    console = interior.group("CenterConsole")
    console.part("Body", box_minmax((-0.12, 0.40, -0.05), (0.12, 0.66, 0.80)), "InteriorDash")
    console.part("Armrest", box_minmax((-0.12, 0.66, -0.05), (0.12, 0.71, 0.25)), "InteriorFabric")
    console.part("Selector", cylinder("y", (0.0, 0.70, 0.55), 0.025, 0.08, 10), "Chrome")

    glove = interior.group("GloveBox")
    glove.part("Lid", box_minmax((-0.62, 0.74, 0.775), (-0.28, 0.87, 0.80)), "InteriorAccent")
    cargo = interior.group("CargoArea")
    cargo.part("Floor", box_minmax((-0.80, 0.45, -2.02), (0.80, 0.47, -1.26)), "InteriorDash")


# --------------------------------------------------------------------------- mechanical
def build_chassis(root: Node) -> None:
    chassis = root.group("Chassis")
    frame = chassis.group("ChassisFrame")
    f = Mesh()
    for s in (1, -1):
        xa, xb = sorted((0.50 * s, 0.60 * s))
        f.extend(box_minmax((xa, 0.24, -2.0), (xb, 0.34, 2.0)))
    for z in (1.85, 0.95, -0.35, -1.75):
        f.extend(box_minmax((-0.60, 0.25, z - 0.04), (0.60, 0.32, z + 0.04)))
    frame.part("Rails", f, "ChassisMetal")
    tank = chassis.group("FuelTank")
    tank.part("Body", box_minmax((-0.45, 0.22, -1.15), (0.25, 0.38, -0.55)), "FuelTank")
    tank.part("Filler", cylinder("x", (0.55, 0.62, -1.4), 0.03, 0.6, 8, radius_end=0.035).rotated("z", 30, (0.55, 0.62, -1.4)), "Trim")


def build_powertrain(root: Node) -> None:
    pt = root.group("EngineOrMotor")
    eng = pt.group("EngineBlock", (0.0, 0.65, 1.45))
    eng.part("Block", box_minmax((-0.30, 0.42, 1.25), (0.22, 0.78, 1.68)), "EngineMetal")
    eng.part("Head", box_minmax((-0.28, 0.78, 1.27), (0.20, 0.86, 1.64)), "EngineHead")
    eng.part("Cover", box_minmax((-0.26, 0.86, 1.30), (0.18, 0.90, 1.60)), "EngineCover")
    coils = Mesh()
    for x in (-0.20, -0.09, 0.02, 0.13):
        coils.extend(cylinder("y", (x, 0.925, 1.45), 0.022, 0.05, 8))
    eng.part("Coils", coils, "Trim")
    eng.part("Intake", box_minmax((-0.25, 0.60, 1.68), (0.18, 0.84, 1.77)), "EngineHead")
    eng.part("OilPan", tapered_block(1.28, 1.64, 0.36, 0.42, 0.20, 0.25), "ChassisMetal")
    eng.part("Alternator", cylinder("x", (0.27, 0.66, 1.62), 0.06, 0.09, 14), "EngineHead")
    eng.part("AirBox", box_minmax((0.30, 0.72, 1.20), (0.62, 0.86, 1.45)), "Trim")

    trans = pt.group("Transmission", (-0.46, 0.59, 1.47))
    trans.part("Case", box_minmax((-0.62, 0.44, 1.28), (-0.34, 0.74, 1.66)), "TransMetal")
    trans.part("Bellhousing", cylinder("x", (-0.32, 0.58, 1.47), 0.17, 0.06, 18), "TransMetal")

    shafts = pt.group("DriveShafts", (0.0, 0.36, AXLE_Z))
    d = Mesh()
    d.extend(cylinder("x", (0.20, 0.36, AXLE_Z), 0.025, 1.0, 10))
    d.extend(cylinder("x", (-0.575, 0.36, AXLE_Z), 0.025, 0.25, 10))
    shafts.part("Shafts", d, "ChassisMetal")
    boots = Mesh()
    for x in (-0.68, -0.47, -0.28, 0.66):
        boots.extend(cylinder("x", (x, 0.36, AXLE_Z), 0.045, 0.07, 10, radius_end=0.03))
    shafts.part("Boots", boots, "Tire")

    rad = pt.group("Radiator", (0.0, 0.65, 1.93))
    rad.part("Core", box_minmax((-0.58, 0.42, 1.90), (0.58, 0.88, 1.96)), "Radiator")
    rad.part("Fan", cylinder("z", (0.0, 0.65, 1.86), 0.19, 0.05, 20), "Trim")
    hoses = Mesh()
    hoses.extend(cylinder("z", (-0.18, 0.80, 1.82), 0.025, 0.16, 8))
    hoses.extend(cylinder("z", (0.10, 0.50, 1.79), 0.025, 0.22, 8))
    rad.part("Hoses", hoses, "Tire")

    bat = pt.group("Battery12V", (0.46, 0.66, 1.66))
    bat.part("Case", box_minmax((0.32, 0.56, 1.52), (0.60, 0.76, 1.80)), "Battery")
    bat.part("Positive", cylinder("y", (0.38, 0.78, 1.57), 0.018, 0.04, 8), "TerminalRed")
    bat.part("Negative", cylinder("y", (0.54, 0.78, 1.57), 0.018, 0.04, 8), "Trim")
    bat.part("Label", box_minmax((0.34, 0.65, 1.80), (0.58, 0.72, 1.805)), "EngineCover")

    ex = pt.group("ExhaustSystem", (0.30, 0.27, 0.0))
    e = Mesh()
    e.extend(cylinder("y", (0.0, 0.38, 1.20), 0.035, 0.20, 10))
    e.extend(cylinder("x", (0.19, 0.27, 1.20), 0.035, 0.40, 10))
    e.extend(cylinder("z", (0.38, 0.27, -0.20), 0.035, 2.80, 10))
    e.extend(cylinder("z", (0.30, 0.26, -2.02), 0.04, 0.16, 10))
    ex.part("Pipes", e, "Exhaust")
    ex.part("Catalyst", cylinder("z", (0.38, 0.27, 0.80), 0.08, 0.32, 14), "Exhaust")
    ex.part("Muffler", box_minmax((-0.05, 0.20, -1.94), (0.45, 0.36, -1.60)), "Exhaust")


def strut(x: float, z: float) -> tuple[Mesh, Mesh, Mesh]:
    damper = cylinder("y", (x, 0.66, z), 0.028, 0.56, 10)
    damper.extend(cylinder("y", (x, 0.965, z), 0.08, 0.03, 14))
    spring = Mesh()
    for i in range(6):
        spring.extend(torus("y", (x, 0.55 + i * 0.065, z), 0.07, 0.011, 14, 5))
    s = 1 if x > 0 else -1
    xa, xb = sorted((0.50 * s, 0.72 * s))
    arm = box_minmax((xa, 0.32, z - 0.10), (xb, 0.36, z + 0.10))
    return damper, spring, arm


def build_suspension(root: Node) -> None:
    sus = root.group("Suspension")
    for axle_name, z in (("FrontSuspension", AXLE_Z), ("RearSuspension", -AXLE_Z)):
        axle = sus.group(axle_name)
        prefix = "Front" if z > 0 else "Rear"
        for side, s in (("Left", 1), ("Right", -1)):
            node = axle.group(f"{prefix}{side}Strut", (0.60 * s, 0.66, z))
            damper, spring, arm = strut(0.60 * s, z - 0.02 * (1 if z > 0 else -1))
            node.part("Damper", damper, "Chrome")
            node.part("Spring", spring, "Spring")
            node.part("Arm", arm, "ChassisMetal")


def build_brakes(root: Node) -> None:
    wb = root.group("WheelsAndBrakes")
    for axle_name, z, disc_r in (("FrontBrakeAssembly", AXLE_Z, 0.16), ("RearBrakeAssembly", -AXLE_Z, 0.14)):
        axle = wb.group(axle_name)
        prefix = "Front" if z > 0 else "Rear"
        for side, s in (("Left", 1), ("Right", -1)):
            node = axle.group(f"{prefix}{side}Brake", (0.72 * s, WHEEL_RADIUS, z))
            node.part("Disc", cylinder("x", (0.73 * s, WHEEL_RADIUS, z), disc_r, 0.026, 22), "BrakeDisc")
            node.part("Hub", cylinder("x", (0.71 * s, WHEEL_RADIUS, z), 0.06, 0.05, 12), "ChassisMetal")
            xa, xb = sorted((0.69 * s, 0.765 * s))
            node.part("Caliper", box_minmax((xa, WHEEL_RADIUS + 0.06, z - 0.16), (xb, WHEEL_RADIUS + 0.15, z - 0.04)), "Caliper")


# --------------------------------------------------------------------------- wheels
def wheel_left_local() -> dict[str, tuple[Mesh, str]]:
    """Wheel authored at the origin with its outer face toward +X (vehicle-left side)."""
    r, w = WHEEL_RADIUS, WHEEL_WIDTH
    tire = cylinder("x", (0, 0, 0), r, w, 32, caps=False)
    inner = cylinder("x", (0, 0, 0), 0.245, w, 32, caps=False)
    tire.extend(_invert(inner))
    for side in (1, -1):
        for i in range(32):
            a0, a1 = 2 * math.pi * i / 32, 2 * math.pi * (i + 1) / 32
            x = side * w / 2
            p = [(x, math.cos(a) * rr, math.sin(a) * rr) for a, rr in ((a0, 0.245), (a1, 0.245), (a1, r), (a0, r))]
            tire.add_quad(*p, outward_hint=(side, 0, 0))
    parts = {"Tire": (tire, "Tire")}

    def ring_and_hub(mat: str) -> Mesh:
        m = torus("x", (0.085, 0, 0), 0.228, 0.02, 32, 6)
        m.extend(cylinder("x", (0.085, 0, 0), 0.065, 0.05, 14))
        return m

    rim1 = ring_and_hub("Rim")
    for k in range(5):
        rim1.extend(box_minmax((0.07, 0.05, -0.04), (0.10, 0.225, 0.04)).rotated("x", 72 * k))
    parts["Rim01"] = (rim1, "Rim")
    rim2 = ring_and_hub("Rim")
    for k in range(10):
        rim2.extend(box_minmax((0.075, 0.05, -0.012), (0.095, 0.225, 0.012)).rotated("x", 36 * k + 9))
    parts["Rim02"] = (rim2, "Rim")
    rim3 = ring_and_hub("RimDark")
    rim3.extend(cylinder("x", (0.07, 0, 0), 0.215, 0.015, 32))
    for k in range(8):
        rim3.extend(box_minmax((0.078, 0.07, -0.025), (0.098, 0.21, 0.025)).rotated("x", 45 * k + 20))
    parts["Rim03"] = (rim3, "RimDark")
    return parts


def _invert(m: Mesh) -> Mesh:
    idx: list[int] = []
    for i in range(0, len(m.indices), 3):
        a, b, c = m.indices[i:i + 3]
        idx += [a, c, b]
    return Mesh(list(m.positions), [(-n[0], -n[1], -n[2]) for n in m.normals], idx)


def build_wheels(root: Node) -> None:
    wheels = root.group("Wheels")
    local = wheel_left_local()
    for name, s, z in (("FrontLeft", 1, AXLE_Z), ("FrontRight", -1, AXLE_Z), ("RearLeft", 1, -AXLE_Z), ("RearRight", -1, -AXLE_Z)):
        center = (TRACK_HALF * s, WHEEL_RADIUS, z)
        node = wheels.group(name, center)
        for suffix, (mesh, mat) in local.items():
            m = mesh if s > 0 else mesh.mirrored_x()
            node.part(suffix, m.translated(center), mat)


# --------------------------------------------------------------------------- hotspots
def build_hotspots(root: Node, hotspots_path: Path) -> None:
    data = json.loads(hotspots_path.read_text())
    hs = root.group("Hotspots")
    groups = {"interior": hs.group("Hotspots_Interior"), "safety": hs.group("Hotspots_Safety")}
    for h in data["hotspots"]:
        pos = tuple(h["position"])
        node = groups[h["module"]].group(f"Hotspot_{h['id']}", pos)
        node.part("Marker", uv_sphere(pos, 0.045, 6, 12), "Hotspot")


def build_vehicle(hotspots_path: Path) -> Node:
    root = Node("VehicleRoot")
    build_body(root)
    build_glass(root)
    build_wheels(root)
    build_doors(root)
    build_hood(root)
    build_trunk(root)
    build_lights(root)
    build_interior(root)
    build_chassis(root)
    build_suspension(root)
    build_powertrain(root)
    build_brakes(root)
    build_hotspots(root, hotspots_path)
    return root


def build_studio() -> Node:
    root = Node("StudioRoot")
    floor = root.group("StudioPlatform")
    floor.part("Disc", cylinder("y", (0, -0.03, 0), 3.4, 0.06, 64), "StudioFloor")
    floor.part("Ring", torus("y", (0, 0.0, 0), 3.38, 0.025, 96, 6), "StudioRing")
    return root
