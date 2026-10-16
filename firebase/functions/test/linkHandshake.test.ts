import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import type { CallableRequest } from "firebase-functions/v2/https";
import { answerLink, approveLinkConfig } from "../src/functions/approveLink";
import { claimLinkTokenConfig, claimToken } from "../src/functions/claimLinkToken";
import { secureHandler, type SecureDeps } from "../src/lib/secureCallable";

if (getApps().length === 0) initializeApp({ projectId: "demo-stayfocus" });
const db = getFirestore();
const deps: SecureDeps = { db: () => db, now: () => new Date() };

const claim = secureHandler(claimLinkTokenConfig, deps);
const approve = secureHandler(approveLinkConfig, deps);

function call(uid: string, provider: string, data: unknown, alreadyConsumed = false): CallableRequest<unknown> {
  return {
    data,
    auth: { uid, token: { firebase: { sign_in_provider: provider }, auth_time: Date.now() / 1000 } },
    app: { appId: "1:1:android:1", token: {}, alreadyConsumed },
    rawRequest: {},
    acceptsStreaming: false,
  } as unknown as CallableRequest<unknown>;
}

const asParent = (uid: string, data: unknown) => call(uid, "password", data);
const asChild = (uid: string, data: unknown) => call(uid, "anonymous", data);

async function code(p: Promise<unknown>): Promise<string> {
  try {
    await p;
  } catch (e) {
    return (e as { code: string }).code;
  }
  return "no-error";
}

async function reason(p: Promise<unknown>): Promise<string | undefined> {
  try {
    await p;
  } catch (e) {
    return (e as { details?: { reason?: string } }).details?.reason;
  }
  return undefined;
}

interface Scene {
  parent: string;
  child: string;
  token: string;
}

/** A parent with a profile and a child's open token, as the Kids app would have written it. */
async function scene(over: { expiresInMs?: number; displayName?: string } = {}): Promise<Scene> {
  const parent = `p-${randomUUID()}`;
  const child = `c-${randomUUID()}`;
  const token = randomUUID().replace(/-/g, "") + "ab"; // 34 url-safe characters
  await db.collection("users").doc(parent).set({ displayName: over.displayName ?? "Asha Rao", email: "a@example.com" });
  await db
    .collection("linkTokens")
    .doc(token)
    .set({
      childUid: child,
      model: "Pixel 7",
      createdAt: Timestamp.now(),
      expiresAt: Timestamp.fromMillis(Date.now() + (over.expiresInMs ?? 4 * 60_000)),
      code: "000000",
      status: "open",
    });
  return { parent, child, token };
}

const tokenDoc = async (token: string) => (await db.collection("linkTokens").doc(token).get()).data() ?? {};

