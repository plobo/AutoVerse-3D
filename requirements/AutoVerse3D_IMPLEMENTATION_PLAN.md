# AutoVerse 3D --- Cross-Platform Implementation Plan

**Platforms:** iOS and Android\
**Implementation partner:** Claude Code\
**Product direction:** A 3D automotive learning and exploration app
using original, fictional vehicles and free assets with verified
commercial-use rights.\
**Primary constraint:** No paid 3D models, manufacturer CAD files,
unauthorized vehicle replicas, logos, trademarks, or scraped proprietary
assets.

------------------------------------------------------------------------

## 1. Product overview

AutoVerse 3D lets users explore fictional vehicles in an interactive 3D
showroom, inspect their interiors and mechanical systems, customize
appearance, and learn how automotive technologies work.

The product should feel like a virtual automotive discovery studio---not
a catalogue of real manufacturers' vehicles.

### Product goals

1.  Make 3D vehicle exploration intuitive on a phone or tablet.
2.  Explain vehicle components through interactive, visual learning.
3.  Let users customize fictional vehicles and save configurations.
4.  Support offline access to downloaded 3D models and learning content.
5.  Keep iOS and Android feature parity while using native platform
    technologies.
6.  Build an asset pipeline that can grow without licensing uncertainty.

### Target users

-   Car enthusiasts curious about vehicle design and engineering.
-   Students learning basic automotive concepts.
-   People comparing vehicle layouts and technologies at a conceptual
    level.
-   Casual users who enjoy 3D customization and exploration.

------------------------------------------------------------------------

## 2. Original vehicle and asset strategy

All vehicles must be fictional and designed from scratch. Avoid copying
the distinctive appearance of a specific production vehicle. Do not use
manufacturer names, badges, logos, model names, proprietary CAD data, or
promotional photography.

### Initial fictional vehicle lineup

  Vehicle ID            Display name   Concept
  --------------------- -------------- --------------------------------
  `urbanx_suv`          UrbanX         Compact urban SUV; MVP vehicle
  `terrax_4x4`          TerraX         Fictional off-road SUV
  `voltx_ev`            VoltX          Fictional electric vehicle
  `aerogt_coupe`        AeroGT         Fictional sports coupe
  `citycruiser_sedan`   CityCruiser    Fictional family sedan

These names and designs are working concepts. Check name availability
and trademark considerations before public release.

### Asset sourcing rules

Preferred order:

1.  Create original vehicle models in Blender.
2.  Create original components, materials, textures, animations, and
    environments.
3.  Use third-party assets only when their exact licence permits the
    intended use, including commercial distribution if the app may be
    monetized.
4.  Keep an auditable record for every imported asset.

Potential sources to investigate---not blanket approval to use every
asset:

-   **Blender:** create and edit original 3D assets.
-   **Poly Haven:** HDRIs, textures, and environment assets; verify the
    asset's stated licence at download time.
-   **Kenney:** selected game assets; verify the licence and suitability
    for the intended use.
-   **Sketchfab:** only assets whose individual licence and creator
    permissions clearly allow the app's intended use. Do not assume
    every downloadable model is commercially reusable.

### Required `ASSET_LICENSING.md` registry

For every non-original asset, record:

-   Asset ID and filename.
-   Asset title and creator.
-   Original source URL.
-   Download date.
-   Exact licence name and a copy or archived reference to the licence
    terms.
-   Whether commercial use is permitted.
-   Whether modification is permitted.
-   Attribution text and placement requirements.
-   Any restrictions or required notices.
-   File checksum.
-   Reviewer and approval date.

Do not merge an asset into a release build until its licence record is
complete and reviewed. If rights are unclear, replace the asset.

### Model creation standards

Use Blender as the primary modelling and export tool.

Suggested structure:

``` text
VehicleRoot
├── Body
├── Glass
├── Wheels
│   ├── FrontLeft
│   ├── FrontRight
│   ├── RearLeft
│   └── RearRight
├── Doors
│   ├── FrontLeftDoor
│   ├── FrontRightDoor
│   ├── RearLeftDoor
│   └── RearRightDoor
├── Hood
├── Trunk
├── Lights
├── Interior
├── Chassis
├── Suspension
├── EngineOrMotor
└── WheelsAndBrakes
```

