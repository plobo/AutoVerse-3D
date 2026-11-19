// Detects questions that ask for safety-critical repair procedures. The assistant still answers
// conceptually, but never with step-by-step repair instructions, and always adds this notice.
const PATTERNS = [
  /\b(bleed|bleeding|replace|replacing|remove|removing|disconnect|disable|bypass|repair|fix|install)\b.*\b(brake|brakes|airbag|airbags|seat ?belt|steering|high[- ]voltage|battery pack|fuel (line|tank|pump)|abs|srs)\b/i,
  /\b(brake|airbag|airbags|seat ?belt|steering|high[- ]voltage|battery pack|fuel (line|tank|pump)|abs|srs)\b.*\b(bleed|replace|remove|disconnect|disable|bypass|repair|fix|install)\b/i,
  /\bhow (do|can|to) i\b.*\b(work on|open up|take apart)\b/i,
];

export const SAFETY_NOTICE =
  "This touches a safety-critical system. AutoVerse 3D explains concepts only — it does not provide repair instructions. " +
  "Work on brakes, airbags, steering, fuel or high-voltage systems should be done by a qualified technician.";

export function isSafetyCritical(question) {
  return PATTERNS.some((p) => p.test(question));
}
