import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, type Firestore } from "firebase-admin/firestore";
import {
  HttpsError,
  onCall,
  type CallableFunction,
  type CallableOptions,
  type CallableRequest,
} from "firebase-functions/v2/https";
import { z } from "zod";
import { logError, logWarn } from "./log";
import { consumeRateLimit, type RateLimit } from "./rateLimit";

export const REGION = "europe-west1";

/** What a handler (and its `authorize` hook) gets after every check has passed. */
export interface SecureContext<In> {
  uid: string;
  /** Validated input. */
  data: In;
  /** Whether the Firebase sign-in is anonymous (the Kids app), from the verified ID token. */
  anonymous: boolean;
  db: Firestore;
}

export interface SecureCallableConfig<S extends z.ZodType, Out> {
  /** Function name; also the rate-limit action and the log field. */
  name: string;
  /** zod schema for `request.data`. Use `z.strictObject` so unknown fields are rejected. */
  input: S;
  /**
   * Role check in code (for example "the caller is the parent of `childUid`"). Return false, or throw an HttpsError,
   * to refuse with `permission-denied`.
   */
  authorize?: (ctx: SecureContext<z.output<S>>) => boolean | Promise<boolean>;
  /** Per-uid rate limit. Omit only for functions that are cheap and idempotent. */
  rateLimit?: RateLimit;
  /** One-shot operations (link claim/approve): the App Check token can be used once; a replay is refused. */
  consumeAppCheckToken?: boolean;
  handler: (ctx: SecureContext<z.output<S>>) => Promise<Out> | Out;
}

export interface SecureDeps {
  db: () => Firestore;
  now: () => Date;
}

/** Initializes the default Admin app on first use (credentials come from the function's runtime service account). */
export function defaultDb(): Firestore {
  if (getApps().length === 0) initializeApp();
  return getFirestore();
}

const defaultDeps: SecureDeps = { db: defaultDb, now: () => new Date() };

/**
 * The baseline every callable shares, in this order: App Check (and replay protection), sign-in, per-uid rate limit,
 * input validation, role check, handler. Errors are typed `HttpsError`s with fixed messages; anything unexpected becomes
 * a generic `internal` error and only its function name and code are logged.
 *
 * Exported separately from `secureCallable` so the checks can be tested without the Functions framework.
 */
export function secureHandler<S extends z.ZodType, Out>(
  config: SecureCallableConfig<S, Out>,
  deps: SecureDeps = defaultDeps,
): (request: CallableRequest<unknown>) => Promise<Out> {
  return async (request) => {
    try {
      // enforceAppCheck rejects these at the framework level; checked again so the baseline never depends on options.
      if (!request.app) {
        throw new HttpsError("unauthenticated", "The app could not be verified.");
      }
      if (config.consumeAppCheckToken && request.app.alreadyConsumed) {
        throw new HttpsError("unauthenticated", "The app could not be verified.");
      }
      const uid = request.auth?.uid;
      if (!uid) {
        throw new HttpsError("unauthenticated", "Sign in to continue.");
      }

      const db = deps.db();
      if (config.rateLimit) {
        await consumeRateLimit(db, config.name, uid, config.rateLimit, deps.now());
      }

      const parsed = config.input.safeParse(request.data);
      if (!parsed.success) {
        // Field paths only: never echo values.
        const fields = [...new Set(parsed.error.issues.map((i) => i.path.join(".")))];
        throw new HttpsError("invalid-argument", "The request is not valid.", { fields });
      }

      const ctx: SecureContext<z.output<S>> = {
        uid,
        data: parsed.data,
        anonymous: request.auth?.token?.firebase?.sign_in_provider === "anonymous",
        db,
      };
      if (config.authorize && !(await config.authorize(ctx))) {
        throw new HttpsError("permission-denied", "You are not allowed to do this.");
      }

      return await config.handler(ctx);
    } catch (e) {
      if (e instanceof HttpsError) {
        logWarn("callable refused", { fn: config.name, outcome: "refused", code: e.code });
        throw e;
      }
      // The error message may echo personal data, so log the error name and the stack frames (file:line) only.
      logError("callable failed", {
        fn: config.name,
        outcome: "error",
        code: e instanceof Error ? e.name : "unknown",
        frames: e instanceof Error ? stackFrames(e) : undefined,
      });
      throw new HttpsError("internal", "Something went wrong.");
    }
  };
}

function stackFrames(e: Error): string[] {
  return (e.stack ?? "")
    .split("\n")
    .filter((line) => line.trimStart().startsWith("at "))
    .slice(0, 8)
    .map((line) => line.trim());
}

/**
 * A callable with the security baseline: `enforceAppCheck: true`, europe-west1. The runtime service account is not set
 * here: Terraform creates one per function and attaches it (infra/firebase/functions.tf).
 */
export function secureCallable<S extends z.ZodType, Out>(
  config: SecureCallableConfig<S, Out>,
): CallableFunction<unknown, Promise<Out>> {
  const options: CallableOptions = {
    region: REGION,
    enforceAppCheck: true,
    consumeAppCheckToken: config.consumeAppCheckToken ?? false,
  };
  return onCall(options, secureHandler(config));
}
