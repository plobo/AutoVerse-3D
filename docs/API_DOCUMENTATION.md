# API documentation

Base URL (development): `http://localhost:8787` (Android emulator: `http://10.0.2.2:8787`). Production must use HTTPS.
All responses are JSON unless noted. Errors use `{ "error": { "code": "…", "message": "…" } }`.

| Method | Path | Description |
|---|---|---|
| GET | `/v1/health` | Status, active AI provider, content version |
| GET | `/v1/vehicles` | Vehicle catalogue (`vehicles.json`) |
| GET | `/v1/vehicles/{vehicleId}` | One vehicle · 404 if unknown |
| GET | `/v1/vehicles/{vehicleId}/components` | Components for a vehicle |
| GET | `/v1/assets/{assetManifestId}` | Manifest + `downloadUrls` (`android`, `ios`, `sceneDescriptor`) · `Cache-Control: no-cache` |
| GET | `/v1/assets/{assetManifestId}/files/{fileName}` | Asset file. Only files listed in the manifest are served. `ETag` = checksum, `X-Checksum-Sha256`, `Cache-Control: immutable`, `Accept-Ranges: bytes`; supports `Range` (206/416) and `If-None-Match` (304) |
| GET | `/v1/learning/modules` | Module summaries + content version |
| GET | `/v1/learning/modules/{moduleId}` | Full module incl. quiz |
| POST | `/v1/ai/automotive-assistant` | AI assistant (below) |
| POST | `/v1/telemetry/events` | Anonymous, allow-listed event counts (below) |

## POST /v1/ai/automotive-assistant

Request (max 8 KB body; `question` 1–500 chars):
```json
{ "question": "How do the front brakes work?", "vehicleId": "urbanx_suv", "componentId": "urbanx_front_brake" }
```
Response 200:
```json
{
  "answer": "…",
  "sources": [{ "id": "component:urbanx_front_brake", "title": "Front brake assembly", "kind": "component" }],
  "scope": "vehicle_specific",
  "safetyNotice": null,
  "refused": false,
  "provider": "anthropic:claude-opus-5-5",
  "disclaimer": "AI answers can be wrong. AutoVerse 3D vehicles are fictional; …",
  "schemaVersion": 1
}
```
* `scope` is `vehicle_specific` when grounding came from the selected vehicle's content, otherwise `general` (the apps label
  general answers).
* `safetyNotice` is set when the question asks for repair/disable/bypass work on brakes, airbags, seat belts, steering, fuel or
  high-voltage systems.
* Errors: `400 invalid_question | question_too_long | unknown_vehicle | unknown_component | invalid_json`, `413 payload_too_large`,
  `429 rate_limited` (+ `Retry-After`), `503 assistant_unavailable` (+ `Retry-After` when retryable). Apps fall back to offline
  content on 429/503/transport errors.

Flow: validate → per-client token-bucket rate limit (`AI_RATE_LIMIT_PER_MINUTE`, default 10) → keyword retrieval over approved
content (top 4) → provider call with a frozen, cached system prompt and the retrieved snippets → response with sources.
Question text is never logged.

### Provider configuration

| Env var | Default | Notes |
|---|---|---|
| `AI_PROVIDER` | `anthropic` if a credential is present, else `mock` | `mock` = deterministic extractive answers (CI/offline demos) |
| `AI_MODEL` | `claude-opus-5-5` | Any Messages-API model id |
| `AI_EFFORT` | `low` | `output_config.effort`; short educational answers |
| `ANTHROPIC_API_KEY` | — | Server-side only. The SDK also accepts an `ant auth login` profile |

The Anthropic provider uses the official `@anthropic-ai/sdk`, enables server-side refusal fallbacks
(`fallbacks: "default"`), caches the system prompt and maps SDK error types to `503` responses.

## POST /v1/telemetry/events

```json
{ "events": [{ "name": "vehicle_viewed" }] }
```
Only `app_open, vehicle_viewed, module_completed, quiz_completed, asset_downloaded, assistant_used` are counted; all other
fields are discarded. Returns `202 { "accepted": n, "rejected": m }`. The v1 apps do not send telemetry.

## Running

```bash
cd backend && npm ci && npm start        # mock provider unless ANTHROPIC_API_KEY is set
npm test                                  # node:test suite
```
