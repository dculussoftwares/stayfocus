import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logWarn } from "../lib/log";
import { cleanLabel, defaultNotifyDeps, pushToParent, type NotifyDeps, type SendResult } from "../lib/notify";

const MAX_MINUTES = 480;

export interface RequestEvent {
  parentUid: string;
  deviceId: string;
  reqId: string;
  /** The new `requests/{reqId}` document's data. */
  data: Record<string, unknown> | undefined;
}

/**
 * Pushes "{device} asks for more time on {app}" to the parent. Only fields that passed `firestore.rules` and are
 * cleaned here reach the message; the device name is read from the device document, not from the request. Returns null
 * when the document is not a pending request this function should announce.
 */
export async function handleRequestCreated(deps: NotifyDeps, event: RequestEvent): Promise<SendResult | null> {
  const { data } = event;
  const minutes = data?.minutes;
  if (
    data === undefined ||
    data.status !== "pending" ||
    typeof minutes !== "number" ||
    !Number.isInteger(minutes) ||
    minutes < 1 ||
    minutes > MAX_MINUTES
  ) {
    logWarn("ignored a request document that does not fit", { fn: "notifyParent", outcome: "invalid-request" });
    return null;
  }
  const device = await deps
    .db()
    .collection("users")
    .doc(event.parentUid)
    .collection("devices")
    .doc(event.deviceId)
    .get();
  // The device was unlinked between the request and now: nobody to tell.
  if (!device.exists) return null;
  return pushToParent(deps, "notifyParent", event.parentUid, {
    type: "request",
    deviceId: event.deviceId,
    reqId: event.reqId,
    device: cleanLabel(device.get("name"), "Your child's phone"),
    app: cleanLabel(data.app, "an app"),
    minutes: String(minutes),
  });
}

/**
 * Event trigger: a child created `users/{uid}/devices/{deviceId}/requests/{reqId}`. Deployed by Terraform with an
 * Eventarc trigger on the same path (infra/firebase/functions.tf). Alerts (M8-06) will add a second trigger.
 */
export const notifyParent = onDocumentCreated(
  { document: "users/{uid}/devices/{deviceId}/requests/{reqId}", region: "europe-west1" },
  async (event) => {
    const { uid, deviceId, reqId } = event.params;
    await handleRequestCreated(defaultNotifyDeps, {
      parentUid: uid,
      deviceId,
      reqId,
      data: event.data?.data(),
    });
  },
);