Use consistent origins, pivots, units, naming, and material conventions.
Separate moving parts from static geometry. Provide simplified levels of
detail for mobile performance.

### Asset formats

-   Use **glTF/GLB** as the primary interchange and delivery format
    where supported.
-   Use **USDZ** for iOS workflows where RealityKit compatibility or
    platform-specific packaging makes it useful.
-   Keep the source `.blend` files and export scripts in the asset
    repository.
-   Validate scale, orientation, materials, texture references, pivots,
    animations, and polygon budgets before accepting a model.

------------------------------------------------------------------------

## 3. Feature scope

### 3.1 Virtual 3D showroom

-   Display a vehicle on a neutral studio platform.
-   Rotate, zoom, and pan using touch gestures.
-   Reset the camera to a standard view.
-   Switch between front, rear, side, top, and three-quarter camera
    presets.
-   Change paint colours using a defined palette.
-   Toggle lights.
-   Open and close doors, hood, and trunk when the model supports those
    animations.
-   Show vehicle name, fictional category, and short specifications.
-   Support loading, error, and asset-download states.

### 3.2 Interior explorer

-   Move the camera into the cabin.
-   Inspect dashboard, steering wheel, seats, centre console, displays,
    and storage areas.
-   Select hotspots to reveal concise explanations.
-   Switch between driver, passenger, and rear-seat viewpoints.
-   Provide an accessible list of interior hotspots as an alternative to
    3D selection.

### 3.3 Mechanical explorer

-   Show a simplified, educational vehicle assembly.
-   Select components such as engine or motor, battery, transmission,
    brakes, suspension, radiator, and exhaust where appropriate.
-   Display a component name, purpose, and simple explanation.
-   Support an exploded-view animation that separates selected
    assemblies.
-   Allow users to reset the assembly to its original state.
-   Avoid presenting simplified models as exact service or repair
    instructions.

### 3.4 Electric vehicle learning module

-   Show a conceptual battery pack, inverter, electric motor, charging
    port, and regenerative braking flow.
-   Animate the high-level flow of energy from battery to motor.
-   Explain charging concepts and energy recovery.
-   Clearly label diagrams as educational simplifications.

### 3.5 Safety lab

-   Demonstrate conceptual locations and roles of airbags, seat belts,
    crumple zones, stability systems, and sensors.
-   Use explanatory animations and selectable hotspots.
-   Avoid claims that the fictional design has been crash-tested or
    certified.

### 3.6 Customization studio

-   Change exterior paint.
-   Change wheel styles from original app-created designs.
-   Change selected trim and interior colour options.
-   Save, rename, duplicate, and delete configurations.
-   Compare a saved configuration with the current design.
-   Store configuration data locally.

### 3.7 Learning and quizzes

-   Provide short learning modules for vehicle systems.
-   Include optional quizzes and progress tracking.
-   Keep educational content versioned and independent of 3D assets.
-   Provide text alternatives for every essential visual explanation.

### 3.8 AI automotive assistant

-   Answer questions about the currently selected fictional vehicle and
    general automotive concepts.
-   Use app-owned learning content as grounding context.
-   Provide clear uncertainty statements and avoid inventing
    specifications.
-   Do not provide safety-critical repair instructions or substitute for
    a qualified mechanic.
-   Use a provider abstraction so the model provider can be changed
    without rewriting the app.
-   Route requests through a secure backend; never embed private API
    keys in the mobile apps.
-   Offer a non-AI fallback using searchable offline learning content.

------------------------------------------------------------------------

## 4. Screen map and user journeys

### Main navigation

1.  **Home**
    -   Featured fictional vehicles.
    -   Continue exploring.
    -   Learning shortcuts.
2.  **Showroom**
    -   Vehicle list and categories.
    -   Vehicle detail and 3D viewer.
3.  **Explore**
    -   Interior, mechanical, EV, and safety modules.
