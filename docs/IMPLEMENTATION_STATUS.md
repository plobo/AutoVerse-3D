# Implementation status

_Last updated: 2026-11-19._ Legend: ✅ done and tested · 🟡 implemented, needs human/device follow-up · ⬜ not started.

## Phases

| Phase | Scope | Android | iOS | Shared / backend | Notes |
|---|---|---|---|---|---|
| 0 Foundation | Repos, docs, CI, shared IDs/schemas, tokens, licensing process | ✅ | ✅ | ✅ | Monorepo with clearly separated `android/` and `ios/` projects; CI in `.github/workflows/ci.yml` |
| 1 Original asset MVP | UrbanX model, moving parts, materials, GLB + USDZ, validation, pipeline docs | ✅ | ✅ | ✅ | 🟡 Licence records await owner approval (`pending_review`) |
| 2 3D showroom | List/detail, native viewer, camera controls + presets + reset, paint, part animations, loading/failure states | ✅ | ✅ | — | |
| 3 Interior explorer | Cabin viewpoints, 8 hotspots, accessible list, explanations | ✅ | ✅ | ✅ content | |
| 4 Mechanical explorer | 12 selectable components, highlight, 2 exploded views, reset, descriptions | ✅ | ✅ | ✅ content | Exceeds "≥ 5 components" |
| 5 EV & safety | Animated EV energy-flow diagram (3 modes), 9 safety hotspots, simplification labels, text alternatives | ✅ | ✅ | ✅ content | |
| 6 Customization & Garage | Paint/wheels/trim/interior, save, rename, duplicate, delete, restore/reopen, compare | ✅ | ✅ | — | Verified after app restart and offline |
| 7 Learning & AI | 6 modules + quizzes + progress; AI proxy, provider abstraction, grounding, rate limits, offline fallback | ✅ | ✅ | ✅ | Live Claude calls need `ANTHROPIC_API_KEY` on the server; mock provider used for demos/CI |
| 8 Offline & release readiness | Downloadable packs with integrity checks and version upgrades; profiling; reviews; store assets | ✅ packs | ✅ packs | ✅ | 🟡 Device profiling, accessibility audit, privacy policy, store assets, licence approval |

## MVP acceptance checklist (plan §13)

| Criterion | Status | Evidence |
|---|---|---|
| iOS app builds and launches on the documented minimum OS | 🟡 | Builds with deployment target iOS 17.0; launched and tested on iOS 26.1 simulator. An iOS 17 runtime run is still to do |
| Android app builds and launches on the documented minimum version | 🟡 | minSdk 26; launched and tested on API 36.1 emulator. An API 26 device run is still to do |
| Both apps load the same approved fictional UrbanX asset | 🟡 | Same generated scene graph (GLB/USDZ, parity validated); "approved" awaits owner sign-off |
| Camera controls work smoothly on the agreed device matrix | 🟡 | Verified on emulator/simulator; device matrix not yet agreed/measured |
| Paint customization works and can be reset | ✅ | Screenshots 02–03, 12; "Reset paint" / "Reset" |
| Doors/hood animations play and reset | ✅ | Screenshot 03; "Close everything"; animator unit tests |
| Interior hotspots work with accessible alternatives | ✅ | Screenshots 05–06; list + accessibility labels/hints |
| At least five mechanical components selectable | ✅ | 12 components; tap + list |
| Exploded view can be entered and reset | ✅ | Screenshot 08; UI tests on both platforms |
| Configuration saved and restored after app restart | ✅ | Android screenshot 22 (force-stop + relaunch); SwiftData test across contexts |
| Downloaded model and learning content work in airplane mode | ✅ (Android) / 🟡 (iOS) | Android verified in airplane mode (screenshot 22–23). iOS simulator can't toggle airplane mode; verified with backend stopped (screenshot 23) |
| All shipped assets have complete, approved licence records | 🟡 | Complete; approval pending human review |
| No manufacturer marks, branded replicas or paid models | ✅ | Original procedural asset; automated trademark scan |
| Automated tests pass; critical journeys manually verified | ✅ | 15 + 22 + 26 + 8 + 2 + 17 + 2 tests passing; screenshot runs |
| Performance and memory results recorded for representative devices | ⬜ | Requires physical devices |

## Test status (last local run)

| Suite | Result |
|---|---|
| `tools/tests` | 15/15 ✅ · validator 281 checks, 0 failures |
| `backend` `npm test` | 22/22 ✅ |
| Android `:domain:test` | 26/26 ✅ |
| Android `:app:testDebugUnitTest` | 8/8 ✅ |
| Android `connectedDebugAndroidTest` (Pixel 10 Pro emulator, API 36.1) | 2/2 ✅ |
| iOS `AutoVerse3DTests` (iPhone 17 Pro, iOS 26.1) | 17/17 ✅ |
| iOS `AutoVerse3DUITests` | 2/2 ✅ (+ screenshot tours, opt-in) |

## Asset approval status

| Asset | Validation | Licence record | Review |
|---|---|---|---|
| `urbanx_suv_v1` | ✅ passes | complete | ⏳ pending owner approval |
| `studio_platform_v1` | ✅ passes | complete | ⏳ pending owner approval |

## Decisions taken during implementation

| Decision | Reason | Cost if wrong |
|---|---|---|
| Procedural (Python) authoring of UrbanX instead of Blender | Blender unavailable; scripted source is reproducible, reviewable and CI-checked | An artist may want to remodel in Blender; the spec + validator make that a drop-in replacement |
| Single generator writes GLB + USDZ | Guarantees identical node names/pivots on both platforms | None significant |
| Part animations/exploded views as data, not baked clips | Identical behaviour on both engines, reduced-motion support, content-updatable | Complex skeletal animation would need clips later |
| Android: `:app` + `:domain` modules with package-per-feature | Less build config for one vehicle; boundaries match the plan | Promote packages to modules if build times grow |
| Retrofit (not Ktor) | Standard with Hilt/OkHttp; simple API surface | — |
| Filament 1.75.1 | Newest release compatible with compileSdk 35 / AGP 8.7 | Upgrade alongside AGP/compileSdk 37 |
| Backend: dependency-free Node HTTP + `@anthropic-ai/sdk` | Small surface; no paid service needed for the core app | Swap to Supabase/Firebase if accounts/sync are added (plan §8) |
| Default AI model `claude-opus-5-5`, effort `low`, server-side refusal fallbacks | Current default model; short educational answers | Configurable via `AI_MODEL`/`AI_EFFORT` |
| Bundled UrbanX copy + optional downloadable pack | Guarantees offline core experience; pack system ready for more vehicles | Slightly larger app binary (~0.7–1.4 MB) |
| iOS: 5 tabs + Assistant toolbar button; Android: 6 tabs | Avoid iOS "More" overflow; equivalent access | — |
| Configuration schema adds `interiorId` | Plan requires interior colour options | — |
| Licence records left `pending_review` | Author must not approve own work | Release blocked until owner reviews (intended) |

## Blockers / next steps

1. Owner review: approve licence records; confirm vehicle names are trademark-clear; choose a repository/content licence.
2. Run on physical devices: iOS 17 minimum device, Android API 26 device, low/mid/high tiers; record FPS, memory, load time,
   thermal (plan §6).
3. VoiceOver/TalkBack audit; Dynamic Type stress test.
4. Deploy backend (HTTPS), set `ANTHROPIC_API_KEY`, set usage limits; publish privacy policy covering AI processing.
5. Store listing assets (icons exist; screenshots in `/screenshots` can seed store images).
6. Next vehicle (only after the above): TerraX or VoltX via the same pipeline.
