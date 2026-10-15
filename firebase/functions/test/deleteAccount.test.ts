import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import type { CallableRequest } from "firebase-functions/v2/https";
import { z } from "zod";
import { deleteAccountConfig, deleteAccountData, MAX_AUTH_AGE_SECONDS, type UserDeleter } from "../src/functions/deleteAccount";
import { secureHandler, type SecureDeps } from "../src/lib/secureCallable";

if (getApps().length === 0) initializeApp({ projectId: "demo-stayfocus" });
const db = getFirestore();
const NOW = new Date("2026-06-01T12:00:00Z");
const deps: SecureDeps = { db: () => db, now: () => NOW };
const nowSeconds = NOW.getTime() / 1000;

class FakeAuth implements UserDeleter {
  deleted: string[] = [];
  deleteUser(uid: string): Promise<void> {
    this.deleted.push(uid);
    return Promise.resolve();
  }
}

async function seed(uid: string): Promise<void> {
  const user = db.collection("users").doc(uid);
  await user.set({ displayName: "Parent", email: "p@example.com" });
  await user.collection("devices").doc("d1").set({ childUid: "kid", model: "Pixel" });
  await user.collection("devices").doc("d1").collection("blocks").doc("b1").set({ name: "Study" });
  await user.collection("devices").doc("d1").collection("usage").doc("u1").set({ minutes: 4 });
  await user.collection("fcmTokens").doc("t1").set({ token: "x" });
  await db.collection("aiReports").doc(`rep-${uid}`).set({ uid, sentence: "block x", draft: {} });
  await db.collection("aiReports").doc(`rep-other-${uid}`).set({ uid: "someone-else", sentence: "y", draft: {} });
  await db.collection("linkTokens").doc(`tok-${uid}`).set({ childUid: "kid", claimedBy: uid });
  await db.collection("linkTokens").doc(`other-${uid}`).set({ childUid: "kid", claimedBy: "someone-else" });
}

function request(authTime: number | undefined): CallableRequest<unknown> {
  return {
    data: {},
    auth: {
      uid: `u-${randomUUID()}`,
      token: { firebase: { sign_in_provider: "password" }, auth_time: authTime },
    },
    app: { appId: "1:1:android:1", token: {}, alreadyConsumed: false },
    rawRequest: {},
    acceptsStreaming: false,
  } as unknown as CallableRequest<unknown>;
}

async function code(p: Promise<unknown>): Promise<string> {
  try {
    await p;
  } catch (e) {
    return (e as { code: string }).code;
  }
  return "no-error";
}

describe("deleteAccountData", () => {
  it("leaves no documents for the user and no claimed tokens", async () => {
    const uid = `u-${randomUUID()}`;
    await seed(uid);
    const auth = new FakeAuth();

    await deleteAccountData(db, auth, uid);

    const user = db.collection("users").doc(uid);
    expect((await user.get()).exists).toBe(false);
    expect((await user.collection("devices").get()).empty).toBe(true);
    expect((await user.collection("fcmTokens").get()).empty).toBe(true);
    expect((await user.collection("devices").doc("d1").collection("blocks").get()).empty).toBe(true);
    expect((await user.collection("devices").doc("d1").collection("usage").get()).empty).toBe(true);
    expect((await db.collection("linkTokens").doc(`tok-${uid}`).get()).exists).toBe(false);
    expect((await db.collection("aiReports").doc(`rep-${uid}`).get()).exists).toBe(false);
    expect((await db.collection("aiReports").doc(`rep-other-${uid}`).get()).exists).toBe(true);
    // Someone else's claimed token is untouched.
    expect((await db.collection("linkTokens").doc(`other-${uid}`).get()).exists).toBe(true);
    expect(auth.deleted).toEqual([uid]);
  });

  it("is safe to run again and ignores an Auth user that is already gone", async () => {
    const uid = `u-${randomUUID()}`;
    await seed(uid);
    const auth: UserDeleter = {
      deleteUser: () => Promise.reject(Object.assign(new Error("gone"), { code: "auth/user-not-found" })),
    };
    await deleteAccountData(db, auth, uid);
    await deleteAccountData(db, auth, uid);
    expect((await db.collection("users").doc(uid).get()).exists).toBe(false);
  });

  it("surfaces other Auth failures so the client can retry", async () => {
    const auth: UserDeleter = { deleteUser: () => Promise.reject(new Error("boom")) };
    await expect(deleteAccountData(db, auth, `u-${randomUUID()}`)).rejects.toThrow("boom");
  });
});

describe("deleteAccount callable checks", () => {
  // The real configuration (schema, role check, token age, rate limit); the handler is only reached when every check
  // passes, which the tests below deliberately avoid.
  const run = secureHandler(deleteAccountConfig, deps);

  function asCaller(over: { authTime?: number; provider?: string; data?: unknown }): CallableRequest<unknown> {
    const base = request(over.authTime ?? nowSeconds - 30) as unknown as {
      auth: { token: { firebase: { sign_in_provider: string } } };
      data: unknown;
    };
    base.auth.token.firebase.sign_in_provider = over.provider ?? "password";
    if (over.data !== undefined) base.data = over.data;
    return base as unknown as CallableRequest<unknown>;
  }

  it("rejects a stale token", async () => {
    expect(await code(run(asCaller({ authTime: nowSeconds - MAX_AUTH_AGE_SECONDS - 1 })))).toBe("unauthenticated");
  });

  it("rejects a token without auth_time", async () => {
    expect(await code(run(request(undefined)))).toBe("unauthenticated");
  });

  it("rejects an anonymous (Kids) sign-in", async () => {
    expect(await code(run(asCaller({ provider: "anonymous" })))).toBe("permission-denied");
  });

  it("rejects unexpected input", async () => {
    expect(await code(run(asCaller({ data: { uid: "someone-else" } })))).toBe("invalid-argument");
  });
});

describe("secureHandler token age", () => {
  const run = secureHandler(
    {
      name: "tokenAgeTest",
      input: z.strictObject({}),
      maxAuthAgeSeconds: MAX_AUTH_AGE_SECONDS,
      handler: () => "done",
    },
    deps,
  );

  it("accepts a fresh sign-in", async () => {
    expect(await code(run(request(nowSeconds - 60)))).toBe("no-error");
  });
});
