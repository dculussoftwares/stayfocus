import { FieldValue, Timestamp, type Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { z } from "zod";
import { logInfo } from "../lib/log";
import { isExpired, LINK_TOKENS, linkFailure, tokenSchema, type LinkTokenData } from "../lib/linkToken";
import { secureCallable } from "../lib/secureCallable";

export type ApproveResult =
  | { linked: true; parentUid: string; deviceId: string; parentName: string }
  | { linked: false };

/**
 * The child answers the consent screen, in one transaction. Only the token's `childUid` may answer, and only for a
 * token the parent has claimed and that has not expired. Approving creates `users/{parent}/devices/{deviceId}` with
 * every status field the rules expect and marks the token `used`; declining marks it `declined`. Either way the token
 * cannot be answered twice.
 */
export async function answerLink(
  db: Firestore,
  childUid: string,
  token: string,
  approve: boolean,
  now: Date = new Date(),
): Promise<ApproveResult> {
  const ref = db.collection(LINK_TOKENS).doc(token);

  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw linkFailure("invalid");
    const data = snap.data() as LinkTokenData;
    if (data.childUid !== childUid) throw new HttpsError("permission-denied", "You are not allowed to do this.");
    // The device and token are committed atomically before the callable response is sent. If that response is lost,
    // let the same child safely recover the result instead of attempting a second create.
    if (data.status === "used" && approve && data.answeredBy === childUid && data.claimedBy && data.deviceId) {
      return {
        linked: true as const,
        parentUid: data.claimedBy,
        deviceId: data.deviceId,
        parentName: data.parentName ?? "",
      };
    }
    if (data.status === "open") throw linkFailure("not_claimed");
    if (data.status !== "claimed" || !data.claimedBy || !data.deviceId) throw linkFailure("used");
    if (isExpired(data, now)) throw linkFailure("expired");

    if (!approve) {
      tx.update(ref, { status: "declined", answeredAt: Timestamp.fromDate(now) });
      return { linked: false as const };
    }

    const parentRef = db.collection("users").doc(data.claimedBy);
    // The parent deleted their account while the child was reading: do not create a device in a deleted tree.
    if (!(await tx.get(parentRef)).exists) throw linkFailure("invalid");

    tx.create(parentRef.collection("devices").doc(data.deviceId), {
      name: data.deviceName ?? data.model,
      model: data.model,
      linkedAt: FieldValue.serverTimestamp(),
      childUid,
      // The status fields the child's update rule needs to find present (docs/IMPLEMENTATION_PLAN.md section 7).
      online: false,
      charging: false,
      battery: null,
      currentApp: null,
      lastSeen: null,
      focusEndsAt: null,
    });
    tx.update(ref, { status: "used", answeredAt: Timestamp.fromDate(now), answeredBy: childUid });
    return {
      linked: true as const,
      parentUid: data.claimedBy,
      deviceId: data.deviceId,
      parentName: data.parentName ?? "",
    };
  });
}

/** The callable's configuration, exported so tests exercise the real checks without the Functions framework. */
export const approveLinkConfig = {
  name: "approveLink",
  input: z.strictObject({ token: tokenSchema, approve: z.boolean() }),
  rateLimit: { max: 20, windowSeconds: 3600 },
  consumeAppCheckToken: true,
  handler: async ({ uid, db, data }: { uid: string; db: Firestore; data: { token: string; approve: boolean } }) => {
    const answer = await answerLink(db, uid, data.token, data.approve);
    logInfo("link answered", { fn: "approveLink", outcome: answer.linked ? "linked" : "declined" });
    return answer;
  },
};

export const approveLink = secureCallable(approveLinkConfig);
