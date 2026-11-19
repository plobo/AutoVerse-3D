"""GLB (glTF 2.0 binary) and USDZ exporters for the procedural scene graph."""
from __future__ import annotations

import json
import struct
from pathlib import Path

from scene import Material, Node

GLTF_FLOAT = 5126
GLTF_UINT16 = 5123
GLTF_UINT32 = 5125
ARRAY_BUFFER = 34962
ELEMENT_ARRAY_BUFFER = 34963


def _pad4(data: bytearray, pad_byte: bytes = b"\x00") -> None:
    while len(data) % 4:
        data += pad_byte


def write_glb(root: Node, materials: list[Material], path: Path, generator: str) -> None:
    used = {n.material for n in root.walk() if n.material}
    mats = [m for m in materials if m.name in used]
    mat_index = {m.name: i for i, m in enumerate(mats)}

    buffer = bytearray()
    buffer_views: list[dict] = []
    accessors: list[dict] = []
    meshes: list[dict] = []
    nodes: list[dict] = []

    def add_view(data: bytes, target: int) -> int:
        _pad4(buffer)
        offset = len(buffer)
        buffer.extend(data)
        buffer_views.append({"buffer": 0, "byteOffset": offset, "byteLength": len(data), "target": target})
        return len(buffer_views) - 1

    def emit(node: Node) -> int:
        entry: dict = {"name": node.name}
        t = node.local_translation
        if any(abs(v) > 1e-9 for v in t):
            entry["translation"] = [round(v, 6) for v in t]
        lm = node.local_mesh()
        if lm is not None:
            pos = b"".join(struct.pack("<3f", *p) for p in lm.positions)
            nor = b"".join(struct.pack("<3f", *n) for n in lm.normals)
            big = len(lm.positions) > 65535
            idx = struct.pack(f"<{len(lm.indices)}{'I' if big else 'H'}", *lm.indices)
            lo, hi = lm.bounds()
            accessors.append({"bufferView": add_view(pos, ARRAY_BUFFER), "componentType": GLTF_FLOAT, "count": len(lm.positions),
                              "type": "VEC3", "min": [round(v, 6) for v in lo], "max": [round(v, 6) for v in hi]})
            pa = len(accessors) - 1
            accessors.append({"bufferView": add_view(nor, ARRAY_BUFFER), "componentType": GLTF_FLOAT, "count": len(lm.normals), "type": "VEC3"})
            na = len(accessors) - 1
            accessors.append({"bufferView": add_view(idx, ELEMENT_ARRAY_BUFFER), "componentType": GLTF_UINT32 if big else GLTF_UINT16,
                              "count": len(lm.indices), "type": "SCALAR"})
            ia = len(accessors) - 1
            meshes.append({"name": node.name, "primitives": [{"attributes": {"POSITION": pa, "NORMAL": na}, "indices": ia,
                                                              "material": mat_index[node.material], "mode": 4}]})
            entry["mesh"] = len(meshes) - 1
        nodes.append(entry)
        my_index = len(nodes) - 1
        if node.children:
            entry["children"] = [emit(c) for c in node.children]
        return my_index

    root_index = emit(root)

    gltf_materials = []
    for m in mats:
        r, g, b = m.base_linear
        entry = {"name": m.name, "pbrMetallicRoughness": {"baseColorFactor": [r, g, b, m.opacity], "metallicFactor": m.metallic,
                                                         "roughnessFactor": m.roughness}}
        if m.emissive_hex:
            entry["emissiveFactor"] = list(m.emissive_linear)
        if m.blended:
            entry["alphaMode"] = "BLEND"
        if m.double_sided:
            entry["doubleSided"] = True
        gltf_materials.append(entry)

    _pad4(buffer)
    gltf = {
        "asset": {"version": "2.0", "generator": generator, "copyright": "Original AutoVerse 3D asset. See ASSET_LICENSING.md"},
        "scene": 0,
        "scenes": [{"name": root.name, "nodes": [root_index]}],
        "nodes": nodes,
        "meshes": meshes,
        "materials": gltf_materials,
        "accessors": accessors,
        "bufferViews": buffer_views,
        "buffers": [{"byteLength": len(buffer)}],
    }
    json_bytes = bytearray(json.dumps(gltf, separators=(",", ":")).encode("utf-8"))
    _pad4(json_bytes, b" ")
    total = 12 + 8 + len(json_bytes) + 8 + len(buffer)
    with open(path, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, total))
        f.write(struct.pack("<II", len(json_bytes), 0x4E4F534A))
        f.write(json_bytes)
        f.write(struct.pack("<II", len(buffer), 0x004E4942))
        f.write(buffer)


