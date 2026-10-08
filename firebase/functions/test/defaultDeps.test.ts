import { randomUUID } from "node:crypto";
import { getApps } from "firebase-admin/app";
import type { CallableRequest } from "firebase-functions/v2/https";
import { z } from "zod";
import { secureHandler } from "../src/lib/secureCallable";

// This file never initializes the Admin app itself: the production default (`defaultDb`) must do it, as it does in
// a deployed function. The emulator supplies the project and host through the environment.
it("initializes the Admin app on first use with the default dependencies", async () => {
  expect(getApps()).toHaveLength(0);
  const run = secureHandler({
    name: `t-${randomUUID()}`,
    input: z.strictObject({}),
    rateLimit: { max: 1, windowSeconds: 60 },
    handler: () => "ok",
  });
  const request = {
    data: {},
    auth: { uid: `u-${randomUUID()}`, token: {} },
    app: { appId: "a", token: {}, alreadyConsumed: false },
  } as unknown as CallableRequest<unknown>;

  await expect(run(request)).resolves.toBe("ok");
  expect(getApps()).toHaveLength(1);
});
