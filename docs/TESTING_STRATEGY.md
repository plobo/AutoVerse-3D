# Testing strategy

| Layer | Where | What is covered | Count (Nov 2026) |
|---|---|---|---|
| Asset pipeline | `tools/tests` (unittest) | Mesh winding/normals, mirroring, pivots, sRGB→linear; full repository validation; checksum/size tamper detection; pivot and metadata-node mismatches; malformed GLB handling; licence registry completeness, coverage, release gating, https source rule; trademark scan | 15 |
| Asset validation (CI gate) | `tools/validate/validate_assets.py` | 281 checks: schemas, manifests, checksums, node uniqueness/hierarchy/pivots, metadata references, budgets, dimensions, USDZ parity, licences, trademarks | — |
| Backend | `backend/test` (node:test) | Retrieval scoring, safety classifier, rate limiter, prompt construction, Anthropic provider request shape (fake client), refusal and transport-error mapping, all endpoints, Range/ETag/304 downloads, path-traversal refusal, input validation, 413, 429 + Retry-After, 503 without leaking details, telemetry allow-list | 22 |
| Android domain | `android/domain` (JUnit) | Contract decoding, search parity, quiz scoring, learning progress, checksum + tamper, manifest validation, semver, download state machine (happy path, pause/resume, failure, retry, bad checksum, upgrades), visibility rules, tap resolution, orbit/interior camera math & limits, door/hood rotation direction, part animator & reduced motion, configuration rules | 26 |
| Android app (JVM) | `android/app/src/test` | Assistant request construction + response parsing via MockWebServer; 429/503/network-failure fallbacks; Room entity round-trips | 8 |
| Android UI | `android/app/src/androidTest` (Compose + Hilt) | All six tabs; open UrbanX, mechanical exploded view + reset, interior viewpoints | 2 |
| iOS unit | `ios/AutoVerse3DTests` (XCTest) | Same domain coverage as Android + bundled content, SwiftData persistence across contexts, request construction/parsing, offline/rate-limit fallbacks | 17 |
| iOS UI | `ios/AutoVerse3DUITests` | Main navigation + assistant sheet; open UrbanX → explode → customize → save → appears in Garage. Screenshot tour (opt-in) | 2 (+2 opt-in) |

Both apps' domain tests read the **real** `/shared` files and committed assets, so a contract change that breaks either
platform fails CI.

## Manual / device verification performed

On the Android 16 emulator and iPhone 17 Pro simulator (screenshots in `/screenshots`): load, rotate, preset cameras,
recolour, lights, doors/hood, interior hotspots, mechanical exploded view + selection, safety hotspots, wheel/trim/interior
customization, save and reopen after restart, pack download + checksum verification, airplane-mode launch with the saved
design and the downloaded pack, offline assistant fallback, quiz pass.

## Not yet done (requires hardware)

* Frame rate, memory, battery and thermal profiling on low/mid/high-tier **physical** devices (plan §6, §13).
* VoiceOver and TalkBack passes on devices (labels, hints and non-3D lists are implemented; needs a human audit).
* Interrupted-download tests on real flaky networks (pause/resume/retry logic is unit-tested and exercised on emulators).

## CI

`.github/workflows/ci.yml`: assets (validation, tests, reproducibility), backend, Android (domain + JVM tests + debug build),
iOS (XcodeGen + unit tests on a simulator).
