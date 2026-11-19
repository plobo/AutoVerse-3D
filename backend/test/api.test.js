import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { createServer } from "node:http";
import path from "node:path";
import { after, before, describe, it } from "node:test";
import { createApp, loadConfig } from "../src/app.js";
import { REPO_ROOT, loadContent, scoreDocument, search, tokenize } from "../src/content.js";
import { createAnthropicProvider } from "../src/providers/anthropic.js";
import { ProviderUnavailableError, buildUserPrompt } from "../src/providers/index.js";
import { createMockProvider } from "../src/providers/mock.js";
import { RateLimiter } from "../src/rateLimiter.js";
import { isSafetyCritical } from "../src/safety.js";

async function withServer(handler) {
  const server = createServer(handler);
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  return { base, close: () => new Promise((r) => server.close(r)) };
}

const config = { ...loadConfig({}), provider: "mock", rateLimitPerMinute: 100 };

describe("content retrieval", () => {
  it("tokenizes and drops stopwords", () => {
    assert.deepEqual(tokenize("How does the INVERTER work?"), ["inverter", "work"]);
  });
  it("weights title matches above body matches", () => {
    const terms = ["radiator"];
    assert.equal(scoreDocument(terms, { title: "Radiator", text: "radiator coolant" }), 4);
    assert.equal(scoreDocument(terms, { title: "Fan", text: "near the radiator" }), 1);
  });
  it("finds the inverter section for an EV question", () => {
    const { docs } = loadContent();
    const hits = search(docs, "what does the inverter do", 3);
    assert.ok(hits.length > 0);
    assert.match(hits[0].doc.title, /Inverter/i);
  });
  it("returns nothing for empty queries", () => {
    assert.deepEqual(search(loadContent().docs, "the and of"), []);
  });
});

describe("safety classifier", () => {
  it("flags repair requests on safety-critical systems", () => {
    assert.ok(isSafetyCritical("How do I bleed my brakes?"));
    assert.ok(isSafetyCritical("can I disable the airbag"));
    assert.ok(isSafetyCritical("Replace the high-voltage battery pack myself"));
  });
  it("does not flag conceptual questions", () => {
    assert.ok(!isSafetyCritical("How do disc brakes work?"));
    assert.ok(!isSafetyCritical("What is regenerative braking?"));
  });
});

describe("rate limiter", () => {
  it("allows the capacity then blocks with a retry-after, and refills", () => {
    let t = 0;
    const rl = new RateLimiter({ capacity: 2, refillPerMinute: 60, now: () => t });
    assert.ok(rl.take("a").allowed);
    assert.ok(rl.take("a").allowed);
    const blocked = rl.take("a");
    assert.equal(blocked.allowed, false);
    assert.equal(blocked.retryAfterSeconds, 1);
    assert.ok(rl.take("b").allowed, "keys are independent");
    t += 1000;
    assert.ok(rl.take("a").allowed);
  });
});

describe("providers", () => {
  it("builds a grounded prompt with vehicle context", () => {
    const prompt = buildUserPrompt({ question: "Q?", vehicle: { name: "UrbanX", categoryLabel: "Compact urban SUV" }, component: null,
      sources: [{ title: "Radiator", text: "cools" }] });
    assert.match(prompt, /UrbanX \(Compact urban SUV, fictional\)/);
    assert.match(prompt, /\[1\] Radiator: cools/);
    assert.match(prompt, /QUESTION: Q\?/);
  });
  it("mock provider says when content is missing instead of inventing", async () => {
    const r = await createMockProvider().answer({ question: "x", sources: [] });
    assert.match(r.text, /not sure/);
  });
  it("anthropic provider sends a server-side request with cached system prompt and fallbacks", async () => {
    let captured;
    const fake = { beta: { messages: { create: async (req) => { captured = req; return { stop_reason: "end_turn", content: [{ type: "text", text: "Answer." }] }; } } } };
    const p = createAnthropicProvider({ model: "claude-opus-5-5", effort: "low" }, fake);
    const r = await p.answer({ question: "Q", vehicle: null, component: null, sources: [] });
    assert.equal(r.text, "Answer.");
    assert.equal(captured.model, "claude-opus-5-5");
    assert.equal(captured.output_config.effort, "low");
    assert.equal(captured.fallbacks, "default");
    assert.equal(captured.system[0].cache_control.type, "ephemeral");
    assert.equal(captured.temperature, undefined);
  });
  it("anthropic provider maps refusals and transport failures", async () => {
    const refusing = { beta: { messages: { create: async () => ({ stop_reason: "refusal", content: [] }) } } };
    const r = await createAnthropicProvider({}, refusing).answer({ question: "Q", sources: [] });
    assert.equal(r.refused, true);
    const failing = { beta: { messages: { create: async () => { throw new TypeError("fetch failed"); } } } };
    await assert.rejects(createAnthropicProvider({}, failing).answer({ question: "Q", sources: [] }), ProviderUnavailableError);
  });
});

