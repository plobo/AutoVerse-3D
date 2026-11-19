# Contributing

## Branching and commits

* `main` is always releasable and protected by CI. Work on `feature/<short-name>` or `fix/<short-name>` branches and merge via
  pull request after review.
* Commit messages: imperative summary line (≤ 72 chars), body explaining *why*.

## Rules that CI enforces

1. **Assets:** only original or verifiably licensed assets. Update `shared/licensing/asset_registry.json` (then run
   `tools/validate/render_licensing_md.py`) *before* adding a file. Rebuild with `tools/assetgen/build.py --update-registry`.
   CI rebuilds the assets and fails if committed files differ.
2. **Contracts:** change `shared/` schemas and data together with both apps' models; bump `schemaVersion` on breaking changes.
3. **Parity:** features land on iOS and Android together, or the gap is recorded in `docs/IMPLEMENTATION_STATUS.md` with an
   equivalent user outcome.
4. **Tests:** domain logic, persistence, asset validation and networking need unit tests; critical journeys need UI tests.
   A feature is not "done" until it builds and its tests pass.
5. **Secrets:** never commit `.env` files or keys; AI credentials stay on the backend.

## Coding standards

* Kotlin: official style, Compose state hoisting, ViewModels expose `StateFlow`, no Filament types outside `core/renderer`.
* Swift: Swift 6 strict concurrency, UI state in `@MainActor @Observable` models, no RealityKit types outside the renderer.
* Python: standard library first; type hints. Node: ES modules, no framework for the small API.
* Accessibility: every control has a label; every 3D interaction has a list/button alternative; respect reduced motion;
  never rely on colour alone.

## Documentation templates

New feature PRs update: `docs/IMPLEMENTATION_STATUS.md` (phase, parity, tests), the relevant doc in `docs/`, and
screenshots in `screenshots/` when UI changes.
