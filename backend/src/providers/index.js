// AI provider abstraction. Each provider implements:
//   name: string
//   answer({ question, vehicle, component, sources }) -> Promise<{ text: string, refused?: boolean }>
// `sources` are pre-retrieved, app-owned content snippets the answer must be grounded in.
// Adding a provider = a new module exporting a factory with this shape; nothing in the apps changes.
import { createAnthropicProvider } from "./anthropic.js";
import { createMockProvider } from "./mock.js";

export class ProviderUnavailableError extends Error {
  constructor(message, { retryable = true } = {}) {
    super(message);
    this.name = "ProviderUnavailableError";
    this.retryable = retryable;
  }
}

export function createProvider(config) {
  switch (config.provider) {
    case "anthropic":
      return createAnthropicProvider(config);
    case "mock":
      return createMockProvider();
    default:
      throw new Error(`Unknown AI_PROVIDER "${config.provider}"`);
  }
}

export const SYSTEM_PROMPT = `You are the AutoVerse 3D automotive learning assistant inside an educational mobile app.
The app features original, fictional vehicles (for example "UrbanX", a fictional compact SUV). They are not real products.

How to answer:
- Answer in plain language for curious non-experts, in at most about 150 words.
- Ground your answer in the CONTEXT snippets provided with each question. When the context does not cover something, say clearly that you are not sure or that it is general knowledge, rather than guessing.
- Never invent specifications, prices, performance figures or ratings for the fictional vehicles. If asked, say the app does not define them.
- The fictional vehicles have not been crash-tested or certified; never claim otherwise.
- Explain concepts only. Do not give step-by-step repair, maintenance, modification or disabling instructions for safety-critical systems (brakes, airbags, seat belts, steering, fuel, high-voltage EV systems). Recommend a qualified technician instead.
- Do not ask for or repeat personal information.
- Do not mention real manufacturers' brands or model names.`;

export function buildUserPrompt({ question, vehicle, component, sources }) {
  const lines = [];
  lines.push(`SELECTED VEHICLE: ${vehicle ? `${vehicle.name} (${vehicle.categoryLabel ?? vehicle.category}, fictional)` : "none"}`);
  lines.push(`SELECTED COMPONENT: ${component ? component.name : "none"}`);
  lines.push("CONTEXT:");
  if (sources.length === 0) lines.push("(no matching app content)");
  sources.forEach((s, i) => lines.push(`[${i + 1}] ${s.title}: ${s.text}`));
  lines.push("");
  lines.push(`QUESTION: ${question}`);
  return lines.join("\n");
}