4.  **My Garage**
    -   Saved vehicle configurations.
    -   Downloaded vehicles.
5.  **Learn**
    -   Learning modules, quizzes, and progress.
6.  **AI Assistant**
    -   Context-aware automotive Q&A.

### Key journeys

**Explore a vehicle** 1. Open Showroom. 2. Select UrbanX. 3. Load the 3D
model. 4. Rotate and zoom. 5. Change paint. 6. Open a door or hood. 7.
Select a hotspot to learn about a component.

**Inspect a mechanical system** 1. Open a vehicle. 2. Select Mechanical
Explorer. 3. Choose an assembly. 4. Play the exploded view. 5. Select a
component. 6. Read or listen to its explanation. 7. Reset the assembly.

**Save a customization** 1. Open Customization Studio. 2. Change paint,
wheels, or trim. 3. Save the configuration. 4. Give it a name. 5. Reopen
it from My Garage.

**Use the app offline** 1. Download a vehicle asset pack while online.
2. Open the downloaded vehicle without a network connection. 3. Explore
cached models, animations, and learning content. 4. Show a clear message
when an online-only AI feature is unavailable.

------------------------------------------------------------------------

## 5. Technical architecture

Use separate native applications with shared product requirements, data
contracts, asset formats, and backend APIs.

### iOS

-   **Language:** Swift 6.
-   **UI:** SwiftUI.
-   **3D rendering:** RealityKit, with Model I/O or other Apple
    frameworks where useful for asset processing.
-   **Persistence:** SwiftData for local app data.
-   **Networking:** URLSession with async/await.
-   **Concurrency:** structured concurrency; isolate UI state on
    `MainActor`.
-   **Dependency injection:** lightweight protocol-based dependency
    container.
-   **Testing:** XCTest and UI tests.
-   **Minimum OS:** choose and document a deployment target after
    validating required RealityKit APIs and the target-device range.

Suggested modules:

``` text
AutoVerse3D/
├── App/
├── Core/
│   ├── Networking/
│   ├── Persistence/
│   ├── DesignSystem/
│   ├── AssetManagement/
│   └── DependencyInjection/
├── Features/
│   ├── Home/
│   ├── Showroom/
│   ├── VehicleViewer/
│   ├── InteriorExplorer/
│   ├── MechanicalExplorer/
│   ├── EVExplorer/
│   ├── SafetyLab/
│   ├── Customization/
│   ├── MyGarage/
│   ├── Learning/
│   └── AIAssistant/
├── Domain/
├── Data/
└── Resources/
```

Keep rendering state and UI state coordinated through well-defined view
models or feature state containers. Do not put all app logic in SwiftUI
views.

### Android

-   **Language:** Kotlin.
-   **UI:** Jetpack Compose and Material 3.
-   **3D rendering:** Filament with a maintained Android integration
    strategy.
-   **Persistence:** Room.
-   **Networking:** Retrofit or Ktor client; choose one and standardize.
-   **Asynchronous work:** Coroutines and Flow.
-   **Dependency injection:** Hilt.
-   **Image and asset loading:** a consistent image-loading library and
    an asset download manager.
-   **Testing:** JUnit, coroutine test utilities, Compose UI tests, and
    instrumentation tests.

Suggested modules:

``` text
app/
core/
  common/
  designsystem/
  network/
  database/
  assets/
  testing/
feature/
  home/
  showroom/
  vehicleviewer/
  interiorexplorer/
  mechanicalexplorer/
  evexplorer/
  safetylab/
  customization/
  mygarage/
  learning/
  aiassistant/
domain/
data/
```

Keep Filament rendering code behind a renderer interface so the UI and
domain layers do not depend directly on rendering implementation
details.

### Shared cross-platform contracts

Share and version:

-   Vehicle and component identifiers.
-   Asset manifest format.
-   Configuration JSON schema.
-   Learning-content schema.
-   API request and response contracts.
-   Asset naming conventions.
-   Feature acceptance criteria.
-   Visual design tokens where practical.

Do not attempt to share native UI or rendering code between
SwiftUI/RealityKit and Compose/Filament.

------------------------------------------------------------------------