def write_usdz(root: Node, materials: list[Material], usdz_path: Path, work_dir: Path) -> None:
    from pxr import Gf, Sdf, Usd, UsdGeom, UsdShade, Vt

    work_dir.mkdir(parents=True, exist_ok=True)
    usdc_path = work_dir / (usdz_path.stem + ".usdc")
    if usdc_path.exists():
        usdc_path.unlink()
    stage = Usd.Stage.CreateNew(str(usdc_path))
    UsdGeom.SetStageUpAxis(stage, UsdGeom.Tokens.y)
    UsdGeom.SetStageMetersPerUnit(stage, 1.0)
    stage.SetMetadata("customLayerData", {"copyright": "Original AutoVerse 3D asset. See ASSET_LICENSING.md"})

    root_path = Sdf.Path("/" + root.name)
    used = {n.material for n in root.walk() if n.material}
    shade: dict[str, UsdShade.Material] = {}
    UsdGeom.Scope.Define(stage, root_path.AppendChild("Materials"))
    for m in materials:
        if m.name not in used:
            continue
        mp = root_path.AppendChild("Materials").AppendChild(m.name)
        mat = UsdShade.Material.Define(stage, mp)
        shader = UsdShade.Shader.Define(stage, mp.AppendChild("PreviewSurface"))
        shader.CreateIdAttr("UsdPreviewSurface")
        shader.CreateInput("diffuseColor", Sdf.ValueTypeNames.Color3f).Set(Gf.Vec3f(*m.base_linear))
        shader.CreateInput("metallic", Sdf.ValueTypeNames.Float).Set(m.metallic)
        shader.CreateInput("roughness", Sdf.ValueTypeNames.Float).Set(m.roughness)
        shader.CreateInput("opacity", Sdf.ValueTypeNames.Float).Set(m.opacity)
        if m.emissive_hex:
            shader.CreateInput("emissiveColor", Sdf.ValueTypeNames.Color3f).Set(Gf.Vec3f(*m.emissive_linear))
        mat.CreateSurfaceOutput().ConnectToSource(shader.ConnectableAPI(), "surface")
        shade[m.name] = mat

    def emit(node: Node, path: Sdf.Path) -> None:
        lm = node.local_mesh()
        if lm is not None:
            mesh = UsdGeom.Mesh.Define(stage, path)
            mesh.CreatePointsAttr(Vt.Vec3fArray([Gf.Vec3f(*p) for p in lm.positions]))
            mesh.CreateNormalsAttr(Vt.Vec3fArray([Gf.Vec3f(*n) for n in lm.normals]))
            mesh.SetNormalsInterpolation(UsdGeom.Tokens.vertex)
            mesh.CreateFaceVertexCountsAttr(Vt.IntArray([3] * (len(lm.indices) // 3)))
            mesh.CreateFaceVertexIndicesAttr(Vt.IntArray(lm.indices))
            mesh.CreateSubdivisionSchemeAttr(UsdGeom.Tokens.none)
            mesh.CreateOrientationAttr(UsdGeom.Tokens.rightHanded)
            mat = next(m for m in materials if m.name == node.material)
            if mat.double_sided:
                mesh.CreateDoubleSidedAttr(True)
            lo, hi = lm.bounds()
            mesh.CreateExtentAttr(Vt.Vec3fArray([Gf.Vec3f(*lo), Gf.Vec3f(*hi)]))
            UsdShade.MaterialBindingAPI.Apply(mesh.GetPrim()).Bind(shade[node.material])
            xf = mesh
        else:
            xf = UsdGeom.Xform.Define(stage, path)
        t = node.local_translation
        if any(abs(v) > 1e-9 for v in t):
            xf.AddTranslateOp().Set(Gf.Vec3d(*t))
        for c in node.children:
            emit(c, path.AppendChild(c.name))

    emit(root, root_path)
    stage.SetDefaultPrim(stage.GetPrimAtPath(root_path))
    stage.GetRootLayer().Save()
    if usdz_path.exists():
        usdz_path.unlink()
    package_usdz_deterministic(usdc_path, usdz_path)


def package_usdz_deterministic(layer: Path, usdz_path: Path) -> None:
    """Writes a USDZ (uncompressed zip, 64-byte aligned payloads) with fixed timestamps so
    identical inputs always produce byte-identical packages (stable checksums)."""
    import zipfile

    data = layer.read_bytes()
    name = layer.name
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_STORED
    info.create_system = 3
    info.external_attr = 0o644 << 16
    header_len = 30 + len(name.encode("utf-8"))
    pad = (64 - (header_len + 4) % 64) % 64
    # extra field: id 0x1986 (Pixar padding), length = pad
    info.extra = struct.pack("<HH", 0x1986, pad) + b"\x00" * pad
    with zipfile.ZipFile(usdz_path, "w", compression=zipfile.ZIP_STORED) as zf:
        zf.writestr(info, data)
    with zipfile.ZipFile(usdz_path) as zf:
        zi = zf.infolist()[0]
        offset = zi.header_offset + 30 + len(zi.filename.encode("utf-8")) + len(zi.extra)
        if offset % 64:
            raise RuntimeError(f"USDZ payload not 64-byte aligned (offset {offset})")