describe("pairing handshake", () => {
  it("claims, shows a server-made code, then the child approves and the device appears under the parent", async () => {
    const { parent, child, token } = await scene();

    const claimed = (await claim(asParent(parent, { token, deviceName: "Aarav's phone" }))) as {
      code: string;
      model: string;
      deviceId: string;
    };
    expect(claimed.code).toMatch(/^\d{6}$/);
    expect(claimed.model).toBe("Pixel 7");

    const afterClaim = await tokenDoc(token);
    expect(afterClaim).toMatchObject({ status: "claimed", claimedBy: parent, code: claimed.code, parentName: "Asha" });
    // The child's placeholder code was replaced by the server's.
    expect(afterClaim.code).not.toBe("000000");
    // No device yet: the child has not consented.
    expect((await db.collection("users").doc(parent).collection("devices").doc(claimed.deviceId).get()).exists).toBe(false);

    const answer = await approve(asChild(child, { token, approve: true }));
    expect(answer).toEqual({ linked: true, parentUid: parent, deviceId: claimed.deviceId, parentName: "Asha" });

    const device = (await db.collection("users").doc(parent).collection("devices").doc(claimed.deviceId).get()).data();
    expect(device).toMatchObject({
      name: "Aarav's phone",
      model: "Pixel 7",
      childUid: child,
      online: false,
      charging: false,
      battery: null,
      currentApp: null,
      lastSeen: null,
      focusEndsAt: null,
    });
    expect(device?.linkedAt).toBeInstanceOf(Timestamp);
    expect((await tokenDoc(token)).status).toBe("used");
  });

  it("uses the child's model as the name unless the parent renames it (also after claiming)", async () => {
    const { parent, child, token } = await scene();
    const first = (await claim(asParent(parent, { token }))) as { code: string; deviceId: string };
    expect((await tokenDoc(token)).deviceName).toBe("Pixel 7");

    const renamed = (await claim(asParent(parent, { token, deviceName: "  Living room  " }))) as { code: string };
    // Same code: renaming never re-rolls it.
    expect(renamed.code).toBe(first.code);
    await approve(asChild(child, { token, approve: true }));
    const device = await db.collection("users").doc(parent).collection("devices").doc(first.deviceId).get();
    expect(device.get("name")).toBe("Living room");
  });

  it("leaves the parent name empty when the profile has none (the app supplies the words)", async () => {
    const { parent, token } = await scene({ displayName: "   " });
    await claim(asParent(parent, { token }));
    expect((await tokenDoc(token)).parentName).toBe("");
  });

  it("a declined link creates no device and ends the token", async () => {
    const { parent, child, token } = await scene();
    const { deviceId } = (await claim(asParent(parent, { token }))) as { deviceId: string };

    expect(await approve(asChild(child, { token, approve: false }))).toEqual({ linked: false });
    expect((await tokenDoc(token)).status).toBe("declined");
    expect((await db.collection("users").doc(parent).collection("devices").doc(deviceId).get()).exists).toBe(false);
    expect(await code(approve(asChild(child, { token, approve: true })))).toBe("failed-precondition");
  });

  describe("replay", () => {
    it("a used token cannot be claimed again by another parent", async () => {
      const { parent, child, token } = await scene();
      await claim(asParent(parent, { token }));
      await approve(asChild(child, { token, approve: true }));

      // The same parent is only told the link happened (no new code, nothing re-claimed); anyone else is refused.
      expect(await claim(asParent(parent, { token }))).toMatchObject({ linked: true });
      expect(await reason(claim(asParent(`p-${randomUUID()}`, { token })))).toBe("used");
    });

    it("approving a used token again returns the same link and creates no second device", async () => {
      const { parent, child, token } = await scene();
      await claim(asParent(parent, { token }));
      const first = await approve(asChild(child, { token, approve: true }));

      // The child never got the answer and asks again.
      expect(await approve(asChild(child, { token, approve: true }))).toEqual(first);
      expect((await db.collection("users").doc(parent).collection("devices").get()).size).toBe(1);
      // Declining a token that is already used is refused.
      expect(await reason(approve(asChild(child, { token, approve: false })))).toBe("used");
    });

    it("refuses a request whose App Check token was already consumed", async () => {
      const { parent, token } = await scene();
      expect(await code(claim(call(parent, "password", { token }, true)))).toBe("unauthenticated");
      expect((await tokenDoc(token)).status).toBe("open");
    });
  });

  describe("expiry", () => {
    it("refuses to claim an expired token", async () => {
      const { parent, token } = await scene({ expiresInMs: -1000 });
      expect(await reason(claim(asParent(parent, { token })))).toBe("expired");
      expect((await tokenDoc(token)).status).toBe("open");
    });

    it("refuses consent that arrives after the token expired", async () => {
      const { parent, child, token } = await scene({ expiresInMs: 60_000 });
      await claim(asParent(parent, { token }));
      const later = new Date(Date.now() + 2 * 60_000);

      await expect(answerLink(db, child, token, true, later)).rejects.toMatchObject({
        code: "failed-precondition",
        details: { reason: "expired" },
      });
      expect((await db.collection("users").doc(parent).collection("devices").get()).empty).toBe(true);
    });

    it("an unknown token is not found", async () => {
      const { parent } = await scene();
      expect(await reason(claim(asParent(parent, { token: "x".repeat(30) })))).toBe("invalid");
    });
  });

  describe("who may answer", () => {
    it("approve by another uid is refused and changes nothing", async () => {
      const { parent, child, token } = await scene();
      const { deviceId } = (await claim(asParent(parent, { token }))) as { deviceId: string };

      expect(await code(approve(asChild(`c-${randomUUID()}`, { token, approve: true })))).toBe("permission-denied");
      // The parent cannot consent on the child's behalf either.
      expect(await code(approve(asParent(parent, { token, approve: true })))).toBe("permission-denied");
      expect((await tokenDoc(token)).status).toBe("claimed");
      expect((await db.collection("users").doc(parent).collection("devices").doc(deviceId).get()).exists).toBe(false);

      // The real child can still answer afterwards.
      expect(await approve(asChild(child, { token, approve: true }))).toMatchObject({ linked: true });
    });

    it("approve before any parent claimed is refused", async () => {
      const { child, token } = await scene();
      expect(await reason(approve(asChild(child, { token, approve: true })))).toBe("not_claimed");
    });

    it("the Kids sign-in cannot claim", async () => {
      const { child, token } = await scene();
      expect(await code(claim(asChild(child, { token })))).toBe("permission-denied");
    });

    it("a recovered approval is refused when the device was removed meanwhile", async () => {
      const { parent, child, token } = await scene();
      const { deviceId } = (await claim(asParent(parent, { token }))) as { deviceId: string };
      await approve(asChild(child, { token, approve: true }));
      await db.collection("users").doc(parent).collection("devices").doc(deviceId).delete();
      expect(await reason(approve(asChild(child, { token, approve: true })))).toBe("invalid");
    });

    it("the parent who was away learns the link already happened", async () => {
      const { parent, child, token } = await scene();
      const first = (await claim(asParent(parent, { token }))) as { code: string; deviceId: string };
      await approve(asChild(child, { token, approve: true }));

      expect(await claim(asParent(parent, { token }))).toMatchObject({
        linked: true,
        code: first.code,
        deviceId: first.deviceId,
      });
      // A device removed since is not reported as linked.
      await db.collection("users").doc(parent).collection("devices").doc(first.deviceId).delete();
      expect(await reason(claim(asParent(parent, { token })))).toBe("invalid");
      // Anyone else still gets "used".
      expect(await reason(claim(asParent(`p-${randomUUID()}`, { token })))).toBe("used");
    });

    it("a deleted parent account (its claimed token is deleted) cannot be linked to", async () => {
      const { parent, child, token } = await scene();
      await claim(asParent(parent, { token }));
      await db.collection("linkTokens").doc(token).delete();
      expect(await reason(approve(asChild(child, { token, approve: true })))).toBe("invalid");
    });

    it("a parent account cannot approve its own token", async () => {
      const { parent, token } = await scene();
      await db.collection("linkTokens").doc(token).update({ childUid: parent });
      await claim(asParent(parent, { token }));
      expect(await code(approve(asParent(parent, { token, approve: true })))).toBe("permission-denied");
    });
  });

  describe("the parent cancels", () => {
    it("blocks the child's later Allow", async () => {
      const { parent, child, token } = await scene();
      const { deviceId } = (await claim(asParent(parent, { token }))) as { deviceId: string };
      expect(await claim(asParent(parent, { token, cancel: true }))).toEqual({ cancelled: true });

      expect((await tokenDoc(token)).status).toBe("declined");
      expect(await reason(approve(asChild(child, { token, approve: true })))).toBe("used");
      expect((await db.collection("users").doc(parent).collection("devices").doc(deviceId).get()).exists).toBe(false);
    });

    it("only the claiming parent can cancel", async () => {
      const { parent, token } = await scene();
      await claim(asParent(parent, { token }));
      expect(await reason(claim(asParent(`p-${randomUUID()}`, { token, cancel: true })))).toBe("used");
      expect((await tokenDoc(token)).status).toBe("claimed");
    });
  });

  describe("two parents racing", () => {
    it("exactly one claim wins and the loser learns the token is used", async () => {
      const { token } = await scene();
      const parents = Array.from({ length: 6 }, () => `p-${randomUUID()}`);
      await Promise.all(parents.map((p) => db.collection("users").doc(p).set({ displayName: p })));

      const outcomes = await Promise.allSettled(parents.map((p) => claim(asParent(p, { token }))));
      const won = outcomes.filter((o) => o.status === "fulfilled");
      const lost = outcomes.filter((o): o is PromiseRejectedResult => o.status === "rejected");

      expect(won).toHaveLength(1);
      expect(lost).toHaveLength(5);
      for (const l of lost) expect(l.reason).toMatchObject({ code: "failed-precondition", details: { reason: "used" } });
      const winner = parents[outcomes.findIndex((o) => o.status === "fulfilled")];
      expect((await tokenDoc(token)).claimedBy).toBe(winner);
    });

    it("the loser cannot rename the winner's claim", async () => {
      const { parent, token } = await scene();
      await claim(asParent(parent, { token, deviceName: "Mine" }));
      const other = `p-${randomUUID()}`;
      expect(await reason(claim(asParent(other, { token, deviceName: "Hijacked" })))).toBe("used");
      expect((await tokenDoc(token)).deviceName).toBe("Mine");
    });
  });

  describe("input", () => {
    it("rejects unknown fields and malformed tokens", async () => {
      const { parent, token } = await scene();
      expect(await code(claim(asParent(parent, { token, extra: 1 })))).toBe("invalid-argument");
      expect(await code(claim(asParent(parent, { token: "a/b" })))).toBe("invalid-argument");
      expect(await code(approve(asChild("c", { token, approve: "yes" })))).toBe("invalid-argument");
    });
  });

  it("claimToken picks all six digits uniformly enough to include leading zeros", async () => {
    const seen = new Set<string>();
    for (let i = 0; i < 40; i++) {
      const { parent, token } = await scene();
      seen.add(((await claimToken(db, parent, token, undefined)) as { code: string }).code);
    }
    for (const c of seen) expect(c).toMatch(/^\d{6}$/);
    expect(seen.size).toBeGreaterThan(30);
  });
});