## 6. 3D rendering and interaction design

### Renderer abstraction

Define a small set of platform-neutral concepts and implement them
natively:

-   Load and unload a vehicle asset.
-   Set camera position and target.
-   Apply a material or colour variant.
-   Select and highlight a component.
-   Play and stop an animation.
-   Toggle visibility of a component.
-   Enter and exit exploded view.
-   Reset the scene.

### Performance requirements

Set budgets after measuring on representative low-, mid-, and high-tier
devices. Initial targets for the MVP:

-   Keep the first vehicle model reasonably lightweight.
-   Use compressed textures and appropriate texture dimensions.
-   Use level-of-detail variants where helpful.
-   Avoid excessive transparent materials and draw calls.
-   Load large assets asynchronously.
-   Release scene resources when leaving the viewer.
-   Avoid blocking the main/UI thread during asset loading.
-   Provide a static fallback or retry state if rendering fails.

Measure startup time, model load time, frame rate, memory use, battery
use, and thermal behaviour on real devices.

### Interaction and accessibility

-   Provide visible reset-camera and close controls.
-   Support VoiceOver and TalkBack labels for all controls and component
    hotspots.
-   Provide a semantic component list for users who cannot use 3D
    selection.
-   Respect dynamic text sizing where possible.
-   Use sufficient contrast and avoid relying on colour alone.
-   Support reduced-motion preferences by limiting or disabling
    nonessential animations.

------------------------------------------------------------------------

## 7. Data models

Use stable IDs and versioned schemas.

### Vehicle

``` json
{
  "id": "urbanx_suv",
  "name": "UrbanX",
  "category": "compact_suv",
  "description": "An original fictional compact SUV.",
  "assetManifestId": "urbanx_suv_v1",
  "featureIds": ["exterior", "interior", "mechanical"],
  "schemaVersion": 1
}
```

### Vehicle component

``` json
{
  "id": "urbanx_front_brake",
  "vehicleId": "urbanx_suv",
  "name": "Front brake assembly",
  "category": "braking",
  "nodeName": "FrontBrakeAssembly",
  "description": "A simplified model of a disc brake assembly.",
  "animationIds": ["brake_exploded"],
  "schemaVersion": 1
}
```

### Asset manifest

``` json
{
  "id": "urbanx_suv_v1",
  "vehicleId": "urbanx_suv",
  "version": 1,
  "format": "glb",
  "fileName": "urbanx_suv_v1.glb",
  "fileSizeBytes": 0,
  "checksum": "sha256:REPLACE_WITH_REAL_CHECKSUM",
  "minimumAppVersion": "1.0.0",
  "licenceRecordId": "asset_original_urbanx"
}
```

### Vehicle configuration

``` json
{
  "id": "configuration_uuid",
  "vehicleId": "urbanx_suv",
  "name": "Midnight UrbanX",
  "paintId": "midnight_blue",
  "wheelId": "wheel_style_02",
  "trimId": "dark_trim",
  "createdAt": "ISO-8601",
  "updatedAt": "ISO-8601",
  "schemaVersion": 1
}
```

### Learning module and quiz

Represent modules, sections, questions, answers, explanations, and
completion state as versioned data. Keep content separate from app code
so it can be updated without rebuilding the apps when appropriate.

------------------------------------------------------------------------

## 8. Backend and APIs

A backend is optional for the first offline-capable MVP. Add one when
remote content management, account sync, analytics, or AI requests are
needed.

### Suggested backend options

Choose one after reviewing cost, data location, privacy, and operational
requirements:

-   Supabase for authentication, PostgreSQL, and object storage.
-   Firebase for authentication, document data, and storage.
-   A small custom API if more control is needed.

Do not make the app dependent on a paid service for its basic 3D viewer.
Keep costs predictable and define usage limits for any hosted AI
service.

### Example API surface

``` text
GET    /v1/vehicles
GET    /v1/vehicles/{vehicleId}
GET    /v1/vehicles/{vehicleId}/components
GET    /v1/assets/{assetManifestId}
GET    /v1/learning/modules
GET    /v1/learning/modules/{moduleId}
POST   /v1/ai/automotive-assistant
POST   /v1/telemetry/events
```

