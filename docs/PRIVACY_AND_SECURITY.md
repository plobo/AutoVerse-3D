# Privacy and security

## Data the apps store (on device only)

Saved configurations, learning progress, installed asset packs and the last-explored vehicle/mode. No accounts, no personal
data, no advertising identifiers. Android disables auto-backup (`allowBackup=false`).

## Network

* The apps call only the AutoVerse backend. Release builds use HTTPS; cleartext is limited to local development hosts
  (`NSAllowsLocalNetworking`; Android `network_security_config.xml` for `10.0.2.2`/`localhost`).
* Downloaded packs are accepted only after the manifest validates (id, version, safe filename, checksum format, minimum app
  version, vehicle id) and the file's SHA-256 matches. Mismatched files are deleted; the working copy is replaced only after
  the new one is verified.
* The backend serves only files listed in a manifest (no path traversal), caps request bodies at 8 KB and sets
  `nosniff`/`no-referrer` headers.

## AI assistant

* Provider secrets (`ANTHROPIC_API_KEY`) exist only on the server. The apps contain no keys.
* Requests contain only the question text and optional vehicle/component ids. No device identifiers or personal data are
  sent to the AI provider. Question text is not logged by the backend.
* Abuse controls: per-client token-bucket rate limiting, 500-character limit, safety notice and system-prompt rules against
  repair instructions for safety-critical systems, server-side refusal fallbacks, and an error mapping that never forwards
  provider details to clients.
* **Before release:** document the provider's data retention and processing terms in the store privacy policy and disclose
  third-party AI processing in the app (the assistant screen already labels answers as AI-generated and possibly wrong).

## Telemetry

The backend accepts only allow-listed, anonymous event names and discards everything else. The v1 apps send none.

## Dependencies

Pinned in `android/gradle/libs.versions.toml`, `backend/package-lock.json` and `tools/requirements.txt`. Review licences and
update regularly (Filament is Apache 2.0; Jetpack, Hilt, Retrofit, OkHttp are Apache 2.0; usd-core is the Tomorrow Open
Source Technology License (modified Apache 2.0, tooling only, not shipped); `@anthropic-ai/sdk` is MIT).

## Asset rights

See [ASSET_LICENSING.md](../ASSET_LICENSING.md) for the registry, review gate and takedown process.
