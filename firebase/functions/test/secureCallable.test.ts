import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import type { CallableRequest } from "firebase-functions/v2/https";
import { z } from "zod";
import { secureHandler, type SecureDeps } from "../src/lib/secureCallable";

if (getApps().length === 0) initializeApp({ projectId: "demo-stayfocus" });
const db = getFirestore();
const deps: SecureDeps = { db: () => db, now: () => new Date() };

function request(over: Partial<{ auth: unknown; app: unknown; data: unknown }> = {}): CallableRequest<unknown> {
  return {
    data: {},
    auth: { uid: `u-${randomUUID()}`, token: { firebase: { sign_in_provider: "password" } } },
    app: { appId: "1:1:android:1", token: {}, alreadyConsumed: false },
    rawRequest: {},
    acceptsStreaming: false,
    ...over,
  } as unknown as CallableRequest<unknown>;
}

const input = z.strictObject({ childUid: z.string().min(1) });
const base = { name: `t-${randomUUID()}`, input, handler: () => "done" };

async function code(p: Promise<unknown>): Promise<string> {
  try {
    await p;
  } catch (e) {
    return (e as { code: string }).code;
  }
  return "no-error";
}

describe("secureHandler", () => {
  it("refuses a missing sign-in", async () => {
    const run = secureHandler(base, deps);
    expect(await code(run(request({ auth: undefined, data: { childUid: "c" } })))).toBe("unauthenticated");
  });

  it("refuses a missing App Check token", async () => {
    const run = secureHandler(base, deps);
    expect(await code(run(request({ app: undefined, data: { childUid: "c" } })))).toBe("unauthenticated");
  });

  it("refuses a replayed App Check token on one-shot operations only", async () => {
    const replayed = { appId: "a", token: {}, alreadyConsumed: true };
    const oneShot = secureHandler({ ...base, consumeAppCheckToken: true }, deps);
    expect(await code(oneShot(request({ app: replayed, data: { childUid: "c" } })))).toBe("unauthenticated");
    const normal = secureHandler(base, deps);
    expect(await code(normal(request({ app: replayed, data: { childUid: "c" } })))).toBe("no-error");
  });

  it("refuses bad input without echoing values", async () => {
    const run = secureHandler(base, deps);
    const bad = request({ data: { childUid: "", extra: "secret-value" } });
    let error: { code: string; message: string; details: unknown } | undefined;
    try {
      await run(bad);
    } catch (e) {
      error = e as typeof error;
    }
    expect(error?.code).toBe("invalid-argument");
    expect(JSON.stringify(error)).not.toContain("secret-value");
  });

  it("refuses a caller the role check rejects", async () => {
    const run = secureHandler({ ...base, authorize: ({ uid, data }) => uid === data.childUid }, deps);
    expect(await code(run(request({ data: { childUid: "someone-else" } })))).toBe("permission-denied");
  });

  it("returns the handler result when every check passes", async () => {
    const run = secureHandler({ ...base, authorize: () => true }, deps);
    expect(await run(request({ data: { childUid: "c" } }))).toBe("done");
  });

  it("hides internal errors behind a generic message", async () => {
    const run = secureHandler(
      {
        ...base,
        handler: () => {
          throw new Error("db password is hunter2");
        },
      },
      deps,
    );
    let error: { code: string; message: string } | undefined;
    try {
      await run(request({ data: { childUid: "c" } }));
    } catch (e) {
      error = e as typeof error;
    }
    expect(error?.code).toBe("internal");
    expect(error?.message).not.toContain("hunter2");
  });

  it("rate limits per uid and per window", async () => {
    let now = new Date("2026-01-01T00:00:00Z");
    const clock: SecureDeps = { db: () => db, now: () => now };
    const run = secureHandler({ ...base, rateLimit: { max: 2, windowSeconds: 60 } }, clock);
    const alice = request({ data: { childUid: "c" } });
    const bob = request({ data: { childUid: "c" } });

    expect(await code(run(alice))).toBe("no-error");
    expect(await code(run(alice))).toBe("no-error");
    expect(await code(run(alice))).toBe("resource-exhausted");
    // Another uid has its own counter.
    expect(await code(run(bob))).toBe("no-error");
    // A new window resets the counter.
    now = new Date(now.getTime() + 61_000);
    expect(await code(run(alice))).toBe("no-error");
  });

  it("does not count calls that fail before the limiter (no sign-in)", async () => {
    const run = secureHandler({ ...base, rateLimit: { max: 1, windowSeconds: 60 } }, deps);
    const anon = request({ auth: undefined, data: { childUid: "c" } });
    expect(await code(run(anon))).toBe("unauthenticated");
    expect(await code(run(anon))).toBe("unauthenticated");
  });
});
