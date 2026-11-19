// In-memory token-bucket rate limiter keyed by client identity (IP or anonymous install id).
// For multi-instance deployments swap this for a shared store (e.g. Redis) behind the same interface.
export class RateLimiter {
  constructor({ capacity, refillPerMinute, now = () => Date.now(), maxKeys = 10_000 }) {
    this.capacity = capacity;
    this.refillPerMs = refillPerMinute / 60_000;
    this.now = now;
    this.maxKeys = maxKeys;
    this.buckets = new Map();
  }

  /** @returns {{allowed: boolean, retryAfterSeconds: number, remaining: number}} */
  take(key) {
    const t = this.now();
    let b = this.buckets.get(key);
    if (!b) {
      if (this.buckets.size >= this.maxKeys) this.buckets.delete(this.buckets.keys().next().value);
      b = { tokens: this.capacity, last: t };
      this.buckets.set(key, b);
    }
    b.tokens = Math.min(this.capacity, b.tokens + (t - b.last) * this.refillPerMs);
    b.last = t;
    if (b.tokens >= 1) {
      b.tokens -= 1;
      return { allowed: true, retryAfterSeconds: 0, remaining: Math.floor(b.tokens) };
    }
    return { allowed: false, retryAfterSeconds: Math.ceil((1 - b.tokens) / this.refillPerMs / 1000), remaining: 0 };
  }
}
