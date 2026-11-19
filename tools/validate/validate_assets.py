#!/usr/bin/env python3
"""AutoVerse 3D asset, manifest, content and licence-registry validator.

Run from the repo root:
    tools/.venv/bin/python tools/validate/validate_assets.py            # full check (USD parity needs usd-core)
    tools/.venv/bin/python tools/validate/validate_assets.py --release  # also require approved licence records

Exit code 0 = all checks passed. Every failure is printed with a stable code.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import sys
from dataclasses import dataclass, field
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# Words that must never appear in shipped names/content (manufacturer marks). Lower-case, whole words.
FORBIDDEN_MARKS = [
    "toyota", "honda", "ford", "bmw", "mercedes", "benz", "audi", "tesla", "porsche", "ferrari", "volkswagen", "vw",
    "chevrolet", "chevy", "nissan", "hyundai", "kia", "jeep", "land rover", "range rover", "lamborghini", "volvo",
    "subaru", "mazda", "lexus", "jaguar", "bugatti", "mclaren", "rivian", "lucid", "peugeot", "renault", "fiat",
    "skoda", "seat", "mini cooper", "dodge", "cadillac", "buick", "gmc", "acura", "infiniti", "maserati",
    "bentley", "rolls-royce", "aston martin", "alfa romeo", "polestar", "byd", "suzuki", "mitsubishi",
]
# "seat"/"mini" are common English words; only flag them in vehicle names, not prose.
PROSE_ALLOWED = {"seat"}


@dataclass
class Report:
    errors: list[str] = field(default_factory=list)
    checks: int = 0

    def check(self, condition: bool, code: str, message: str) -> bool:
        self.checks += 1
        if not condition:
            self.errors.append(f"[{code}] {message}")
        return condition


def sha256_file(path: Path) -> str:
    return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()


# --------------------------------------------------------------------------- GLB
def read_glb(path: Path) -> dict:
    data = path.read_bytes()
    if len(data) < 20:
        raise ValueError("file too small to be GLB")
    magic, version, length = struct.unpack_from("<III", data, 0)
    if magic != 0x46546C67 or version != 2:
        raise ValueError("not a glTF 2.0 binary")
    if length != len(data):
        raise ValueError(f"declared length {length} != actual {len(data)}")
    chunk_len, chunk_type = struct.unpack_from("<II", data, 12)
    if chunk_type != 0x4E4F534A:
        raise ValueError("first chunk is not JSON")
    return json.loads(data[20:20 + chunk_len].decode("utf-8"))


def glb_node_table(gltf: dict) -> dict[str, dict]:
    """name -> {parent, translation, world, mesh} computed from the scene graph (translation-only pivots)."""
    nodes = gltf.get("nodes", [])
    parent_of: dict[int, int] = {}
    for i, n in enumerate(nodes):
        for c in n.get("children", []):
            parent_of[c] = i
    table: dict[str, dict] = {}

    def world(i: int) -> list[float]:
        t = nodes[i].get("translation", [0, 0, 0])
        if i in parent_of:
            p = world(parent_of[i])
            return [t[0] + p[0], t[1] + p[1], t[2] + p[2]]
        return list(t)

    for i, n in enumerate(nodes):
        name = n.get("name", f"#{i}")
        table.setdefault(name, {"count": 0})
        table[name].update({"parent": nodes[parent_of[i]].get("name") if i in parent_of else None, "world": world(i),
                            "mesh": n.get("mesh"), "index": i})
        table[name]["count"] += 1
    return table


def glb_triangles(gltf: dict) -> int:
    total = 0
    for mesh in gltf.get("meshes", []):
        for prim in mesh.get("primitives", []):
            if "indices" in prim:
                total += gltf["accessors"][prim["indices"]]["count"] // 3
            else:
                total += gltf["accessors"][prim["attributes"]["POSITION"]]["count"] // 3
    return total


def glb_bounds(gltf: dict, table: dict[str, dict]) -> tuple[list[float], list[float]]:
    lo, hi = [1e9] * 3, [-1e9] * 3
    for name, info in table.items():
        if info.get("mesh") is None or name.startswith("Hotspot"):
            continue
        for prim in gltf["meshes"][info["mesh"]]["primitives"]:
            acc = gltf["accessors"][prim["attributes"]["POSITION"]]
            for k in range(3):
                lo[k] = min(lo[k], acc["min"][k] + info["world"][k])
                hi[k] = max(hi[k], acc["max"][k] + info["world"][k])
    return lo, hi


# --------------------------------------------------------------------------- checks
def validate_schemas(rep: Report) -> None:
    try:
        import jsonschema
    except ImportError:
        rep.check(False, "SCHEMA-DEP", "jsonschema not installed (pip install -r tools/requirements.txt)")
        return
    pairs = [
        ("shared/schemas/vehicle.schema.json", ["shared/data/vehicles.json"]),
        ("shared/schemas/component.schema.json", ["shared/data/components.json"]),
        ("shared/schemas/learning_module.schema.json", ["shared/content/learning_modules.json"]),
        ("shared/schemas/asset_registry.schema.json", ["shared/licensing/asset_registry.json"]),
        ("shared/schemas/asset_manifest.schema.json", [str(p.relative_to(REPO)) for p in sorted((REPO / "assets/manifests").glob("*.json"))]),
        ("shared/schemas/configuration.schema.json", ["shared/examples/configuration.example.json"]),
    ]
    for schema_path, docs in pairs:
        schema = json.loads((REPO / schema_path).read_text())
        for doc in docs:
            try:
                jsonschema.validate(json.loads((REPO / doc).read_text()), schema)
                rep.check(True, "SCHEMA", doc)
            except jsonschema.ValidationError as e:
                rep.check(False, "SCHEMA", f"{doc}: {e.message} at {list(e.absolute_path)}")


def validate_learning_content(rep: Report, learning: dict) -> None:
    ids = set()
    for m in learning["modules"]:
        rep.check(m["id"] not in ids, "CONTENT-DUP", f"duplicate module id {m['id']}")
        ids.add(m["id"])
        for q in m["quiz"]:
            rep.check(0 <= q["correctIndex"] < len(q["choices"]), "CONTENT-QUIZ", f"{q['id']}: correctIndex out of range")


def validate_manifest(rep: Report, manifest: dict, build_dir: Path) -> None:
    mid = manifest["id"]
    files = [(manifest["fileName"], manifest["fileSizeBytes"], manifest["checksum"])]
    for v in list(manifest.get("platformVariants", {}).values()) + [manifest["sceneDescriptor"]]:
        files.append((v["fileName"], v["fileSizeBytes"], v["checksum"]))
    for name, size, checksum in files:
        path = build_dir / name
        if not rep.check(path.exists(), "MANIFEST-FILE", f"{mid}: missing file {name}"):
            continue
        rep.check(path.stat().st_size == size, "MANIFEST-SIZE", f"{mid}: {name} size {path.stat().st_size} != manifest {size}")
        rep.check(sha256_file(path) == checksum, "CHECKSUM", f"{mid}: {name} checksum mismatch")


def validate_vehicle_glb(rep: Report, glb_path: Path, spec: dict, components: dict, hotspots: dict, custom: dict) -> dict[str, dict]:
    try:
        gltf = read_glb(glb_path)
    except Exception as e:  # malformed asset must not crash the validator
        rep.check(False, "GLB-PARSE", f"{glb_path.name}: {e}")
        return {}
    table = glb_node_table(gltf)
    dups = [n for n, info in table.items() if info["count"] > 1]
    rep.check(not dups, "NODE-UNIQUE", f"duplicate node names: {dups}")
    for name in spec["requiredNodes"]:
        rep.check(name in table, "NODE-REQUIRED", f"missing required node {name}")
    for child, parent in spec["requiredParents"].items():
        if child in table:
            rep.check(table[child]["parent"] == parent, "NODE-PARENT", f"{child} parent is {table[child]['parent']}, expected {parent}")
    tol = spec["pivotTolerance"]
    for name, expected in spec["pivots"].items():
        if name in table:
            w = table[name]["world"]
            rep.check(all(abs(w[i] - expected[i]) <= tol for i in range(3)), "PIVOT", f"{name} pivot {w} != {expected}")
    # metadata <-> model consistency
    for c in components["components"]:
        rep.check(c["nodeName"] in table, "META-NODE", f"component {c['id']} references missing node {c['nodeName']}")
    anim_ids = {a["id"] for a in components["animations"]} | {e["id"] for e in components["explodedViews"]}
    for a in components["animations"]:
        rep.check(a["nodeName"] in table, "ANIM-NODE", f"animation {a['id']} targets missing node {a['nodeName']}")
    for c in components["components"]:
        for aid in c["animationIds"]:
            rep.check(aid in anim_ids, "ANIM-ID", f"component {c['id']} references unknown animation {aid}")
    for ev in components["explodedViews"]:
        for o in ev["offsets"]:
            rep.check(o["nodeName"] in table, "EXPLODE-NODE", f"exploded view {ev['id']} targets missing node {o['nodeName']}")
    for mode, vis in components["modeVisibility"].items():
        for n in vis["hide"]:
            rep.check(n in table, "MODE-NODE", f"mode {mode} hides missing node {n}")
    for h in hotspots["hotspots"]:
        rep.check(f"Hotspot_{h['id']}" in table, "HOTSPOT-NODE", f"hotspot {h['id']} has no marker node")
    for wheel in ("FrontLeft", "FrontRight", "RearLeft", "RearRight"):
        for style in custom["wheels"]:
            rep.check(f"{wheel}_{style['nodeSuffix']}" in table, "WHEEL-STYLE", f"missing {wheel}_{style['nodeSuffix']}")
    # materials, textures and budgets
    mat_names = {m.get("name") for m in gltf.get("materials", [])}
    for req in spec["requiredMaterials"]:
        rep.check(req in mat_names, "MATERIAL", f"missing material {req}")
    budgets = spec["budgets"]
    rep.check(len(mat_names) <= budgets["maxMaterials"], "BUDGET-MATERIALS", f"{len(mat_names)} materials > {budgets['maxMaterials']}")
    tris = glb_triangles(gltf)
    rep.check(tris <= budgets["maxTriangles"], "BUDGET-TRIS", f"{tris} triangles > {budgets['maxTriangles']}")
    rep.check(glb_path.stat().st_size <= budgets["maxFileSizeBytes"], "BUDGET-SIZE", f"{glb_path.stat().st_size} B > {budgets['maxFileSizeBytes']}")
    for img in gltf.get("images", []):
        uri = img.get("uri")
        if uri and not uri.startswith("data:"):
            rep.check((glb_path.parent / uri).exists(), "TEXTURE-REF", f"texture {uri} not found")
    # dimensions
    lo, hi = glb_bounds(gltf, table)
    d = spec["dimensions"]
    t = d["tolerance"]
    rep.check(abs((hi[2] - lo[2]) - d["length"]) <= t, "DIM-LENGTH", f"length {hi[2]-lo[2]:.3f} vs {d['length']}")
    rep.check(abs((hi[0] - lo[0]) - d["width"]) <= t + 0.15, "DIM-WIDTH", f"width {hi[0]-lo[0]:.3f} vs {d['width']} (mirrors allowed)")
    rep.check(abs((hi[1] - max(lo[1], 0)) - d["height"]) <= t, "DIM-HEIGHT", f"height {hi[1]:.3f} vs {d['height']}")
    rep.check(abs(lo[1]) <= 0.01, "DIM-GROUND", f"lowest point {lo[1]:.3f} should sit on the ground plane (y=0)")
    return table


def validate_usd_parity(rep: Report, usdz_path: Path, glb_table: dict[str, dict]) -> None:
    try:
        from pxr import Usd
    except ImportError:
        print("note: usd-core not installed; skipping USDZ parity check")
        return
    stage = Usd.Stage.Open(str(usdz_path))
    if not rep.check(stage is not None, "USD-OPEN", f"cannot open {usdz_path.name}"):
        return
    names = set()
    for prim in stage.Traverse():
        path = str(prim.GetPath())
        if "/Materials" in path:
            continue
        names.add(prim.GetName())
    missing = sorted(set(glb_table) - names)
    extra = sorted(names - set(glb_table))
    rep.check(not missing and not extra, "USD-PARITY", f"USDZ/GLB node mismatch missing={missing[:8]} extra={extra[:8]}")
    rep.check(stage.GetMetadata("upAxis") == "Y", "USD-UPAXIS", "USDZ upAxis must be Y")


REQUIRED_RECORD_TEXT = ["title", "creator", "sourceUrl", "createdOrDownloadedDate", "restrictions"]


def validate_registry(rep: Report, registry: dict, manifests: list[dict], build_dir: Path, release: bool) -> None:
    by_id = {r["recordId"]: r for r in registry["records"]}
    covered: dict[str, dict] = {}
    for r in registry["records"]:
        for k in REQUIRED_RECORD_TEXT:
            rep.check(bool(str(r.get(k, "")).strip()), "LICENCE-FIELD", f"{r['recordId']}: '{k}' is empty")
        rep.check(bool(r["licence"]["name"].strip()) and bool(r["licence"]["termsReference"].strip()), "LICENCE-TERMS",
                  f"{r['recordId']}: licence name/terms reference required")
        if r["origin"] == "third_party":
            rep.check(r["sourceUrl"].startswith("https://"), "LICENCE-SOURCE", f"{r['recordId']}: third-party assets need an https source URL")
            if r["attribution"]["required"]:
                rep.check(bool(r["attribution"]["text"].strip()), "LICENCE-ATTRIB", f"{r['recordId']}: attribution text required")
        rep.check(r["commercialUse"], "LICENCE-COMMERCIAL", f"{r['recordId']}: commercial use not permitted — replace the asset")
        if r["status"] == "approved":
            rep.check(bool(r["reviewer"].strip()) and bool(r["approvalDate"]), "LICENCE-REVIEW", f"{r['recordId']}: approved without reviewer/date")
        if release:
            rep.check(r["status"] == "approved", "LICENCE-STATUS", f"{r['recordId']}: status {r['status']} (release requires approved)")
        for fname, checksum in r["checksums"].items():
            covered[fname] = r
            p = build_dir / fname
            if rep.check(p.exists(), "LICENCE-FILE", f"{r['recordId']}: listed file {fname} missing"):
                rep.check(sha256_file(p) == checksum, "LICENCE-CHECKSUM", f"{r['recordId']}: {fname} changed since review — re-review")
    for m in manifests:
        rep.check(m["licenceRecordId"] in by_id, "LICENCE-RECORD", f"manifest {m['id']} references unknown licence record {m['licenceRecordId']}")
    for p in sorted(build_dir.iterdir()):
        if p.is_file() and not p.name.startswith("."):
            rep.check(p.name in covered, "LICENCE-COVERAGE", f"asset file {p.name} has no licence record")


def validate_no_marks(rep: Report, texts: dict[str, str], strict_names: list[str]) -> None:
    for source, text in texts.items():
        lower = text.lower()
        for mark in FORBIDDEN_MARKS:
            if mark in PROSE_ALLOWED:
                continue
            if re.search(rf"\b{re.escape(mark)}\b", lower):
                rep.check(False, "TRADEMARK", f"'{mark}' found in {source}")
    for name in strict_names:
        for mark in FORBIDDEN_MARKS:
            if re.search(rf"\b{re.escape(mark)}\b", name.lower()):
                rep.check(False, "TRADEMARK-NAME", f"vehicle/asset name '{name}' contains '{mark}'")
    rep.checks += 1


def run(release: bool = False) -> Report:
    rep = Report()
    load = lambda p: json.loads((REPO / p).read_text())
    components, hotspots = load("shared/data/components.json"), load("shared/data/hotspots.json")
    custom, learning = load("shared/data/customization_options.json"), load("shared/content/learning_modules.json")
    vehicles, registry = load("shared/data/vehicles.json"), load("shared/licensing/asset_registry.json")
    spec = load("assets/source/urbanx_asset_spec.json")
    build_dir = REPO / "assets" / "build"
    manifests = [json.loads(p.read_text()) for p in sorted((REPO / "assets" / "manifests").glob("*.json"))]

    validate_schemas(rep)
    validate_learning_content(rep, learning)
    for m in manifests:
        validate_manifest(rep, m, build_dir)
    for v in vehicles["vehicles"]:
        if v["assetManifestId"]:
            rep.check(any(m["id"] == v["assetManifestId"] for m in manifests), "VEHICLE-MANIFEST", f"{v['id']}: manifest {v['assetManifestId']} missing")
    table = validate_vehicle_glb(rep, build_dir / "urbanx_suv_v1.glb", spec, components, hotspots, custom)
    if table:
        validate_usd_parity(rep, build_dir / "urbanx_suv_v1.usdz", table)
    validate_registry(rep, registry, manifests, build_dir, release)
    texts = {p: (REPO / p).read_text() for p in ["shared/data/vehicles.json", "shared/data/components.json", "shared/data/hotspots.json",
                                                 "shared/content/learning_modules.json", "shared/content/ev_lab.json"]}
    texts["urbanx node names"] = " ".join(table.keys())
    validate_no_marks(rep, texts, [v["name"] for v in vehicles["vehicles"]])
    return rep


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--release", action="store_true", help="require approved licence records")
    args = ap.parse_args()
    rep = run(args.release)
    for e in rep.errors:
        print("FAIL", e)
    print(f"{rep.checks} checks, {len(rep.errors)} failures")
    return 1 if rep.errors else 0


if __name__ == "__main__":
    sys.exit(main())