Use signed or otherwise access-controlled asset URLs if content is
private. Support asset versioning, cache headers, integrity checks, and
resumable downloads where practical.

### AI request flow

1.  App sends the user's question and selected vehicle/component context
    to the backend.
2.  Backend validates the request and applies rate limits.
3.  Backend retrieves relevant approved learning content.
4.  Backend calls the configured AI provider using a server-side secret.
5.  Backend returns the answer, source references where available, and
    safety guidance.
6.  App displays the answer and indicates when it is general information
    rather than vehicle-specific data.

Do not send unnecessary personal data to the AI provider. Document
retention, provider processing, and privacy implications before release.

------------------------------------------------------------------------

## 9. Offline-first strategy

The core 3D experience should work without an account or network
connection.

Cache locally:

-   Vehicle model files and required textures.
-   Asset manifests and checksums.
-   Component metadata.
-   Animation metadata.
-   Learning modules and quiz content.
-   Saved configurations and learning progress.

Requirements:

-   Show download size before downloading.
-   Show progress, pause/resume where supported, and retry after
    failure.
-   Verify checksums after download.
-   Keep an asset version and compatibility record.
-   Allow users to remove downloaded vehicle packs.
-   Do not delete a working asset until its replacement is verified.
-   Clearly distinguish offline features from online-only services.

------------------------------------------------------------------------

## 10. Security, privacy, and reliability

-   Never embed AI provider secrets or backend admin keys in the app.
-   Use HTTPS for network communication.
-   Validate downloaded manifests and file checksums.
-   Apply request limits and abuse controls to AI endpoints.
-   Minimize analytics and avoid collecting unnecessary personal
    information.
-   Provide a privacy policy and disclose any third-party AI processing.
-   Avoid storing sensitive information in logs.
-   Handle malformed assets and network failures without crashing.
-   Keep dependencies updated and review licences.
-   Include a process for removing or replacing an asset if rights are
    challenged.

------------------------------------------------------------------------

## 11. Testing strategy

### Unit tests

-   Vehicle and component decoding.
-   Configuration persistence.
-   Asset manifest validation.
-   Licence registry validation.
-   Download state transitions.
-   Checksum verification.
-   AI request construction and response parsing.
-   Learning progress and quiz scoring.

### Integration tests

-   Backend API success and error cases.
-   Asset download and offline loading.
-   Database migrations.
-   Asset version upgrades.
-   Renderer lifecycle and resource cleanup.
-   AI proxy rate limits and provider failures.

### UI and device tests

-   Main navigation and core user journeys.
-   3D viewer controls on supported devices.
-   Component selection and hotspot accessibility.
-   Colour and wheel customization.
-   Save/reopen configuration.
-   Offline mode and interrupted downloads.
-   VoiceOver and TalkBack checks.
-   Memory, frame rate, and thermal testing on real devices.

### Asset validation tests

Create automated checks where possible for:

-   Required file presence.
-   Manifest schema validity.
-   Unique node names.
-   Expected pivots and animation names.
-   Texture references and dimensions.
-   File size and polygon budgets.
-   Licence registry completeness.
-   Checksum correctness.

------------------------------------------------------------------------

## 12. Development phases

### Phase 0 --- Project foundation

-   Create separate iOS and Android repositories or clearly separated
    top-level projects.
-   Add coding standards, branching strategy, CI, and documentation
    templates.
-   Define shared IDs, JSON schemas, design tokens, and feature parity
    checklist.
-   Create the asset licensing process before importing third-party
    assets.

**Exit criteria:** Both projects build, run, and execute a basic test on
CI.

### Phase 1 --- Original 3D asset MVP

-   Create the UrbanX fictional SUV in Blender.
-   Model exterior, cabin, wheels, doors, hood, and trunk.
-   Add basic materials and lighting.
-   Add simple door, hood, and trunk animations.
-   Export and validate GLB.
-   Create iOS-compatible USDZ export if required.
-   Document the complete source and export pipeline.

