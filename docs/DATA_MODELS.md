# Data models

All contracts live in `shared/` with JSON Schemas in `shared/schemas/` and are validated in CI. Every document carries
`schemaVersion`; content files also carry `contentVersion` so apps can detect updated content. Kotlin mirrors are in
`android/domain/.../model/Models.kt`, Swift mirrors in `ios/AutoVerse3D/Domain/Models.swift`. Unknown JSON keys are ignored
by both apps so newer content stays readable.

| File | Schema | Purpose |
|---|---|---|
| `shared/data/vehicles.json` | `vehicle.schema.json` | Fictional vehicle catalogue (UrbanX available; four "coming soon") |
| `shared/data/components.json` | `component.schema.json` | Selectable components (exterior + mechanical), part animations, exploded views, per-mode visibility |
| `shared/data/hotspots.json` | — (validated via model checks) | Interior and safety hotspots with positions and explanations |
| `shared/data/customization_options.json` | — | Paints, wheel styles, trims, interior colours, defaults |
| `shared/data/camera_presets.json` | — | Orbit presets (front, rear, side, top, three-quarter) and interior viewpoints |
| `shared/content/learning_modules.json` | `learning_module.schema.json` | Modules, sections, quizzes |
| `shared/content/ev_lab.json` | — | EV diagram nodes, flows and text alternatives |
| `assets/manifests/*.json` | `asset_manifest.schema.json` | Asset files, sizes, SHA-256, minimum app version, licence record |
| `assets/build/*.scene.json` | — | Generated node/material/pivot map used by both apps |
| `shared/licensing/asset_registry.json` | `asset_registry.schema.json` | Licence records |
| saved configuration | `configuration.schema.json` | Persisted locally (Room / SwiftData) |

## Identifiers

Vehicle ids are `snake_case` (`urbanx_suv`). Component ids are `<vehicle-prefix>_<part>` (`urbanx_front_brake`). Model node
names are `PascalCase` (`FrontBrakeAssembly`); mesh nodes are `<Parent>_<Part>`. Hotspot marker nodes are `Hotspot_<id>`.
Asset manifest ids are `<vehicle>_v<version>`.

## Examples

Vehicle component:
```json
{ "id": "urbanx_front_brake", "vehicleId": "urbanx_suv", "module": "mechanical", "name": "Front brake assembly",
  "category": "braking", "nodeName": "FrontBrakeAssembly", "description": "A simplified model of a disc brake assembly.",
  "purpose": "…", "detail": "…", "animationIds": ["brake_exploded", "mechanical_exploded"], "schemaVersion": 1 }
```

Asset manifest (iOS uses `platformVariants.ios`; Android uses the primary GLB):
```json
{ "id": "urbanx_suv_v1", "vehicleId": "urbanx_suv", "version": 1, "format": "glb", "fileName": "urbanx_suv_v1.glb",
  "fileSizeBytes": 1435232, "checksum": "sha256:…", "minimumAppVersion": "1.0.0", "licenceRecordId": "asset_original_urbanx",
  "platformVariants": { "ios": { "format": "usdz", "fileName": "urbanx_suv_v1.usdz", "fileSizeBytes": 651867, "checksum": "sha256:…" } },
  "sceneDescriptor": { "format": "json", "fileName": "urbanx_suv_v1.scene.json", "fileSizeBytes": 35957, "checksum": "sha256:…" },
  "triangleCount": 17320, "schemaVersion": 1 }
```

Vehicle configuration (adds `interiorId` to the plan's example to support interior colour options):
```json
{ "id": "3f0c6a1e-…", "vehicleId": "urbanx_suv", "name": "Midnight UrbanX", "paintId": "midnight_blue",
  "wheelId": "wheel_style_02", "trimId": "dark_trim", "interiorId": "interior_sand",
  "createdAt": "2026-11-18T10:00:00Z", "updatedAt": "2026-11-18T10:00:00Z", "schemaVersion": 1 }
```

## Local persistence

| Data | Android (Room) | iOS (SwiftData) |
|---|---|---|
| Saved configurations | `configurations` | `SavedConfiguration` |
| Learning progress | `module_progress` (sections read, best quiz %, completed, content version) | `ModuleProgressRecord` |
| Installed asset packs | `installed_packs` | `InstalledPack` |
| "Continue exploring" | DataStore preferences | `UserDefaults` |

Room exports its schema to `android/app/schemas/`; add a `Migration` for every version bump. SwiftData models are additive;
introduce a `VersionedSchema` + migration plan at the first breaking change.

## Offline search

Used by the assistant fallback and identical on backend, Android and iOS: lowercase, split on non-alphanumerics, drop
1-character tokens and a fixed stop-word list; score = Σ over distinct query terms of (3 if the term is in the title) +
(occurrences in the body); sort by score then id; return the top 4.

## Learning rules

A quiz passes at ≥ 70 %. A module is complete when every section is marked read and (if it has a quiz) the best score passes.
The best score is kept across attempts.
