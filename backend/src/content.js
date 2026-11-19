// Loads the app-owned, versioned content from /shared and provides keyword retrieval.
// The same scoring algorithm is implemented in the iOS and Android apps for the offline fallback
// (see docs/DATA_MODELS.md#offline-search), so online and offline results stay consistent.
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
export const REPO_ROOT = path.resolve(here, "..", "..");

const readJson = (rel) => JSON.parse(readFileSync(path.join(REPO_ROOT, rel), "utf8"));

export const STOPWORDS = new Set(
  "a an and are as at be by can do does for from how i in is it its me my of on or the this to what when where which who why with you your".split(" "),
);

export function tokenize(text) {
  return (text.toLowerCase().match(/[a-z0-9]+/g) ?? []).filter((t) => t.length > 1 && !STOPWORDS.has(t));
}

/** Score = 3 x title-term hits + 1 x body-term hits, summed over distinct query terms. */
export function scoreDocument(queryTerms, doc) {
  const title = new Set(tokenize(doc.title));
  const body = tokenize(doc.text);
  let score = 0;
  for (const term of new Set(queryTerms)) {
    if (title.has(term)) score += 3;
    score += body.filter((t) => t === term).length;
  }
  return score;
}

export function search(docs, query, limit = 4) {
  const terms = tokenize(query);
  if (terms.length === 0) return [];
  return docs
    .map((doc) => ({ doc, score: scoreDocument(terms, doc) }))
    .filter((r) => r.score > 0)
    .sort((a, b) => b.score - a.score || a.doc.id.localeCompare(b.doc.id))
    .slice(0, limit);
}

export function loadContent() {
  const vehicles = readJson("shared/data/vehicles.json");
  const components = readJson("shared/data/components.json");
  const hotspots = readJson("shared/data/hotspots.json");
  const learning = readJson("shared/content/learning_modules.json");
  const ev = readJson("shared/content/ev_lab.json");

  const docs = [];
  for (const m of learning.modules) {
    for (const s of m.sections) {
      docs.push({ id: `learning:${m.id}:${s.id}`, kind: "learning", title: `${m.title}: ${s.heading}`, text: s.body });
    }
  }
  for (const c of components.components) {
    docs.push({
      id: `component:${c.id}`, kind: "component", vehicleId: c.vehicleId, title: c.name,
      text: [c.description, c.purpose, c.detail].filter(Boolean).join(" "),
    });
  }
  for (const h of hotspots.hotspots) {
    docs.push({ id: `hotspot:${h.id}`, kind: "hotspot", vehicleId: hotspots.vehicleId, title: h.title, text: `${h.summary} ${h.detail}` });
  }
  for (const n of ev.nodes) {
    docs.push({ id: `ev:${n.id}`, kind: "ev", title: n.title, text: n.summary });
  }
  return { vehicles, components, hotspots, learning, ev, docs };
}
