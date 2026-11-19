"""Unit tests for the asset generator and validator. Run: tools/.venv/bin/python -m unittest discover -s tools/tests"""
from __future__ import annotations

import copy
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "validate"))
sys.path.insert(0, str(REPO / "tools" / "assetgen"))

import validate_assets as va  # noqa: E402
from mesh import box, cylinder, Mesh  # noqa: E402
from scene import Node, hex_to_linear  # noqa: E402


def load(p: str) -> dict:
    return json.loads((REPO / p).read_text())


class MeshTests(unittest.TestCase):
    def test_box_has_12_outward_triangles(self):
        m = box((0, 0, 0), (2, 2, 2))
        self.assertEqual(m.triangle_count, 12)
        for i in range(0, len(m.indices), 3):
            a = m.positions[m.indices[i]]
            n = m.normals[m.indices[i]]
            centroid = [sum(m.positions[m.indices[i + k]][j] for k in range(3)) / 3 for j in range(3)]
            self.assertGreater(sum(centroid[j] * n[j] for j in range(3)), 0, "face normal must point outward")
            self.assertIsNotNone(a)

    def test_mirror_keeps_faces_outward(self):
        m = box((1, 0, 0), (1, 1, 1)).mirrored_x()
        lo, hi = m.bounds()
        self.assertAlmostEqual(lo[0], -1.5)
        self.assertAlmostEqual(hi[0], -0.5)
        for i in range(0, len(m.indices), 3):
            p = [m.positions[m.indices[i + k]] for k in range(3)]
            e1 = [p[1][j] - p[0][j] for j in range(3)]
            e2 = [p[2][j] - p[0][j] for j in range(3)]
            cr = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
            n = m.normals[m.indices[i]]
            self.assertGreater(sum(cr[j] * n[j] for j in range(3)), 0, "winding must agree with normal after mirroring")

    def test_cylinder_triangle_count(self):
        self.assertEqual(cylinder("x", (0, 0, 0), 1, 1, 8).triangle_count, 8 * 4)

    def test_local_mesh_is_relative_to_pivot(self):
        root = Node("Root")
        door = root.group("Door", (1.0, 0.0, 0.0))
        part = door.part("Panel", box((1.5, 0, 0), (1, 1, 1)), "Paint")
        self.assertEqual(part.local_translation, (0.0, 0.0, 0.0))
        self.assertEqual(door.local_translation, (1.0, 0.0, 0.0))
        lo, _ = part.local_mesh().bounds()
        self.assertAlmostEqual(lo[0], 0.0)

    def test_srgb_to_linear(self):
        self.assertEqual(hex_to_linear("#FFFFFF"), (1.0, 1.0, 1.0))
        self.assertEqual(hex_to_linear("#000000"), (0.0, 0.0, 0.0))
        self.assertAlmostEqual(hex_to_linear("#808080")[0], 0.21586, places=4)


class ValidatorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.components = load("shared/data/components.json")
        cls.hotspots = load("shared/data/hotspots.json")
        cls.custom = load("shared/data/customization_options.json")
        cls.spec = load("assets/source/urbanx_asset_spec.json")
        cls.registry = load("shared/licensing/asset_registry.json")
        cls.manifests = [json.loads(p.read_text()) for p in sorted((REPO / "assets/manifests").glob("*.json"))]
        cls.glb = REPO / "assets/build/urbanx_suv_v1.glb"

    def test_full_repository_validation_passes(self):
        rep = va.run(release=False)
        self.assertEqual(rep.errors, [])
        self.assertGreater(rep.checks, 200)

    def test_committed_glb_matches_spec(self):
        rep = va.Report()
        table = va.validate_vehicle_glb(rep, self.glb, self.spec, self.components, self.hotspots, self.custom)
        self.assertEqual(rep.errors, [])
        self.assertIn("FrontLeftDoor", table)

    def test_detects_checksum_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            build = Path(tmp)
            for f in (REPO / "assets/build").iterdir():
                shutil.copy(f, build / f.name)
            with open(build / "urbanx_suv_v1.glb", "ab") as fh:
                fh.write(b"tamper")
            rep = va.Report()
            va.validate_manifest(rep, next(m for m in self.manifests if m["id"] == "urbanx_suv_v1"), build)
            codes = {e.split("]")[0].strip("[") for e in rep.errors}
            self.assertIn("CHECKSUM", codes)
            self.assertIn("MANIFEST-SIZE", codes)

    def test_detects_missing_pivot_and_metadata_nodes(self):
        spec = copy.deepcopy(self.spec)
        spec["pivots"]["Hood"] = [0, 0, 0]
        comps = copy.deepcopy(self.components)
        comps["components"][0]["nodeName"] = "DoesNotExist"
        rep = va.Report()
        va.validate_vehicle_glb(rep, self.glb, spec, comps, self.hotspots, self.custom)
        joined = "\n".join(rep.errors)
        self.assertIn("[PIVOT] Hood", joined)
        self.assertIn("[META-NODE]", joined)

    def test_malformed_glb_reports_instead_of_crashing(self):
        with tempfile.NamedTemporaryFile(suffix=".glb") as tmp:
            tmp.write(b"not a model at all, definitely not")
            tmp.flush()
            rep = va.Report()
            table = va.validate_vehicle_glb(rep, Path(tmp.name), self.spec, self.components, self.hotspots, self.custom)
            self.assertEqual(table, {})
            self.assertTrue(rep.errors[0].startswith("[GLB-PARSE]"))

    def test_registry_requires_complete_records(self):
        reg = copy.deepcopy(self.registry)
        reg["records"][0]["creator"] = ""
        reg["records"][0]["commercialUse"] = False
        reg["records"][0]["status"] = "approved"
        reg["records"][0]["reviewer"] = ""
        rep = va.Report()
        va.validate_registry(rep, reg, self.manifests, REPO / "assets/build", release=False)
        joined = "\n".join(rep.errors)
        for code in ("LICENCE-FIELD", "LICENCE-COMMERCIAL", "LICENCE-REVIEW"):
            self.assertIn(code, joined)

    def test_registry_release_mode_requires_approval(self):
        reg = copy.deepcopy(self.registry)
        for r in reg["records"]:
            r["status"] = "pending_review"
        rep = va.Report()
        va.validate_registry(rep, reg, self.manifests, REPO / "assets/build", release=True)
        self.assertTrue(any("LICENCE-STATUS" in e for e in rep.errors))

    def test_registry_flags_uncovered_files(self):
        reg = copy.deepcopy(self.registry)
        reg["records"][1]["checksums"] = {}
        rep = va.Report()
        va.validate_registry(rep, reg, self.manifests, REPO / "assets/build", release=False)
        self.assertTrue(any("LICENCE-COVERAGE" in e and "studio_platform_v1.glb" in e for e in rep.errors))

    def test_third_party_record_needs_https_source(self):
        reg = copy.deepcopy(self.registry)
        reg["records"][0]["origin"] = "third_party"
        reg["records"][0]["sourceUrl"] = "somewhere"
        rep = va.Report()
        va.validate_registry(rep, reg, self.manifests, REPO / "assets/build", release=False)
        self.assertTrue(any("LICENCE-SOURCE" in e for e in rep.errors))

    def test_trademark_scan(self):
        rep = va.Report()
        va.validate_no_marks(rep, {"x": "A replica of a Porsche coupe"}, ["UrbanX"])
        self.assertTrue(any("porsche" in e for e in rep.errors))
        rep2 = va.Report()
        va.validate_no_marks(rep2, {"x": "Adjust the seat and mirrors"}, ["UrbanX", "VoltX"])
        self.assertEqual(rep2.errors, [])


if __name__ == "__main__":
    unittest.main()