**Exit criteria:** The model passes asset validation and is approved for
app use.

### Phase 2 --- 3D showroom

-   Implement vehicle list and detail screens.
-   Implement native 3D viewer on iOS and Android.
-   Add camera controls and reset.
-   Add paint selection.
-   Add basic part animations.
-   Add loading and failure states.

**Exit criteria:** Users can load, inspect, rotate, zoom, recolour, and
reset UrbanX on both platforms.

### Phase 3 --- Interior explorer

-   Add interior camera presets.
-   Add selectable cabin hotspots.
-   Add accessible list-based hotspot navigation.
-   Add educational content for major interior elements.

**Exit criteria:** Users can inspect the cabin and read or hear
component explanations.

### Phase 4 --- Mechanical explorer

-   Create a simplified generic engine or motor assembly and selected
    components.
-   Add component highlighting and selection.
-   Add exploded-view animation.
-   Add reset and component descriptions.

**Exit criteria:** Users can select at least five components and
understand their basic purpose.

### Phase 5 --- EV and safety learning

-   Add conceptual EV powertrain content.
-   Add energy-flow animation.
-   Add safety-system hotspots and explanations.
-   Clearly label educational simplifications.

**Exit criteria:** Each module works on both platforms and has
accessible text alternatives.

### Phase 6 --- Customization and My Garage

-   Add paint, wheel, and trim variants.
-   Add local configuration persistence.
-   Add rename, duplicate, delete, and restore.
-   Add configuration comparison if performance permits.

**Exit criteria:** Saved configurations survive app restarts and work
offline.

### Phase 7 --- Learning and AI assistant

-   Add learning modules and quizzes.
-   Add backend AI proxy and provider abstraction.
-   Ground responses in approved content.
-   Add rate limiting, error handling, and non-AI fallback.

**Exit criteria:** AI secrets remain server-side; assistant responses
are contextual and safe; learning content works offline.

### Phase 8 --- Offline, optimization, and release readiness

-   Add downloadable vehicle packs.
-   Add integrity checks and version upgrades.
-   Profile rendering and memory on representative devices.
-   Complete accessibility, privacy, and licence reviews.
-   Prepare store assets and release documentation.

**Exit criteria:** MVP acceptance criteria pass on both platforms.

------------------------------------------------------------------------

## 13. MVP definition and acceptance criteria

Keep the first release deliberately small.

### MVP scope

-   One original fictional vehicle: UrbanX.
-   Exterior 3D viewer with rotate, zoom, pan, and camera reset.
-   At least one paint customization feature.
-   Basic door and hood animation.
-   Interior view with hotspots.
-   Generic mechanical assembly.
-   At least five selectable mechanical components with explanations.
-   One exploded-view animation.
-   Local saved configuration.
-   Offline access to the downloaded UrbanX model and learning content.
-   Native iOS and Android implementations.
-   Complete asset provenance and licence documentation.

### Acceptance checklist

-   [ ] iOS app builds and launches on the documented minimum OS.
-   [ ] Android app builds and launches on the documented minimum
    Android version.
-   [ ] Both apps load the same approved fictional UrbanX asset.
-   [ ] Camera controls work smoothly on the agreed device test matrix.
-   [ ] Paint customization works and can be reset.
-   [ ] Supported doors/hood animations play and reset correctly.
-   [ ] Interior hotspots work and have accessible alternatives.
-   [ ] At least five mechanical components can be selected.
-   [ ] Exploded view can be entered and reset.
-   [ ] A configuration can be saved and restored after app restart.
-   [ ] Downloaded model and learning content work in airplane mode.
-   [ ] All shipped assets have complete, approved licence records.
-   [ ] No unauthorized manufacturer marks, branded replicas, or paid
    models are included.
-   [ ] Automated tests pass and critical journeys are manually
    verified.
-   [ ] Performance and memory results are recorded for representative
    devices.

------------------------------------------------------------------------

## 14. Required project documentation

Create and maintain:

