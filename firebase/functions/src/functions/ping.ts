import { z } from "zod";
import { secureCallable } from "../lib/secureCallable";

/**
 * Sample callable that proves the pipeline end to end (build, zip, Terraform deploy, App Check, auth, rate limit).
 * Remove it, together with its entry in infra/firebase/functions.tf, once a real function (M6-06, M7-07, ...) exists.
 */
export const ping = secureCallable({
  name: "ping",
  input: z.strictObject({}),
  rateLimit: { max: 60, windowSeconds: 3600 },
  handler: () => ({ ok: true as const }),
});
