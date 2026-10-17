import type { DocumentReference, Firestore } from "firebase-admin/firestore";
import { z } from "zod";
import { logInfo } from "../lib/log";
import { cleanLabel, defaultNotifyDeps, pushToParent } from "../lib/notify";
import { secureCallable } from "../lib/secureCallable";

/** Firestore auto ids are 20 letters and digits; the bounds are looser so the schema does not depend on that. */
const idSchema = z.string().regex(/^[A-Za-z0-9_-]{1,128}$/);

/** gRPC status code of a missing document. */
const NOT_FOUND = 5;

export type UnlinkWho = "parent" | "child";

export interface UnlinkResult {
  ok: true;
  /** False when the device was already gone (an earlier call, or the other side unlinked first). */
  removed: boolean;
}

/** Tells the parent their child unlinked. [deviceName] is read before the device document is deleted. */
export type NotifyParent = (parentUid: string, deviceId: string, deviceName?: string) => Promise<void>;

/** The shared notify module (also used by notifyParent): a data push the parent app shows as "{device} unlinked". */
export const pushNotify: NotifyParent = async (parentUid, deviceId, deviceName) => {
  await pushToParent(defaultNotifyDeps, "unlinkDevice", parentUid, {
    type: "unlinked",
    deviceId,
    device: cleanLabel(deviceName, "Your child's phone"),
  });
};

/**
 * Who the caller is for `users/{parentUid}/devices/{deviceId}`: the parent who owns it (a signed-in, non-anonymous
 * account whose uid is `parentUid`), the child whose uid is the device's `childUid`, or nobody (null).
 * A device that no longer exists has no child, so only its former owner can still ask (and gets a no-op).
 */
export async function whoIsCaller(
  db: Firestore,
  uid: string,
  anonymous: boolean,
  parentUid: string,
  deviceId: string,
): Promise<UnlinkWho | null> {
  if (!anonymous && uid === parentUid) return "parent";
  const snap = await db.collection("users").doc(parentUid).collection("devices").doc(deviceId).get();
  return snap.exists && snap.get("childUid") === uid ? "child" : null;
}

/**
 * Deletes everything under the device (blocks, usage, apps, requests, alerts) with `recursiveDelete`, and the device
 * document last. The document is what proves a caller's right to ask, so if a step fails halfway the document is still
 * there and the same caller can simply retry. When the child unlinked, the parent is told afterwards. Safe to repeat.
 */
export async function removeDevice(
  db: Firestore,
  who: UnlinkWho,
  parentUid: string,
  deviceId: string,
  notify: NotifyParent = pushNotify,
): Promise<UnlinkResult> {
  const ref = db.collection("users").doc(parentUid).collection("devices").doc(deviceId);
  const deviceName = who === "child" ? ((await ref.get()).get("name") as string | undefined) : undefined;
  await removeSubcollections(db, ref);
  // Only one of several overlapping calls deletes the document; that one reports the removal and tells the parent.
  const deleted = await deleteIfExists(ref);
  // A write that was in flight when the document went (the child reports every 90 s) can leave a stray subcollection
  // document; the rules refuse further writes once the device document is gone, so a second pass leaves nothing.
  await removeSubcollections(db, ref);
  if (deleted && who === "child") await notify(parentUid, deviceId, deviceName);
  return { ok: true, removed: deleted };
}

/** Deletes the document if it is there; false when it was already gone (NOT_FOUND from the precondition). */
async function deleteIfExists(ref: DocumentReference): Promise<boolean> {
  try {
    await ref.delete({ exists: true });
    return true;
  } catch (e) {
    if ((e as { code?: number }).code === NOT_FOUND) return false;
    throw e;
  }
}

async function removeSubcollections(db: Firestore, ref: DocumentReference): Promise<void> {
  for (const collection of await ref.listCollections()) {
    await db.recursiveDelete(collection);
  }
}

interface UnlinkContext {
  uid: string;
  anonymous: boolean;
  db: Firestore;
  data: { deviceId: string; parentUid?: string };
}

/** The callable's configuration, exported so tests exercise the real checks without the Functions framework. */
export const unlinkDeviceConfig = {
  name: "unlinkDevice",
  // A parent names only the device (their own uid is the owner); the child also names the parent it is linked to.
  input: z.strictObject({ deviceId: idSchema, parentUid: idSchema.optional() }),
  authorize: async ({ uid, anonymous, db, data }: UnlinkContext) =>
    (await whoIsCaller(db, uid, anonymous, data.parentUid ?? uid, data.deviceId)) !== null,
  rateLimit: { max: 20, windowSeconds: 3600 },
  handler: async ({ uid, anonymous, db, data }: UnlinkContext): Promise<UnlinkResult> => {
    const parentUid = data.parentUid ?? uid;
    const who = await whoIsCaller(db, uid, anonymous, parentUid, data.deviceId);
    // Asked again: the other side may have unlinked between the check and here, and a former child is then nobody.
    if (who === null) return { ok: true, removed: false };
    const result = await removeDevice(db, who, parentUid, data.deviceId);
    logInfo("device unlinked", { fn: "unlinkDevice", outcome: `${who}:${result.removed ? "removed" : "already-gone"}` });
    return result;
  },
};

/** Unlinks a child's phone: callable by the parent who owns the device or by the child itself (M7-09, M8-04). */
export const unlinkDevice = secureCallable(unlinkDeviceConfig);
