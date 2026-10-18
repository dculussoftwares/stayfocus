import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { handleAlertCreated } from "../src/functions/notifyParentAlert";
import type { NotifyDeps } from "../src/lib/notify";
import type { MulticastMessage } from "firebase-admin/messaging";

if (getApps().length === 0) initializeApp({ projectId: "demo-stayfocus" });
const db = getFirestore();

function fakeMessaging() {
  const sent: Record<string, string>[] = [];
  const deps: NotifyDeps = {
    db: () => db,
    messaging: () => ({
      sendEachForMulticast: (message: MulticastMessage) => {
        sent.push(message.data ?? {});
        return Promise.resolve({
          successCount: message.tokens.length,
          failureCount: 0,
          responses: message.tokens.map(() => ({ success: true, messageId: "m" })),
        });
      },
    }),
  };
  return { deps, sent };
}

async function scene(name = "Maya's phone") {
  const parent = `p-${randomUUID()}`;
  const deviceId = randomUUID().replace(/-/g, "").slice(0, 20);
  const user = db.collection("users").doc(parent);
  await user.set({ displayName: "Asha" });
  await user.collection("devices").doc(deviceId).set({ childUid: "c", name: name });
  await user.collection("fcmTokens").doc("tok").set({ platform: "android" });
  return { parent, deviceId };
}

const lost = (over: Record<string, unknown> = {}) => ({
  kind: "permission_lost",
  permission: "accessibility",
  dismissed: false,
  ...over,
});

describe("notifyParentAlert", () => {
  it("pushes a lost permission with the device name from the device document", async () => {
    const s = await scene();
    const { deps, sent } = fakeMessaging();
    const result = await handleAlertCreated(deps, {
      parentUid: s.parent,
      deviceId: s.deviceId,
      alertId: "a1",
      data: lost({ device: "spoofed" }),
    });
    expect(result).toEqual({ sent: 1, pruned: 0 });
    expect(sent[0]).toEqual({
      type: "alert",
      deviceId: s.deviceId,
      alertId: "a1",
      device: "Maya's phone",
      permission: "accessibility",
    });
  });

  it("reduces an unknown permission to 'other' so client text never reaches the message", async () => {
    const s = await scene();
    const { deps, sent } = fakeMessaging();
    await handleAlertCreated(deps, {
      parentUid: s.parent,
      deviceId: s.deviceId,
      alertId: "a1",
      data: lost({ permission: "Call 555-0100 now" }),
    });
    expect(sent[0]?.permission).toBe("other");
  });

  it("ignores other kinds, dismissed alerts, missing data and a device that is gone", async () => {
    const s = await scene();
    const { deps, sent } = fakeMessaging();
    const base = { parentUid: s.parent, deviceId: s.deviceId, alertId: "a1" };
    expect(await handleAlertCreated(deps, { ...base, data: lost({ kind: "offline" }) })).toBeNull();
    expect(await handleAlertCreated(deps, { ...base, data: lost({ kind: "unlinked" }) })).toBeNull();
    expect(await handleAlertCreated(deps, { ...base, data: lost({ dismissed: true }) })).toBeNull();
    expect(await handleAlertCreated(deps, { ...base, data: undefined })).toBeNull();
    expect(await handleAlertCreated(deps, { ...base, deviceId: "gone", data: lost() })).toBeNull();
    expect(sent).toHaveLength(0);
  });
});