``` text
README.md
IMPLEMENTATION_PLAN.md
IMPLEMENTATION_STATUS.md
ARCHITECTURE.md
ASSET_LICENSING.md
ASSET_PIPELINE.md
API_DOCUMENTATION.md
DATA_MODELS.md
IOS_SETUP.md
ANDROID_SETUP.md
TESTING_STRATEGY.md
CONTRIBUTING.md
PRIVACY_AND_SECURITY.md
```

`IMPLEMENTATION_STATUS.md` should track each phase, platform parity,
completed tasks, blockers, test status, and asset approval status.

------------------------------------------------------------------------

## 15. Claude Code master instructions

Use the following as the project-level instruction for Claude Code:

> You are implementing AutoVerse 3D, a native iOS and Android
> application for exploring original fictional vehicles in interactive
> 3D.
>
> Build two native applications: - iOS: Swift 6, SwiftUI, RealityKit,
> SwiftData, async/await, XCTest. - Android: Kotlin, Jetpack Compose,
> Filament, Room, Coroutines/Flow, Hilt, JUnit.
>
> Do not use a WebView as the main app UI or as a substitute for native
> 3D rendering.
>
> The product must use original fictional vehicle designs and only
> assets whose licences have been verified for the intended distribution
> and monetization model. Never download, generate, or include
> unauthorized manufacturer CAD files, logos, trademarks, branded
> replicas, paid models, or scraped proprietary assets. Do not assume an
> asset is commercially reusable merely because it is downloadable for
> free.
>
> Before adding any third-party asset, update and review
> `ASSET_LICENSING.md`. If licence terms are ambiguous, do not use the
> asset.
>
> Implement features in the phases defined in `IMPLEMENTATION_PLAN.md`.
> Keep `IMPLEMENTATION_STATUS.md` updated after each meaningful task. Do
> not claim a feature is complete until it builds and its tests pass.
>
> Keep business logic separate from UI and rendering code. Use protocols
> and dependency injection where appropriate. Handle asynchronous work,
> cancellation, errors, and resource cleanup correctly.
>
> Maintain feature parity between iOS and Android. If a
> platform-specific limitation requires different behaviour, document it
> and provide an equivalent user outcome.
>
> Keep AI provider secrets on the backend. The basic showroom, saved
> configurations, and downloaded learning content must not require an AI
> subscription or network connection.
>
> Write unit tests for domain logic, persistence, asset validation, and
> networking. Add integration and UI tests for critical user journeys.
> Include accessibility labels and non-3D alternatives for essential
> interactions.
>
> Work incrementally. First inspect the repository and existing project
> structure, then propose a concise task breakdown for the current
> phase. Do not generate the entire application in one unreviewed step.
> At the end of each phase, report changed files, build/test results,
> outstanding issues, and documentation updates.

------------------------------------------------------------------------

## 16. Recommended first Claude Code task

Start with Phase 0 and Phase 1 only. Ask Claude Code to:

1.  Inspect the repository and report its current state.
2.  Create the documentation files and shared data schemas.
3.  Set up the native iOS and Android project foundations.
4.  Define the asset manifest and licence registry format.
5.  Create a Blender asset specification for UrbanX, including
    dimensions, node names, pivots, materials, animations, and mobile
    performance budgets.
6.  Add automated validation for the manifest and licence registry.
7.  Build and test both app shells before implementing the 3D viewer.

Do not begin AI, accounts, cloud sync, or a large vehicle catalogue
until the first vehicle loads reliably on both platforms.

------------------------------------------------------------------------

## 17. Product decisions to resolve before implementation

-   Minimum supported iOS and Android versions.
-   Whether the initial release is fully offline with optional online
    features, or requires account sign-in for selected features.
-   Whether AI assistant functionality is included in the first public
    release or a later phase.
-   Whether the first release is free, ad-supported, or monetized later.
-   Target device performance tiers and maximum downloadable asset size.
-   Final review of fictional vehicle names and visual designs for
    trademark and trade-dress risk.

**Guiding principle:** Ship one excellent, fully original and
well-optimized fictional vehicle before expanding the catalogue. The
asset pipeline, licensing discipline, and cross-platform renderer
foundations are more important than the number of vehicles in the first
release.
