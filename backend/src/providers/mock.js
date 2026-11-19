// Deterministic provider for local development, CI and deployments without an AI subscription.
// It composes an extractive answer from the retrieved, approved content — it never invents facts.
export function createMockProvider() {
  return {
    name: "mock:extractive",
    async answer({ sources }) {
      if (sources.length === 0) {
        return { text: "I'm not sure — the AutoVerse 3D learning content doesn't cover that yet. Try asking about engines, brakes, suspension, EVs, safety systems or the UrbanX interior." };
      }
      const [best, ...rest] = sources;
      const extra = rest.length ? ` Related topics: ${rest.map((s) => s.title).join("; ")}.` : "";
      return { text: `${best.title} — ${best.text}${extra}` };
    },
  };
}
