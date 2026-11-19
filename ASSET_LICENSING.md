# Asset licensing registry

Every file that ships inside an AutoVerse 3D app build must have a **complete, reviewed** licence record.
The machine-readable registry is [`shared/licensing/asset_registry.json`](shared/licensing/asset_registry.json)
(schema: [`shared/schemas/asset_registry.schema.json`](shared/schemas/asset_registry.schema.json)). This file is generated
from it by `tools/validate/render_licensing_md.py`. `tools/validate/validate_assets.py` fails CI when a record is incomplete,
when a shipped file is not covered, or when a file changes after review.

## Rules

1. **Original first.** Model vehicles, components, materials and environments from scratch (Blender or the procedural
   generator in `tools/assetgen`). No manufacturer CAD, logos, badges, model names, promotional photos or replicas.
2. **Third-party assets only with an explicit licence** that permits the intended use, *including commercial distribution*.
   "Free to download" is not a licence. If terms are ambiguous, **do not use the asset**.
3. **Record before import.** Add the record (and archive a copy of the licence terms under `shared/licensing/terms/`)
   before the file enters `assets/`.
4. **Review before release.** A human reviewer sets `status: approved`, `reviewer` and `approvalDate`.
   `validate_assets.py --release` refuses records that are not approved.
5. **Changed file ⇒ re-review.** `tools/assetgen/build.py --update-registry` writes new checksums and resets the record to
   `pending_review`.
6. **Takedown process.** If rights are challenged: set `status: withdrawn`, remove the file and its manifest, bump the asset
   version, publish a replacement pack, and record the incident in `docs/IMPLEMENTATION_STATUS.md`.

## Required fields per record

| Field | Meaning |
|---|---|
| `recordId`, `assetId`, `files` | Stable identifiers and every shipped filename |
| `title`, `creator` | What it is and who made it |
| `origin` | `original` or `third_party` |
| `sourceUrl` | Source (https URL for third-party; repo path for originals) |
| `createdOrDownloadedDate` | ISO date |
| `licence.name`, `licence.termsReference` | Exact licence and a copy or archived reference to its terms |
| `commercialUse`, `modificationAllowed` | Must be `true` for shipped assets |
| `attribution.required/text/placement` | Credit text and where it appears in the app |
| `restrictions` | Any notices or limits |
| `checksums` | `sha256:` per file (verified by CI) |
| `reviewer`, `approvalDate`, `status` | Review trail |

## Current records

| Record | Asset | Origin | Licence | Commercial use | Status |
|---|---|---|---|---|---|
| `asset_original_urbanx` | UrbanX fictional compact SUV (v1) | original — AutoVerse 3D project (procedural source: tools/assetgen/urbanx.py) | Project-owned original work | Yes | **pending_review** |
| `asset_original_studio` | Neutral studio platform (v1) | original — AutoVerse 3D project (procedural source: tools/assetgen/urbanx.py#build_studio) | Project-owned original work | Yes | **pending_review** |

No third-party models, textures, HDRIs, fonts or sounds are used. Studio lighting is generated at runtime (Android: a
procedural reflection cubemap; iOS: RealityKit lights). UI icons are platform icon sets (Material Icons from the Compose
icon library, Apache 2.0; SF Symbols on iOS under Apple's terms for apps on its platforms). The app icon is original.

> **Owner action required before public release:** a human reviewer must inspect these records and set them to
> `approved` with a name and date. They are intentionally left `pending_review` because the authoring agent must not approve
> its own work. Also confirm that the working vehicle names (UrbanX, TerraX, VoltX, AeroGT, CityCruiser) are clear of
> trademark conflicts (plan §17).

### Checksums under review

| File | SHA-256 |
|---|---|
| `urbanx_suv_v1.glb` | `sha256:0242401dde0f8552619394a19b0cf84016a167179e526cde4712e5b7ad0a0dfc` |
| `urbanx_suv_v1.scene.json` | `sha256:6d3fb7389d436507a6843df6659ad1f8d22e0810e9f303029d9eb773f468c47f` |
| `urbanx_suv_v1.usdz` | `sha256:6cd65aa82a7ec6c63b6e0112a0f12f5744e5bfdfe01f40bea6b94ef8e99e3ed9` |
| `studio_platform_v1.glb` | `sha256:dbfe864a6dd8f734a9379404cc249316a7eb024f89d2212a65602a166c971f93` |
| `studio_platform_v1.scene.json` | `sha256:bd0b71931d18c0e48132abff715be6361f70d35a6dca2655df6d5754978da637` |
| `studio_platform_v1.usdz` | `sha256:6a89709fb92ec6a311da06f2093f2bc07ef96aa789f8f0171f72261e2bee5432` |

## Third-party sources investigated (none imported)

| Source | Notes for future use |
|---|---|
| Poly Haven | Assets are published as CC0; still record the specific asset page and download date. |
| Kenney | Packs are generally CC0; verify each pack's licence file. |
| Sketchfab | Licence varies per model (many are non-commercial or require attribution); check every model individually. |
