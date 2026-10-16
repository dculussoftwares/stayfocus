import { randomInt } from "node:crypto";
import { Timestamp, type Firestore } from "firebase-admin/firestore";
import { z } from "zod";
import { logInfo } from "../lib/log";
import {
  isExpired,
  LINK_TOKENS,
  linkFailure,
  sixDigits,
  tokenSchema,
  type LinkTokenData,
} from "../lib/linkToken";
import { secureCallable } from "../lib/secureCallable";

const MAX_NAME = 60;

export interface ClaimResult {
  /** The 6 digits both phones show. */
  code: string;
  /** The child phone's model, as the child wrote it. */
  model: string;
  /** The device document id the link will create (`users/{uid}/devices/{deviceId}`). */
  deviceId: string;
  /** Epoch milliseconds; the child's consent must arrive before this. */
  expiresAtMillis: number;
  /** True when the child had already allowed this link (the parent app was away): open the device. */
  linked?: true;
}

/**
 * Claims a one-time link token for the signed-in parent, in one transaction: the token must be unexpired and still
 * open. The server (not the child) picks the 6-digit code. Calling it again as the same parent while the token is
 * `claimed` is allowed and only changes the device name, so a replay by anyone else, or after approval, is refused.
 */
export async function claimToken(
  db: Firestore,
  uid: string,
  token: string,
  deviceName: string | undefined,
  now?: Date,
  cancel = false,
): Promise<ClaimResult | { cancelled: true }> {
  const ref = db.collection(LINK_TOKENS).doc(token);
  const parentRef = db.collection("users").doc(uid);
  const name = deviceName?.trim() || undefined;

  return db.runTransaction(async (tx) => {
    // Read again on every attempt: a retried transaction must not judge expiry by the time it first started.
    const at = now ?? new Date();
    const [snap, parentSnap] = await Promise.all([tx.get(ref), tx.get(parentRef)]);
    if (!snap.exists) throw linkFailure("invalid");
    const data = snap.data() as LinkTokenData;
    if (!cancel && data.status === "used" && data.claimedBy === uid && data.code && data.deviceId) {
      // The child allowed it while this phone was away (app restarted): report it as linked instead of failing, unless
      // the device has been removed since (account deletion).
      const device = parentRef.collection("devices").doc(data.deviceId);
      if (!(await tx.get(device)).exists) throw linkFailure("invalid");
      return { ...result(data, data.code, data.deviceId), linked: true as const };
    }
    if (isExpired(data, at)) throw linkFailure("expired");

    if (cancel) {
      // The parent says the codes do not match (or leaves): the child can no longer approve this token.
      if (data.status === "claimed" && data.claimedBy === uid) {
        tx.update(ref, { status: "declined", answeredAt: Timestamp.fromDate(at) });
        return { cancelled: true as const };
      }
      throw linkFailure("used");
    }
    if (data.status === "claimed" && data.claimedBy === uid && data.code && data.deviceId) {
      if (name !== undefined) tx.update(ref, { deviceName: name });
      return result(data, data.code, data.deviceId);
    }
    // Claimed by someone else, approved, declined, or malformed: the same answer, so nothing leaks.
    if (data.status !== "open" || data.claimedBy !== undefined) throw linkFailure("used");

    const code = sixDigits(randomInt);
    const deviceId = db.collection("users").doc().id;
    tx.update(ref, {
      status: "claimed",
      claimedBy: uid,
      claimedAt: Timestamp.fromDate(at),
      code,
      deviceId,
      deviceName: name ?? data.model,
      parentName: firstName(parentSnap.get("displayName")),
    });
    return result(data, code, deviceId);
  });
}

function result(data: LinkTokenData, code: string, deviceId: string): ClaimResult {
  return { code, model: data.model, deviceId, expiresAtMillis: data.expiresAt.toMillis() };
}

/** The parent's first name for the child's consent screen; empty when the profile has none (the app words it). */
function firstName(displayName: unknown): string {
  if (typeof displayName !== "string") return "";
  return (displayName.trim().split(/\s+/)[0] ?? "").slice(0, MAX_NAME);
}

/** The callable's configuration, exported so tests exercise the real checks without the Functions framework. */
export const claimLinkTokenConfig = {
  name: "claimLinkToken",
  input: z.strictObject({ token: tokenSchema,
    deviceName: z.string().trim().min(1).max(MAX_NAME).optional(),
    cancel: z.literal(true).optional(),
  }),
  // Parents only: the Kids app signs in anonymously.
  authorize: ({ anonymous }: { anonymous: boolean }) => !anonymous,
  rateLimit: { max: 30, windowSeconds: 3600 },
  // One-shot: each call needs a fresh App Check token, so a captured request cannot be replayed.
  consumeAppCheckToken: true,
  handler: async ({ uid, db, data }: { uid: string; db: Firestore; data: { token: string; deviceName?: string; cancel?: true } }) => {
    const claimed = await claimToken(db, uid, data.token, data.deviceName, new Date(), data.cancel === true);
    logInfo("link token claimed", { fn: "claimLinkToken", outcome: "ok" });
    return claimed;
  },
};

export const claimLinkToken = secureCallable(claimLinkTokenConfig);
