import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logWarn } from "../lib/log";
import { cleanLabel, defaultNotifyDeps, pushToParent, type NotifyDeps, type SendResult } from "../lib/notify";

/** The permissions a child phone reports; anything else is shown as a generic "permission". */
const KNOWN_PERMISSIONS = new Set(["accessibility", "usage", "overlay", "notifications"]);

export interface AlertEvent {
  parentUid: string;
  deviceId: string;
  alertId: string;
  /** The new `alerts/{alertId}` document's data. */
  data: Record<string, unknown> | undefined;
}

/**
 * Pushes "Accessibility turned off on {device}" to the parent. Only a `permission_lost` alert that has not been
 * dismissed is announced (an offline alert is derived on the parent side, an unlink is pushed by `unlinkDevice`). The
 * device name is read from the device document and the permission is reduced to a known name, so no client text other
 * than the cleaned name reaches the message. Returns null when nothing should be sent.
 */
export async function handleAlertCreated(deps: NotifyDeps, event: AlertEvent): Promise<SendResult | null> {
  const { data } = event;
  if (data === undefined || data.kind !== "permission_lost" || data.dismissed !== false) {
    logWarn("ignored an alert document that is not announced", { fn: "notifyParentAlert", outcome: "not-announced" });
    return null;
  }
  const device = await deps
    .db()
    .collection("users")
    .doc(event.parentUid)
    .collection("devices")
    .doc(event.deviceId)
    .get();
  // The device was unlinked between the alert and now: nobody to tell.
  if (!device.exists) return null;
  const raw = typeof data.permission === "string" ? data.permission.toLowerCase() : "";
  return pushToParent(deps, "notifyParentAlert", event.parentUid, {
    type: "alert",
    deviceId: event.deviceId,
    alertId: event.alertId,
    device: cleanLabel(device.get("name"), "Your child's phone"),
    permission: KNOWN_PERMISSIONS.has(raw) ? raw : "other",
  });
}

/**
 * Event trigger: a child created `users/{uid}/devices/{deviceId}/alerts/{alertId}`. Deployed by Terraform with an
 * Eventarc trigger on the same path (infra/firebase/functions.tf).
 */
export const notifyParentAlert = onDocumentCreated(
  { document: "users/{uid}/devices/{deviceId}/alerts/{alertId}", region: "europe-west1" },
  async (event) => {
    const { uid, deviceId, alertId } = event.params;
    await handleAlertCreated(defaultNotifyDeps, { parentUid: uid, deviceId, alertId, data: event.data?.data() });
  },
);
