import type { Firestore } from "firebase-admin/firestore";
import { getMessaging, type BatchResponse, type MulticastMessage } from "firebase-admin/messaging";
import { logInfo, logWarn } from "./log";
import { defaultDb } from "./secureCallable";

/** FCM accepts at most 500 tokens per multicast call. */
const BATCH = 500;
/** How long FCM keeps an undelivered message: a request nobody saw for an hour is stale. */
const TTL_MS = 60 * 60 * 1000;
const MAX_LABEL = 40;

/** Error codes after which a token can never work again and is deleted. */
const DEAD_TOKEN_CODES = new Set(["messaging/registration-token-not-registered", "messaging/invalid-registration-token"]);

export interface NotifyDeps {
  db: () => Firestore;
  messaging: () => { sendEachForMulticast(message: MulticastMessage): Promise<BatchResponse> };
}

export const defaultNotifyDeps: NotifyDeps = { db: defaultDb, messaging: () => getMessaging() };

/**
 * Text that came from a child's phone, made safe to show in the parent's notification: control, line-break and
 * bidirectional-override characters are dropped (they can spoof or break the layout), whitespace is collapsed and the
 * length is capped. The notification is plain text, so there is no markup to escape.
 */
export function cleanLabel(value: unknown, fallback: string): string {
  if (typeof value !== "string") return fallback;
  const cleaned = value
    .replace(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
  if (cleaned === "") return fallback;
  return Array.from(cleaned).slice(0, MAX_LABEL).join("");
}

/** A push the parent app turns into a notification itself (data-only, so it can attach the Approve/Deny actions). */
export type PushData = Record<string, string>;

export interface SendResult {
  sent: number;
  pruned: number;
}

/**
 * Sends [data] to every FCM token the parent registered under `users/{parentUid}/fcmTokens` and deletes the tokens FCM
 * reports as gone. Never throws for a delivery problem: a failed push must not fail (and so retry) the trigger.
 */
export async function pushToParent(
  deps: NotifyDeps,
  fn: string,
  parentUid: string,
  data: PushData,
): Promise<SendResult> {
  const db = deps.db();
  const tokenCollection = db.collection("users").doc(parentUid).collection("fcmTokens");
  const tokens = (await tokenCollection.get()).docs.map((d) => d.id);
  let sent = 0;
  let pruned = 0;
  for (let i = 0; i < tokens.length; i += BATCH) {
    const batch = tokens.slice(i, i + BATCH);
    try {
      const response = await deps.messaging().sendEachForMulticast({
        tokens: batch,
        data,
        android: { priority: "high", ttl: TTL_MS },
      });
      const dead: string[] = [];
      response.responses.forEach((r, index) => {
        const token = batch[index];
        if (r.success) {
          sent += 1;
        } else if (token !== undefined && DEAD_TOKEN_CODES.has(r.error?.code ?? "")) {
          dead.push(token);
        }
      });
      await Promise.all(dead.map((t) => tokenCollection.doc(t).delete()));
      pruned += dead.length;
    } catch {
      // Never log the error: it may echo a token.
      logWarn("push batch failed", { fn, outcome: "send-failed" });
    }
  }
  logInfo("push sent", { fn, outcome: `sent=${sent} pruned=${pruned} tokens=${tokens.length}` });
  return { sent, pruned };
}
