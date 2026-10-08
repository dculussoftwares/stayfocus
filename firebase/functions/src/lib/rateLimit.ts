import { HttpsError } from "firebase-functions/v2/https";
import { Timestamp, type Firestore } from "firebase-admin/firestore";

export interface RateLimit {
  /** Maximum calls per window. */
  max: number;
  /** Window length in seconds. */
  windowSeconds: number;
}

/** Collection of counter documents (`rateLimits/{action}_{uid}`). Functions only: the rules grant no client access. */
export const RATE_LIMIT_COLLECTION = "rateLimits";

/**
 * Fixed-window per-uid counter in a Firestore transaction. Throws `resource-exhausted` when the window is used up.
 * `expireAt` lets a Firestore TTL policy (infra/firebase/functions.tf) delete stale counters.
 */
export async function consumeRateLimit(
  db: Firestore,
  action: string,
  uid: string,
  limit: RateLimit,
  now: Date = new Date(),
): Promise<void> {
  const ref = db.collection(RATE_LIMIT_COLLECTION).doc(`${action}_${uid}`);
  const windowMs = limit.windowSeconds * 1000;

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const startedAt = snap.exists ? (snap.get("windowStart") as Timestamp | undefined)?.toMillis() : undefined;
    const count = snap.exists ? ((snap.get("count") as number | undefined) ?? 0) : 0;

    if (startedAt === undefined || now.getTime() - startedAt >= windowMs) {
      tx.set(ref, {
        count: 1,
        windowStart: Timestamp.fromDate(now),
        expireAt: Timestamp.fromMillis(now.getTime() + 2 * windowMs),
      });
      return;
    }
    if (count >= limit.max) {
      throw new HttpsError("resource-exhausted", "Too many requests. Try again later.");
    }
    tx.update(ref, { count: count + 1 });
  });
}
