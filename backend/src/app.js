// HTTP API for AutoVerse 3D (see docs/API_DOCUMENTATION.md). Dependency-free routing on node:http.
import { createReadStream, existsSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { REPO_ROOT, loadContent, search } from "./content.js";
import { ProviderUnavailableError, createProvider } from "./providers/index.js";
import { RateLimiter } from "./rateLimiter.js";
import { SAFETY_NOTICE, isSafetyCritical } from "./safety.js";

export const AI_DISCLAIMER = "AI answers can be wrong. AutoVerse 3D vehicles are fictional; this is educational information, not repair advice.";
const TELEMETRY_EVENTS = new Set(["app_open", "vehicle_viewed", "module_completed", "quiz_completed", "asset_downloaded", "assistant_used"]);
const MAX_BODY_BYTES = 8 * 1024;

export function loadConfig(env = process.env) {
  const hasKey = Boolean(env.ANTHROPIC_API_KEY || env.ANTHROPIC_AUTH_TOKEN);
  return {
    port: Number(env.PORT ?? 8787),
    provider: env.AI_PROVIDER ?? (hasKey ? "anthropic" : "mock"),
    model: env.AI_MODEL ?? "claude-opus-5-5",
    effort: env.AI_EFFORT ?? "low",
    rateLimitPerMinute: Number(env.AI_RATE_LIMIT_PER_MINUTE ?? 10),
    maxQuestionChars: Number(env.AI_MAX_QUESTION_CHARS ?? 500),
    trustProxy: env.TRUST_PROXY === "1",
    assetsDir: env.ASSETS_DIR ?? path.join(REPO_ROOT, "assets"),
  };
}

class HttpError extends Error {
  constructor(status, code, message, headers = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.headers = headers;
  }
}

function sendJson(res, status, body, headers = {}) {
  const data = JSON.stringify(body);
  res.writeHead(status, { "content-type": "application/json; charset=utf-8", "content-length": Buffer.byteLength(data), ...headers });
  res.end(data);
}

async function readJsonBody(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new HttpError(413, "payload_too_large", "Request body too large");
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
  } catch {
    throw new HttpError(400, "invalid_json", "Body must be valid JSON");
  }
}

