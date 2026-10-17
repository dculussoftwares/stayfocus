import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { handleRequestCreated } from "../src/functions/notifyParent";
import { cleanLabel, pushToParent, type NotifyDeps } from "../src/lib/notify";
import type { MulticastMessage } from "firebase-admin/messaging";

if (getApps().length === 0) initializeApp({ projectId: "demo-stayfocus" });
const db = getFirestore();

interface Sent {
  tokens: string[];
  data: Record<string, string>;
}

/** FCM mocked: tokens listed in [dead] answer "not registered", tokens in [broken] answer with another error. */
function fakeMessaging(dead: string[] = [], broken: string[] = [], throwAll = false) {
  const sent: Sent[] = [];
  const deps: NotifyDeps = {
    db: () => db,
    messaging: () => ({
      sendEachForMulticast: (message: MulticastMessage) => {
        if (throwAll) return Promise.reject(new Error("fcm down"));
        sent.push({ tokens: [...message.tokens], data: message.data as Record<string, string> });
        return Promise.resolve({
          successCount: 0,
          failureCount: 0,
          responses: message.tokens.map((t) => {
            if (dead.includes(t)) {
              return { success: false, error: { code: "messaging/registration-token-not-registered" } };
            }
            if (broken.includes(t)) return { success: false, error: { code: "messaging/internal-error" } };
            return { success: true, messageId: "m" };
          }),
        }) as never;
      },
    }),
  };
  return { deps, sent };
}

async function scene(tokens: string[], deviceName = "Maya's phone") {
  const parent = `p-${randomUUID()}`;
  const deviceId = randomUUID().replace(/-/g, "").slice(0, 20);
  const user = db.collection("users").doc(parent);
  await user.set({ displayName: "Asha" });
  await user.collection("devices").doc(deviceId).set({ childUid: "c", name: deviceName });
  for (const t of tokens) await user.collection("fcmTokens").doc(t).set({ platform: "android" });
  return { parent, deviceId, user };
}

const pending = (over: Record<string, unknown> = {}) => ({ app: "Instagram", minutes: 15, status: "pending", ...over });

describe("notifyParent", () => {
  it("sends the request to every parent token with server-built fields", async () => {
    const s = await scene(["tok-a", "tok-b"]);
    const { deps, sent } = fakeMessaging();
    const result = await handleRequestCreated(deps, {
      parentUid: s.parent,
      deviceId: s.deviceId,
      reqId: "r1",
      data: pending(),
    });
    expect(result).toEqual({ sent: 2, pruned: 0 });
    expect(sent).toHaveLength(1);
    expect(sent[0]?.tokens.sort()).toEqual(["tok-a", "tok-b"]);
    expect(sent[0]?.data).toEqual({
      type: "request",
      deviceId: s.deviceId,
      reqId: "r1",
      device: "Maya's phone",
      app: "Instagram",
      minutes: "15",
    });
  });

  it("deletes tokens FCM reports as not registered and keeps the rest", async () => {
    const s = await scene(["good", "gone", "flaky"]);
    const { deps } = fakeMessaging(["gone"], ["flaky"]);
    const result = await handleRequestCreated(deps, {
      parentUid: s.parent,
      deviceId: s.deviceId,
      reqId: "r1",
      data: pending(),
    });
    expect(result).toEqual({ sent: 1, pruned: 1 });
    const left = (await s.user.collection("fcmTokens").get()).docs.map((d) => d.id).sort();
    expect(left).toEqual(["flaky", "good"]);
  });

  it("sanitises the child's text and takes the device name from the device document", async () => {
    const s = await scene(["tok"], "Maya‮'s\nphone");
    const { deps, sent } = fakeMessaging();
    await handleRequestCreated(deps, {
      parentUid: s.parent,
      deviceId: s.deviceId,
      reqId: "r1",
      data: pending({ app: `Evil\u0000\n  App ${"x".repeat(200)}`, device: "spoofed" }),
    });
    const data = sent[0]?.data;
    expect(data?.device).toBe("Maya 's phone");
    expect(data?.app?.startsWith("Evil App x")).toBe(true);
    expect(Array.from(data?.app ?? "").length).toBe(40);
    expect(data).not.toHaveProperty("spoofed");
  });

  it("ignores documents that are not a pending request, and a device that is gone", async () => {
    const s = await scene(["tok"]);
    const { deps, sent } = fakeMessaging();
    const base = { parentUid: s.parent, deviceId: s.deviceId, reqId: "r1" };
    expect(await handleRequestCreated(deps, { ...base, data: undefined })).toBeNull();
    expect(await handleRequestCreated(deps, { ...base, data: pending({ status: "approved" }) })).toBeNull();
    expect(await handleRequestCreated(deps, { ...base, data: pending({ minutes: 0 }) })).toBeNull();
    expect(await handleRequestCreated(deps, { ...base, data: pending({ minutes: "15" }) })).toBeNull();
    expect(await handleRequestCreated(deps, { ...base, deviceId: "nope", data: pending() })).toBeNull();
    expect(sent).toEqual([]);
  });

  it("does nothing, and does not fail, when the parent has no tokens or FCM is down", async () => {
    const none = await scene([]);
    const ok = fakeMessaging();
    expect(await pushToParent(ok.deps, "t", none.parent, { type: "x" })).toEqual({ sent: 0, pruned: 0 });
    expect(ok.sent).toEqual([]);

    const some = await scene(["tok"]);
    const down = fakeMessaging([], [], true);
    expect(await pushToParent(down.deps, "t", some.parent, { type: "x" })).toEqual({ sent: 0, pruned: 0 });
    expect((await some.user.collection("fcmTokens").get()).size).toBe(1);
  });

  it("sends in batches of 500 tokens", async () => {
    const s = await scene([]);
    const batch = db.batch();
    for (let i = 0; i < 501; i += 1) batch.set(s.user.collection("fcmTokens").doc(`t${i}`), { platform: "android" });
    await batch.commit();
    const { deps, sent } = fakeMessaging();
    expect(await pushToParent(deps, "t", s.parent, { type: "x" })).toEqual({ sent: 501, pruned: 0 });
    expect(sent.map((m) => m.tokens.length).sort((a, b) => a - b)).toEqual([1, 500]);
  });
});

describe("cleanLabel", () => {
  it("falls back for non-strings and blank text", () => {
    expect(cleanLabel(undefined, "fb")).toBe("fb");
    expect(cleanLabel(42, "fb")).toBe("fb");
    expect(cleanLabel(" \n\t ", "fb")).toBe("fb");
    expect(cleanLabel("  Maps  ", "fb")).toBe("Maps");
  });
});
