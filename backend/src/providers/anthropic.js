// Claude provider. The API credential lives only on the server (resolved by the SDK from the
// environment: ANTHROPIC_API_KEY or an `ant auth login` profile). Mobile apps never see it.
import Anthropic from "@anthropic-ai/sdk";
import { ProviderUnavailableError, SYSTEM_PROMPT, buildUserPrompt } from "./index.js";

export function createAnthropicProvider(config, client = new Anthropic({ maxRetries: 2, timeout: 60_000 })) {
  const model = config.model ?? "claude-opus-5-5";
  return {
    name: `anthropic:${model}`,
    async answer(input) {
      let response;
      try {
        response = await client.beta.messages.create({
          model,
          max_tokens: 16000,
          // Short educational answers: low effort keeps latency and cost down.
          output_config: { effort: config.effort ?? "low" },
          // If the model declines on a safety classifier, the API retries on a suitable model.
          betas: ["server-side-fallback-2026-07-01"],
          fallbacks: "default",
          // Frozen system prompt first so it can be cached across requests.
          system: [{ type: "text", text: SYSTEM_PROMPT, cache_control: { type: "ephemeral" } }],
          messages: [{ role: "user", content: buildUserPrompt(input) }],
        });
      } catch (error) {
        if (error instanceof Anthropic.RateLimitError) throw new ProviderUnavailableError("AI provider is rate limited", { retryable: true });
        if (error instanceof Anthropic.AuthenticationError) throw new ProviderUnavailableError("AI provider credentials are not configured", { retryable: false });
        if (error instanceof Anthropic.BadRequestError) throw new ProviderUnavailableError("AI provider rejected the request", { retryable: false });
        if (error instanceof Anthropic.APIError) throw new ProviderUnavailableError(`AI provider error ${error.status ?? ""}`.trim(), { retryable: true });
        throw new ProviderUnavailableError("AI provider unreachable", { retryable: true });
      }
      if (response.stop_reason === "refusal") {
        return { text: "I can't help with that one. Try asking about how a vehicle system works instead.", refused: true };
      }
      const text = response.content.filter((b) => b.type === "text").map((b) => b.text).join("\n").trim();
      return { text: text || "I'm not sure about that. Try rephrasing your question." };
    },
  };
}