export function createApp(config = loadConfig(), deps = {}) {
  const content = deps.content ?? loadContent();
  const provider = deps.provider ?? createProvider(config);
  const limiter = deps.rateLimiter ?? new RateLimiter({ capacity: config.rateLimitPerMinute, refillPerMinute: config.rateLimitPerMinute });
  const telemetryCounts = new Map();
  const manifestsDir = path.join(config.assetsDir, "manifests");
  const buildDir = path.join(config.assetsDir, "build");

  const clientKey = (req) => {
    const fwd = config.trustProxy ? String(req.headers["x-forwarded-for"] ?? "").split(",")[0].trim() : "";
    return fwd || req.socket.remoteAddress || "unknown";
  };

  const vehicleById = (id) => content.vehicles.vehicles.find((v) => v.id === id);

  function loadManifest(id) {
    if (!/^[a-z0-9_]+$/.test(id)) throw new HttpError(400, "invalid_id", "Invalid asset manifest id");
    const file = path.join(manifestsDir, `${id}.json`);
    if (!existsSync(file)) throw new HttpError(404, "not_found", "Asset manifest not found");
    return JSON.parse(readFileSync(file, "utf8"));
  }

  async function handleAssistant(req, res) {
    const rl = limiter.take(clientKey(req));
    if (!rl.allowed) {
      throw new HttpError(429, "rate_limited", "Too many assistant requests. Please wait and try again.", { "retry-after": String(rl.retryAfterSeconds) });
    }
    const body = await readJsonBody(req);
    const question = typeof body.question === "string" ? body.question.trim() : "";
    if (!question) throw new HttpError(400, "invalid_question", "question is required");
    if (question.length > config.maxQuestionChars) throw new HttpError(400, "question_too_long", `question must be at most ${config.maxQuestionChars} characters`);
    const vehicle = body.vehicleId ? vehicleById(String(body.vehicleId)) : undefined;
    if (body.vehicleId && !vehicle) throw new HttpError(400, "unknown_vehicle", "Unknown vehicleId");
    const component = body.componentId ? content.components.components.find((c) => c.id === body.componentId) : undefined;
    if (body.componentId && !component) throw new HttpError(400, "unknown_component", "Unknown componentId");

    const query = [question, component?.name].filter(Boolean).join(" ");
    const hits = search(content.docs, query, 4);
    const sources = hits.map((h) => h.doc);
    const safetyCritical = isSafetyCritical(question);

    let result;
    try {
      result = await provider.answer({ question, vehicle, component, sources });
    } catch (error) {
      if (error instanceof ProviderUnavailableError) {
        throw new HttpError(503, "assistant_unavailable", "The AI assistant is temporarily unavailable. Offline learning content is still available.",
          error.retryable ? { "retry-after": "30" } : {});
      }
      throw error;
    }
    const vehicleSpecific = sources.some((s) => s.vehicleId && vehicle && s.vehicleId === vehicle.id);
    sendJson(res, 200, {
      answer: result.text,
      sources: sources.map((s) => ({ id: s.id, title: s.title, kind: s.kind })),
      scope: vehicleSpecific ? "vehicle_specific" : "general",
      safetyNotice: safetyCritical ? SAFETY_NOTICE : null,
      refused: Boolean(result.refused),
      provider: provider.name,
      disclaimer: AI_DISCLAIMER,
      schemaVersion: 1,
    });
  }

  function serveAssetFile(req, res, manifestId, fileName) {
    const manifest = loadManifest(manifestId);
    const variants = [{ fileName: manifest.fileName, checksum: manifest.checksum, format: manifest.format },
      ...Object.values(manifest.platformVariants ?? {}), manifest.sceneDescriptor];
    const entry = variants.find((v) => v && v.fileName === fileName);
    if (!entry) throw new HttpError(404, "not_found", "File is not part of this manifest");
    const file = path.join(buildDir, entry.fileName);
    const size = statSync(file).size;
    const etag = `"${entry.checksum}"`;
    const headers = {
      "content-type": entry.format === "json" ? "application/json" : entry.format === "usdz" ? "model/vnd.usdz+zip" : "model/gltf-binary",
      "accept-ranges": "bytes", etag, "cache-control": "public, max-age=31536000, immutable", "x-checksum-sha256": entry.checksum,
    };
    if (req.headers["if-none-match"] === etag) {
      res.writeHead(304, headers);
      return res.end();
    }
    const range = req.headers.range;
    if (range) {
      const m = /^bytes=(\d+)-(\d*)$/.exec(range);
      const start = m ? Number(m[1]) : NaN;
      const end = m && m[2] ? Math.min(Number(m[2]), size - 1) : size - 1;
      if (!m || start >= size || start > end) {
        res.writeHead(416, { "content-range": `bytes */${size}` });
        return res.end();
      }
      res.writeHead(206, { ...headers, "content-range": `bytes ${start}-${end}/${size}`, "content-length": end - start + 1 });
      return createReadStream(file, { start, end }).pipe(res);
    }
    res.writeHead(200, { ...headers, "content-length": size });
    return createReadStream(file).pipe(res);
  }

  async function route(req, res) {
    const url = new URL(req.url, "http://localhost");
    const parts = url.pathname.split("/").filter(Boolean);
    const method = req.method;
    if (parts[0] !== "v1") throw new HttpError(404, "not_found", "Not found");
    const [, resource, id, sub, subId] = parts;

    if (method === "GET" && resource === "health" && !id) {
      return sendJson(res, 200, { status: "ok", provider: provider.name, contentVersion: content.vehicles.contentVersion });
    }
    if (method === "GET" && resource === "vehicles") {
      if (!id) return sendJson(res, 200, content.vehicles);
      const vehicle = vehicleById(id);
      if (!vehicle) throw new HttpError(404, "not_found", "Vehicle not found");
      if (!sub) return sendJson(res, 200, vehicle);
      if (sub === "components" && !subId) {
        return sendJson(res, 200, { vehicleId: id, components: content.components.components.filter((c) => c.vehicleId === id), schemaVersion: 1 });
      }
    }
    if (method === "GET" && resource === "assets" && id) {
      if (!sub) {
        const manifest = loadManifest(id);
        const base = `/v1/assets/${id}/files/`;
        return sendJson(res, 200, { ...manifest, downloadUrls: {
          android: base + manifest.fileName,
          ios: base + manifest.platformVariants.ios.fileName,
          sceneDescriptor: base + manifest.sceneDescriptor.fileName,
        } }, { "cache-control": "no-cache" });
      }
      if (sub === "files" && subId) return serveAssetFile(req, res, id, decodeURIComponent(subId));
    }
    if (method === "GET" && resource === "learning" && id === "modules") {
      if (!sub) {
        return sendJson(res, 200, {
          contentVersion: content.learning.contentVersion,
          modules: content.learning.modules.map(({ id: mid, title, summary, estimatedMinutes }) => ({ id: mid, title, summary, estimatedMinutes })),
        });
      }
      const mod = content.learning.modules.find((m) => m.id === sub);
      if (!mod) throw new HttpError(404, "not_found", "Module not found");
      return sendJson(res, 200, mod);
    }
    if (method === "POST" && resource === "ai" && id === "automotive-assistant" && !sub) return handleAssistant(req, res);
    if (method === "POST" && resource === "telemetry" && id === "events" && !sub) {
      const body = await readJsonBody(req);
      const events = Array.isArray(body.events) ? body.events.slice(0, 50) : [];
      let accepted = 0;
      for (const e of events) {
        // Only an allow-listed event name and a coarse property set are kept; no identifiers, no free text.
        if (e && TELEMETRY_EVENTS.has(e.name)) {
          telemetryCounts.set(e.name, (telemetryCounts.get(e.name) ?? 0) + 1);
          accepted += 1;
        }
      }
      return sendJson(res, 202, { accepted, rejected: events.length - accepted });
    }
    throw new HttpError(404, "not_found", "Not found");
  }

  const handler = async (req, res) => {
    res.setHeader("x-content-type-options", "nosniff");
    res.setHeader("referrer-policy", "no-referrer");
    try {
      await route(req, res);
    } catch (error) {
      if (error instanceof HttpError) {
        return sendJson(res, error.status, { error: { code: error.code, message: error.message } }, error.headers);
      }
      // Never log request bodies (they may contain user questions); log only the error type.
      console.error(`internal error on ${req.method} ${new URL(req.url, "http://x").pathname}: ${error?.name}`);
      if (!res.headersSent) sendJson(res, 500, { error: { code: "internal", message: "Internal server error" } });
    }
  };
  handler.telemetryCounts = telemetryCounts;
  return handler;
}
