import type { Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { z } from "zod";

export const LINK_TOKENS = "linkTokens";

/** The token as the Kids app mints it: 22 base64url characters; the rules accept 16..128. */
export const tokenSchema = z.string().regex(/^[A-Za-z0-9_-]{16,128}$/);

/** Why a handshake step was refused; the apps map this to their own copy. */
export type LinkFailureReason = "invalid" | "expired" | "used" | "not_claimed";

export function linkFailure(reason: LinkFailureReason): HttpsError {
  // Fixed messages: nothing about the token or the people involved is echoed.
  const code = reason === "invalid" ? "not-found" : "failed-precondition";
  return new HttpsError(code, "This code can't be used.", { reason });
}

export interface LinkTokenData {
  childUid: string;
  model: string;
  expiresAt: Timestamp;
  status: string;
  code?: string;
  claimedBy?: string;
  deviceId?: string;
  deviceName?: string;
  parentName?: string;
  /** The child that completed approval, retained so a lost response can be retried safely. */
  answeredBy?: string;
}

export function isExpired(token: LinkTokenData, now: Date): boolean {
  return !(token.expiresAt.toMillis() > now.getTime());
}

/** Six random digits from the OS CSPRNG (`crypto.randomInt` is uniform: no modulo bias). */
export function sixDigits(randomInt: (min: number, max: number) => number): string {
  return String(randomInt(0, 1_000_000)).padStart(6, "0");
}
