# iOS setup

| Requirement | Version used |
|---|---|
| macOS + Xcode | Xcode 26.1 (Swift 6 language mode). Xcode 16+ should work. |
| XcodeGen | `brew install xcodegen` (the `.xcodeproj` is generated from `ios/project.yml`) |
| Minimum OS | **iOS 17.0** (SwiftData, `@Observable`, RealityKit non-AR `ARView`). `Entity(contentsOf:)` async loading is used on iOS 18+, synchronous `Entity.load` on 17. |
| Tested on | iPhone 17 Pro simulator, iOS 26.1 |

```bash
cd ios
xcodegen generate
open AutoVerse3D.xcodeproj           # or build from the command line:
xcodebuild -project AutoVerse3D.xcodeproj -scheme AutoVerse3D \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' CODE_SIGNING_ALLOWED=NO build
```

A pre-build script copies `shared/data`, `shared/content`, `assets/manifests` and the USDZ + scene descriptors from
`assets/build` into the app bundle, so `/shared` stays the single source of truth (GLB files are Android-only).

**Backend URL:** `Info.plist → API_BASE_URL` (default `http://localhost:8787/`, reachable from the simulator). Local HTTP is
allowed via `NSAllowsLocalNetworking` only. For release builds set an HTTPS URL; no other ATS exceptions are needed.

**Signing:** set your team in Xcode (`DEVELOPMENT_TEAM`) to run on a device. Simulator builds need no signing.

## Tests

```bash
xcodebuild … test                                   # unit (AutoVerse3DTests) + UI journeys (AutoVerse3DUITests)
TEST_RUNNER_SCREENSHOT_DIR="$PWD/../screenshots/ios" xcodebuild … -only-testing:AutoVerse3DUITests/ScreenshotTour/testTour test
```
`-uiTestingInMemoryStore` (set by UI tests) uses an in-memory SwiftData store.

## Structure

`App/` (entry, tab + navigation routing) · `Core/` (Networking, Persistence, DesignSystem, AssetManagement,
DependencyInjection) · `Domain/` (models + pure logic) · `Data/` (bundled content) · `Features/` (Home, Showroom, Explore,
VehicleViewer, InteriorExplorer, MechanicalExplorer, EVExplorer, SafetyLab, Customization, MyGarage, Learning, AIAssistant).
UI state lives in `@MainActor @Observable` models; views stay declarative.
