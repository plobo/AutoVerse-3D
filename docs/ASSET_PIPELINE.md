# Asset pipeline

```
tools/assetgen/urbanx.py   ──►  build.py  ──►  assets/build/urbanx_suv_v1.glb          (Android · Filament)
  (procedural source of             │                 urbanx_suv_v1.usdz         (iOS · RealityKit)
   truth, original geometry)        │                 urbanx_suv_v1.scene.json   (node/material map, both apps)
shared/data/hotspots.json  ─────────┘          assets/manifests/urbanx_suv_v1.json (sizes, sha256, licence id)
                                                         │
                       tools/validate/validate_assets.py ┘  (CI gate)
```

**Why procedural instead of hand-modelled in Blender?** Blender is not installed in the build environment, and a scripted
source keeps the model reviewable, diff-able and reproducible (CI rebuilds it and fails on any byte difference). Both
platform formats are written from one in-memory scene graph, so node names, pivots and materials are identical by
construction (the validator also checks USDZ/GLB parity). An artist can later replace or refine the model in Blender as long
as the export keeps the [UrbanX specification](#urbanx-specification) below; the validator enforces it either way.

## Commands

```bash
python3 -m venv tools/.venv && tools/.venv/bin/pip install -r tools/requirements.txt
tools/.venv/bin/python tools/assetgen/build.py --update-registry   # rebuild + refresh registry checksums (resets review)
tools/.venv/bin/python tools/validate/validate_assets.py           # validate (add --release for release builds)
tools/.venv/bin/python -m unittest discover -s tools/tests         # pipeline tests
swiftc -O -o /tmp/preview tools/preview/render_preview.swift && /tmp/preview assets/build/urbanx_suv_v1.usdz out.png  # macOS preview render
```

USDZ packages are written with a deterministic zip writer (fixed timestamps, uncompressed, 64-byte aligned payloads), so
checksums are stable across rebuilds.

## Conventions

* Units metres; **+Y up, +Z vehicle front, +X vehicle left** (glTF convention). Origin on the ground between the axles.
* Every node has a unique name. Group nodes carry pivots; mesh nodes are named `<Parent>_<Part>` and use exactly one material.
* Moving parts are separate groups whose origin is the hinge: doors hinge at their front edge, the hood at its rear edge,
  the tailgate at the roof. Animations are data (`shared/data/components.json` → `animations`) applied as local rotations
  about these pivots, so iOS and Android animate identically and respect reduced-motion.
* Exploded views are per-node translation offsets (`explodedViews`), applied on top of part animations.
* Materials are shared by name; apps recolour `Paint`, `Accent` (trim), `InteriorFabric` and toggle emissive on
  `HeadlightLens` / `TaillightLens`. No textures in v1 (keeps the pack small; colour comes from PBR factors).
* Hotspot markers (`Hotspot_<id>`) are generated from `shared/data/hotspots.json` under `Hotspots_Interior` and `Hotspots_Safety`.
* Wheel styles ship as alternative rim meshes (`<Wheel>_Rim01..03`); the app shows one.

## UrbanX specification

Machine-readable form: [`assets/source/urbanx_asset_spec.json`](../assets/source/urbanx_asset_spec.json).

| Item | Value |
|---|---|
| Overall | L 4.30 m · W 1.82 m (excl. mirrors) · H 1.66 m · wheelbase 2.60 m · track 1.60 m |
| Wheels | radius 0.36 m, width 0.24 m, centres (±0.80, 0.36, ±1.30) |
| Pivots | FrontLeftDoor (0.88, 0.65, 0.90) · RearLeftDoor (0.88, 0.65, 0.04) · mirrored right · Hood (0, 0.97, 0.98) · Trunk (0, 1.53, −1.90) |
| Animations | doors ±65° about Y (outward) · hood −50° about X · tailgate +75° about X |
| Hierarchy | `VehicleRoot` → Body, Glass, Wheels{FrontLeft,FrontRight,RearLeft,RearRight}, Doors{4 doors}, Hood, Trunk, Lights, Interior, Chassis, Suspension, EngineOrMotor, WheelsAndBrakes, Hotspots |
| Mechanical components | EngineBlock, Transmission, DriveShafts, Radiator, Battery12V, ExhaustSystem, FrontBrakeAssembly, RearBrakeAssembly, FrontSuspension, RearSuspension, ChassisFrame, FuelTank |
| Budgets (mobile) | ≤ 50,000 triangles (v1: ~17,300) · ≤ 5 MB per file (v1: GLB 1.4 MB, USDZ 0.65 MB) · ≤ 40 materials · textures ≤ 2048 px |
| Design intent | Generic two-box compact SUV proportions; no grille signature, badge or lamp graphic resembling a production vehicle |

## Validation (automated, `validate_assets.py`)

Required files · manifest JSON-schema · file sizes + SHA-256 checksums · unique node names · required nodes and parents ·
pivot positions · animation/exploded/hotspot/component node references · wheel-style meshes · required materials ·
triangle/size/material budgets · texture references · overall dimensions and ground contact · USDZ↔GLB node parity and
up-axis · licence-registry completeness, coverage and checksum freshness · trademark scan of names and content.

## Levels of detail and performance

v1 is a single LOD at ~17k triangles, well under budget for all target tiers. When higher-detail packs are added, ship an
`_lod1` variant per manifest and select by device tier; keep transparent surfaces (glass) to the few meshes they are today.
