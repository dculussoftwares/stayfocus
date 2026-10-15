import { getAuth } from "firebase-admin/auth";
import type { Firestore } from "firebase-admin/firestore";
import { z } from "zod";
import { logInfo } from "../lib/log";
import { secureCallable } from "../lib/secureCallable";

/** The sign-in must be at most this old (Firebase `auth_time`); the app re-authenticates the person otherwise. */
export const MAX_AUTH_AGE_SECONDS = 5 * 60;

/** What deleting a user needs from Firebase Auth (the Admin SDK's `deleteUser`). */
export interface UserDeleter {
  deleteUser(uid: string): Promise<void>;
}

/**
 * Deletes everything held for `uid`: `users/{uid}` with every subcollection (devices and their subtrees, fcmTokens,
 * blocks), the link tokens the account claimed, the AI reports it filed, and finally the Auth user. Auth goes last so a
 * failure halfway leaves a signed-in user who can simply retry; every step is idempotent.
 */
export async function deleteAccountData(db: Firestore, auth: UserDeleter, uid: string): Promise<void> {
  await db.recursiveDelete(db.collection("users").doc(uid));

  const claimed = await db.collection("linkTokens").where("claimedBy", "==", uid).get();
  await Promise.all(claimed.docs.map((doc) => doc.ref.delete()));

  // "Report a bad result" filings carry the uid, the sentence and the draft: personal data, so they go too.
  const reports = await db.collection("aiReports").where("uid", "==", uid).get();
  await Promise.all(reports.docs.map((doc) => doc.ref.delete()));

  try {
    await auth.deleteUser(uid);
  } catch (e) {
    // Already gone (a retry after a partial run): nothing left to do.
    if ((e as { code?: string }).code !== "auth/user-not-found") throw e;
  }
}

/**
 * Deletes the signed-in parent's account and all their data (Play's in-app account-deletion requirement). Linked
 * children see their device document disappear and reset (M7-09).
 */
/** The callable's configuration, exported so tests exercise the real checks without the Functions framework. */
export const deleteAccountConfig = {
  name: "deleteAccount",
  input: z.strictObject({}),
  // Parents only: an anonymous (Kids) sign-in has no account to delete.
  authorize: ({ anonymous }: { anonymous: boolean }) => !anonymous,
  maxAuthAgeSeconds: MAX_AUTH_AGE_SECONDS,
  rateLimit: { max: 5, windowSeconds: 3600 },
  handler: async ({ uid, db }: { uid: string; db: Firestore }) => {
    await deleteAccountData(db, getAuth(), uid);
    logInfo("account deleted", { fn: "deleteAccount", outcome: "ok" });
    return { ok: true as const };
  },
};

export const deleteAccount = secureCallable(deleteAccountConfig);
