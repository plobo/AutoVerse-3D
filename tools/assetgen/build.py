#!/usr/bin/env python3
"""Builds the original AutoVerse 3D assets (UrbanX + studio platform).

Usage (from repo root):
    tools/.venv/bin/python tools/assetgen/build.py            # build GLB + USDZ + scene descriptors + manifests
    tools/.venv/bin/python tools/assetgen/build.py --update-registry
        # additionally refresh checksums in shared/licensing/asset_registry.json; any changed record is
        # reset to status "pending_review" so a human must re-approve it before release.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from exporters import write_glb, write_usdz  # noqa: E402
from scene import Node  # noqa: E402
from urbanx import MATERIALS, build_studio, build_vehicle  # noqa: E402

REPO = HERE.parent.parent
BUILD = REPO / "assets" / "build"
MANIFESTS = REPO / "assets" / "manifests"
REGISTRY = REPO / "shared" / "licensing" / "asset_registry.json"
GENERATOR = "AutoVerse3D assetgen 1.0 (procedural, original)"


def sha256(path: Path) -> str:
    return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()


def variant(path: Path, fmt: str) -> dict:
    return {"format": fmt, "fileName": path.name, "fileSizeBytes": path.stat().st_size, "checksum": sha256(path)}


def scene_descriptor(root: Node, asset_id: str) -> dict:
    nodes = []
    tris = 0
    for n in root.walk():
        entry = {"name": n.name, "parent": n.parent.name if n.parent else None, "kind": "mesh" if n.mesh else "group",
                 "pivot": [round(v, 4) for v in n.pivot]}
        if n.mesh:
            entry["material"] = n.material
            entry["triangles"] = n.mesh.triangle_count
            tris += n.mesh.triangle_count
        nodes.append(entry)
    used = sorted({n.material for n in root.walk() if n.material})
    mats = {m.name: {"baseColorHex": m.base_hex, "metallic": m.metallic, "roughness": m.roughness,
                     "emissiveHex": m.emissive_hex, "opacity": m.opacity} for m in MATERIALS if m.name in used}
    return {"schemaVersion": 1, "assetId": asset_id, "generator": GENERATOR, "units": "meters", "upAxis": "Y",
            "forwardAxis": "+Z", "leftAxis": "+X", "triangleCount": tris, "nodes": nodes, "materials": mats}


def build_one(root: Node, asset_id: str, vehicle_id: str | None, licence_record: str) -> dict:
    BUILD.mkdir(parents=True, exist_ok=True)
    glb = BUILD / f"{asset_id}.glb"
    usdz = BUILD / f"{asset_id}.usdz"
    scene_json = BUILD / f"{asset_id}.scene.json"
    write_glb(root, MATERIALS, glb, GENERATOR)
    write_usdz(root, MATERIALS, usdz, REPO / "assets" / ".work")
    desc = scene_descriptor(root, asset_id)
    scene_json.write_text(json.dumps(desc, indent=1) + "\n")
    version = int(asset_id.rsplit("_v", 1)[1])
    manifest = {
        "id": asset_id,
        "vehicleId": vehicle_id or "studio",
        "version": version,
        "format": "glb",
        "fileName": glb.name,
        "fileSizeBytes": glb.stat().st_size,
        "checksum": sha256(glb),
        "minimumAppVersion": "1.0.0",
        "licenceRecordId": licence_record,
        "platformVariants": {"ios": variant(usdz, "usdz")},
        "sceneDescriptor": variant(scene_json, "json"),
        "triangleCount": desc["triangleCount"],
        "schemaVersion": 1,
    }
    MANIFESTS.mkdir(parents=True, exist_ok=True)
    (MANIFESTS / f"{asset_id}.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"built {asset_id}: {desc['triangleCount']} triangles, glb {glb.stat().st_size} B, usdz {usdz.stat().st_size} B")
    return manifest


def update_registry(manifests: list[dict]) -> None:
    registry = json.loads(REGISTRY.read_text())
    for man in manifests:
        record = next((r for r in registry["records"] if r["recordId"] == man["licenceRecordId"]), None)
        if record is None:
            print(f"WARNING: no licence record {man['licenceRecordId']}; add one to ASSET_LICENSING.md + registry")
            continue
        new = {man["fileName"]: man["checksum"], man["platformVariants"]["ios"]["fileName"]: man["platformVariants"]["ios"]["checksum"],
               man["sceneDescriptor"]["fileName"]: man["sceneDescriptor"]["checksum"]}
        if record.get("checksums") != new:
            record["checksums"] = new
            record["files"] = sorted(new)
            record["status"] = "pending_review"
            record["approvalDate"] = None
            print(f"registry record {record['recordId']} changed -> pending_review (re-approve after review)")
    REGISTRY.write_text(json.dumps(registry, indent=2) + "\n")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--update-registry", action="store_true")
    args = ap.parse_args()
    manifests = [
        build_one(build_vehicle(REPO / "shared" / "data" / "hotspots.json"), "urbanx_suv_v1", "urbanx_suv", "asset_original_urbanx"),
        build_one(build_studio(), "studio_platform_v1", None, "asset_original_studio"),
    ]
    if args.update_registry:
        update_registry(manifests)


if __name__ == "__main__":
    main()
