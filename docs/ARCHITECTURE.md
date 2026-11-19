# Architecture

AutoVerse 3D is a monorepo with two **native** apps that share data contracts, content, assets and a backend API — but no UI
or rendering code (plan §5).

```
shared/            Versioned JSON contracts + content (single source of truth, bundled into both apps at build time)
  schemas/         JSON Schemas: vehicle, component, asset manifest, configuration, learning module, licence registry
  data/            vehicles, components (+ part animations, exploded views, mode visibility), hotspots,
                   customization options, camera presets
  content/         learning modules + quizzes, EV lab diagram
  design/          design tokens
  licensing/       machine-readable asset licence registry
assets/            source spec, built GLB/USDZ/scene descriptors, manifests
tools/             asset generator, validators, preview renderer (Python + Swift)
backend/           Node 20 API: content, asset downloads, AI proxy with provider abstraction
android/           Kotlin · Compose/Material 3 · Filament · Room · Hilt · Retrofit · Coroutines/Flow
ios/               Swift 6 · SwiftUI · RealityKit · SwiftData · URLSession async/await · XCTest
```

## Layering (both platforms)

| Layer | Android | iOS | Notes |
|---|---|---|---|
| Domain (pure logic) | `:domain` Gradle module (Kotlin/JVM) | `Domain/` | Models mirroring the JSON contracts; search, quiz scoring, learning progress, download state machine, checksum, manifest validation, scene graph & visibility rules, orbit/interior cameras, `PartAnimator`, configuration rules. Unit-tested against the real `/shared` files. |
| Data | `data/` repositories | `Data/`, `Core/Persistence` | Content (bundled JSON), configurations, progress, assistant with offline fallback |
| Core | `core/{database,network,assets,renderer,designsystem,di}` | `Core/{Persistence,Networking,AssetManagement,DesignSystem,DependencyInjection}` | Room/SwiftData, Retrofit/URLSession, pack downloads, theme |
| Renderer | `VehicleRenderer` interface → `FilamentVehicleRenderer` | `VehicleRendering` protocol → `RealityVehicleRenderer` | Implements the plan §6 operations; driven by a declarative `SceneState` |
| Features | `feature/*` (Compose + ViewModels) | `Features/*` (SwiftUI + `@Observable` models) | Home, Showroom, Explore, VehicleViewer, Interior/Mechanical/Safety/Customization panels, EV Lab, Garage, Learning, AI Assistant |

The plan's Android module list (`core/common`, `feature/home`, …) is implemented as **packages** inside `:app` plus a separate
`:domain` module. Splitting every feature into its own Gradle module adds build configuration without benefit at the
current size; the package boundaries match the plan, so they can be promoted to modules later.

## Renderer abstraction

Both renderers expose the same operations: load/unload a vehicle; set camera (orbit or first-person interior rig, with
animated transitions); apply a `SceneState` (hidden nodes, material colour overrides, lights, open parts, exploded view,
highlighted nodes, reduced-motion); reset; tap-to-pick returning node names. View models never touch Filament/RealityKit
types — they compute `SceneState` from domain rules (`VisibilityRules`, `SceneGraph`) and the renderer diffs it.

Part animations and exploded views are **data** (`components.json`) evaluated by the shared `PartAnimator` algorithm
(implemented identically in Kotlin and Swift), not baked glTF/USD animation clips. This keeps behaviour identical across
platforms, makes reduced-motion trivial (jump to the end state) and lets content change without re-exporting the model.

* **Android:** Filament 1.75.1 + gltfio (ubershader materials). Own engine/scene/view setup, a procedural studio reflection
  cubemap, sun + fill lights with shadows, `View.pick` for selection, `MaterialInstance` parameters for recolouring and
  emissive lights, per-renderable duplicated material instances for highlighting, `TransformManager` for animations.
  Resources are destroyed when the viewer leaves composition; rendering pauses with the lifecycle.
* **iOS:** RealityKit `ARView` in non-AR mode, `PerspectiveCamera`, directional lights with shadows, `PhysicallyBasedMaterial`
  overrides for colour/emissive/highlight, collision shapes + `hitTest` for picking, `SceneEvents.Update` for animation.

## Offline-first

The core experience needs no network or account: content and a verified copy of the UrbanX asset are bundled; saved
configurations and learning progress live in Room/SwiftData. Downloadable packs (backend `/v1/assets`) are optional
updates: size shown first, progress, pause/resume (HTTP Range), SHA-256 verification, and an older pack is removed only after
its replacement is verified. The AI assistant is the only online-only feature and falls back to the offline content search.

## AI assistant

```
App ──POST /v1/ai/automotive-assistant──► Backend: validate → rate-limit → retrieve approved content → provider.answer()
                                                     │                                          ├─ AnthropicProvider (server-side secret)
                                                     └─ safety classifier (repair requests)     └─ MockProvider (extractive, offline/CI)
```
The provider is chosen by configuration (`AI_PROVIDER`), so it can be swapped without app changes. Apps never hold keys.

## Platform differences (equivalent user outcomes)

| Area | Android | iOS | Why |
|---|---|---|---|
| Main navigation | 6-item bottom bar incl. Assistant | 5 tabs; Assistant is a toolbar button on every tab and in the viewer | iOS collapses >5 tabs into "More" |
| Model format | GLB | USDZ (same scene graph, generated together) | RealityKit loads USD natively |
| Studio lighting | Procedural IBL cubemap | RealityKit default environment + lights | Engine capabilities |
| Minimum OS | Android 8.0 (API 26) | iOS 17.0 | See setup docs |