describe("HTTP API", () => {
  let srv;
  before(async () => { srv = await withServer(createApp(config)); });
  after(async () => srv.close());

  it("lists vehicles and components", async () => {
    const v = await (await fetch(`${srv.base}/v1/vehicles`)).json();
    assert.equal(v.vehicles[0].id, "urbanx_suv");
    const c = await (await fetch(`${srv.base}/v1/vehicles/urbanx_suv/components`)).json();
    assert.ok(c.components.filter((x) => x.module === "mechanical").length >= 5);
    assert.equal((await fetch(`${srv.base}/v1/vehicles/nope`)).status, 404);
  });

  it("serves learning modules", async () => {
    const list = await (await fetch(`${srv.base}/v1/learning/modules`)).json();
    assert.ok(list.modules.length >= 5);
    const mod = await (await fetch(`${srv.base}/v1/learning/modules/ev_fundamentals`)).json();
    assert.ok(mod.quiz.length > 0);
  });

  it("serves asset manifests and files with integrity metadata, caching and ranges", async () => {
    const m = await (await fetch(`${srv.base}/v1/assets/urbanx_suv_v1`)).json();
    assert.equal(m.downloadUrls.android, "/v1/assets/urbanx_suv_v1/files/urbanx_suv_v1.glb");
    const res = await fetch(srv.base + m.downloadUrls.android);
    assert.equal(res.status, 200);
    const buf = Buffer.from(await res.arrayBuffer());
    assert.equal(`sha256:${createHash("sha256").update(buf).digest("hex")}`, m.checksum);
    assert.equal(res.headers.get("etag"), `"${m.checksum}"`);
    const part = await fetch(srv.base + m.downloadUrls.android, { headers: { range: "bytes=10-19" } });
    assert.equal(part.status, 206);
    assert.equal(Buffer.from(await part.arrayBuffer()).length, 10);
    const notModified = await fetch(srv.base + m.downloadUrls.android, { headers: { "if-none-match": `"${m.checksum}"` } });
    assert.equal(notModified.status, 304);
    assert.equal((await fetch(`${srv.base}/v1/assets/urbanx_suv_v1/files/..%2F..%2Fpackage.json`)).status, 404);
    assert.equal((await fetch(`${srv.base}/v1/assets/../etc`)).status, 404);
  });

  it("answers assistant questions with grounded sources", async () => {
    const res = await fetch(`${srv.base}/v1/ai/automotive-assistant`, {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ question: "What does the radiator do?", vehicleId: "urbanx_suv" }),
    });
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.ok(body.sources.some((s) => s.id === "component:urbanx_radiator"));
    assert.equal(body.scope, "vehicle_specific");
    assert.equal(body.safetyNotice, null);
    assert.match(body.disclaimer, /fictional/);
  });

  it("adds a safety notice for repair questions", async () => {
    const res = await fetch(`${srv.base}/v1/ai/automotive-assistant`, {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ question: "How do I bleed the brakes?" }),
    });
    assert.match((await res.json()).safetyNotice, /qualified technician/);
  });

  it("validates assistant input", async () => {
    const post = (body) => fetch(`${srv.base}/v1/ai/automotive-assistant`, { method: "POST", headers: { "content-type": "application/json" }, body });
    assert.equal((await post(JSON.stringify({}))).status, 400);
    assert.equal((await post("{not json")).status, 400);
    assert.equal((await post(JSON.stringify({ question: "x".repeat(501) }))).status, 400);
    assert.equal((await post(JSON.stringify({ question: "hi", vehicleId: "fake" }))).status, 400);
    assert.equal((await post(JSON.stringify({ question: "x".repeat(9000) }))).status, 413);
  });

  it("accepts only allow-listed telemetry events", async () => {
    const res = await fetch(`${srv.base}/v1/telemetry/events`, { method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ events: [{ name: "app_open" }, { name: "email_captured", email: "x@y.z" }] }) });
    assert.equal(res.status, 202);
    assert.deepEqual(await res.json(), { accepted: 1, rejected: 1 });
  });
});

describe("AI proxy failure modes", () => {
  it("rate limits the assistant with 429 + Retry-After", async () => {
    const srv = await withServer(createApp({ ...config, rateLimitPerMinute: 1 }));
    const post = () => fetch(`${srv.base}/v1/ai/automotive-assistant`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ question: "brakes" }) });
    assert.equal((await post()).status, 200);
    const limited = await post();
    assert.equal(limited.status, 429);
    assert.ok(Number(limited.headers.get("retry-after")) > 0);
    await srv.close();
  });

  it("returns 503 when the provider fails, without leaking details", async () => {
    const provider = { name: "broken", answer: async () => { throw new ProviderUnavailableError("upstream exploded secret-key-123"); } };
    const srv = await withServer(createApp(config, { provider }));
    const res = await fetch(`${srv.base}/v1/ai/automotive-assistant`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ question: "brakes" }) });
    assert.equal(res.status, 503);
    const text = await res.text();
    assert.ok(!text.includes("secret-key-123"));
    assert.match(text, /Offline learning content/);
    await srv.close();
  });
});

describe("configuration", () => {
  it("defaults to the mock provider when no credential is configured", () => {
    assert.equal(loadConfig({}).provider, "mock");
    assert.equal(loadConfig({ ANTHROPIC_API_KEY: "k" }).provider, "anthropic");
  });
  it("shared content files used by the server exist", () => {
    assert.ok(readFileSync(path.join(REPO_ROOT, "shared/content/learning_modules.json"), "utf8").length > 0);
  });
});
