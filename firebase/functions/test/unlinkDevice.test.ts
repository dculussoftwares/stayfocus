import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import type { CallableRequest } from "firebase-functions/v2/https";
import { removeDevice, unlinkDeviceConfig } from "../src/functions/unlinkDevice";
import { secureHandler, type SecureDeps } from "../src/lib/secureCallable";

if (getApps().length === 0) initializeApp({ projectId: "demo-stayfocus" });
const db = getFirestore();
const deps: SecureDeps = { db: () => db, now: () => new Date() };
const unlink = secureHandler(unlinkDeviceConfig, deps);

function call(uid: string, provider: string, data: unknown): CallableRequest<unknown> {
  return {
    data,
    auth: { uid, token: { firebase: { sign_in_provider: provider }, auth_time: Date.now() / 1000 } },
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

interface Scene {
  parent: string;
  child: string;
  deviceId: string;
}

/** A parent with one linked device that has data in every subcollection the app writes. */
async function scene(): Promise<Scene> {
  const parent = `p-${randomUUID()}`;
  const child = `c-${randomUUID()}`;
  const deviceId = randomUUID().replace(/-/g, "").slice(0, 20);
  const user = db.collection("users").doc(parent);
  await user.set({ displayName: "Asha" });
  const device = user.collection("devices").doc(deviceId);
  await device.set({ childUid: child, name: "Pixel", online: true });
  for (const sub of ["blocks", "usage", "apps", "requests", "alerts"]) {
    await device.collection(sub).doc("a").set({ x: 1 });
    await device.collection(sub).doc("b").set({ x: 2 });
  }
  // A sibling device and the parent's own data must survive.
  await user.collection("devices").doc("other").set({ childUid: "someone" });
  await user.collection("devices").doc("other").collection("blocks").doc("a").set({ x: 1 });
  await user.collection("fcmTokens").doc("t").set({ token: "x" });
  return { parent, child, deviceId };
}

async function remaining(s: Scene): Promise<string[]> {
  const device = db.collection("users").doc(s.parent).collection("devices").doc(s.deviceId);
  const paths: string[] = [];
  if ((await device.get()).exists) paths.push(device.path);
  for (const sub of ["blocks", "usage", "apps", "requests", "alerts"]) {
    for (const d of (await device.collection(sub).get()).docs) paths.push(d.ref.path);
  }
  return paths;
}

describe("unlinkDevice", () => {
  it("lets the owning parent remove the device and every document under it", async () => {
    const s = await scene();
    const result = await unlink(call(s.parent, "password", { deviceId: s.deviceId }));
    expect(result).toEqual({ ok: true, removed: true });
    expect(await remaining(s)).toEqual([]);
    const user = db.collection("users").doc(s.parent);
    expect((await user.collection("devices").doc("other").get()).exists).toBe(true);
    expect((await user.collection("devices").doc("other").collection("blocks").get()).size).toBe(1);
    expect((await user.collection("fcmTokens").get()).size).toBe(1);
  });

  it("lets the child remove its own device", async () => {
    const s = await scene();
    const result = await unlink(call(s.child, "anonymous", { deviceId: s.deviceId, parentUid: s.parent }));
    expect(result).toEqual({ ok: true, removed: true });
    expect(await remaining(s)).toEqual([]);
  });

  it("refuses a third party, signed in or anonymous, and leaves everything in place", async () => {
    const s = await scene();
    const before = await remaining(s);
    expect(before.length).toBe(11);
    const stranger = `x-${randomUUID()}`;
    expect(await code(unlink(call(stranger, "password", { deviceId: s.deviceId, parentUid: s.parent })))).toBe(
      "permission-denied",
    );
    expect(await code(unlink(call(stranger, "anonymous", { deviceId: s.deviceId, parentUid: s.parent })))).toBe(
      "permission-denied",
    );
    expect(await code(unlink(call(stranger, "password", { deviceId: s.deviceId })))).toBe("no-error"); // their own tree: nothing there
    expect(await remaining(s)).toEqual(before);
  });

  it("does not let a child of another device, or an anonymous caller posing as the parent, unlink", async () => {
    const s = await scene();
    const before = await remaining(s);
    // The other device's child.
    expect(await code(unlink(call("someone", "anonymous", { deviceId: s.deviceId, parentUid: s.parent })))).toBe(
      "permission-denied",
    );
    // An anonymous sign-in whose uid equals the parent's is still not a parent.
    expect(await code(unlink(call(s.parent, "anonymous", { deviceId: s.deviceId })))).toBe("permission-denied");
    expect(await remaining(s)).toEqual(before);
  });

  it("is a no-op for the parent when the device is already gone, and refuses the former child", async () => {
    const s = await scene();
    await unlink(call(s.parent, "password", { deviceId: s.deviceId }));
    expect(await unlink(call(s.parent, "password", { deviceId: s.deviceId }))).toEqual({ ok: true, removed: false });
    expect(await code(unlink(call(s.child, "anonymous", { deviceId: s.deviceId, parentUid: s.parent })))).toBe(
      "permission-denied",
    );
  });

  it("rejects unknown fields and malformed ids", async () => {
    const s = await scene();
    expect(await code(unlink(call(s.parent, "password", { deviceId: s.deviceId, extra: 1 })))).toBe("invalid-argument");
    expect(await code(unlink(call(s.parent, "password", { deviceId: "../x" })))).toBe("invalid-argument");
    expect(await code(unlink(call(s.parent, "password", {})))).toBe("invalid-argument");
  });
});

describe("removeDevice", () => {
  it("notifies the parent only when the child unlinked a device that existed", async () => {
    const s = await scene();
    const told: string[] = [];
    const notify = (parentUid: string, deviceId: string) => {
      told.push(`${parentUid}/${deviceId}`);
      return Promise.resolve();
    };
    await removeDevice(db, "parent", s.parent, s.deviceId, notify);
    expect(told).toEqual([]);

    const t = await scene();
    await removeDevice(db, "child", t.parent, t.deviceId, notify);
    expect(told).toEqual([`${t.parent}/${t.deviceId}`]);
    // Repeating does not tell the parent again.
    await removeDevice(db, "child", t.parent, t.deviceId, notify);
    expect(told.length).toBe(1);
  });
});
