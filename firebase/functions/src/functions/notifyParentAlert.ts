import { Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logWarn } from "../lib/log";
import { cleanLabel, defaultNotifyDeps, pushToParent, type NotifyDeps, type SendResult } from "../lib/notify";

/**
 * One permission being switched off and on over and over is a flood, not news: after this many alerts for it within
 * the window the rest are not pushed. Each distinct permission has its own budget, so a different permission lost
 * later is still announced.
 */
const MAX_ALERTS_PER_PERMISSION = 3;
const WINDOW_MS = 10 * 60 * 1000;
/** Upper bound on the documents read to count the window. */
const WINDOW_READ_LIMIT = 40;

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
 * Pushes "Accessibility turned off on {device}" to the parent. Only a `permission_lost` alert that is still
 * undismissed is announced (an offline alert is derived on the parent side, an unlink is pushed by `unlinkDevice`). The
 * device name is read from the device document and the permission is reduced to a known name, so no client text other
 * than the cleaned name reaches the message. Returns null when nothing should be sent.
 */
export async function handleAlertCreated(deps: NotifyDeps, event: AlertEvent): Promise<SendResult | null> {
  const { data } = event;
  if (data === undefined || data.kind !== "permission_lost" || data.dismissed !== false) {
    logWarn("ignored an alert document that is not announced", { fn: "notifyParentAlert", outcome: "not-announced" });
    return null;
  }
  const deviceRef = deps.db().collection("users").doc(event.parentUid).collection("devices").doc(event.deviceId);
  const device = await deviceRef.get();
  // The device was unlinked between the alert and now: nobody to tell.
  if (!device.exists) return null;
  // The parent may have dismissed the alert (or the device was unlinked and its alerts deleted) before this ran.
  const current = await deviceRef.collection("alerts").doc(event.alertId).get();
  if (!current.exists || current.get("dismissed") !== false) return null;
  const raw = typeof data.permission === "string" ? data.permission.toLowerCase() : "";
  const permission = KNOWN_PERMISSIONS.has(raw) ? raw : "other";
  // Rules let a linked child create alerts freely, so cap the pushes one phone can cause for one permission.
  const recent = await deviceRef
    .collection("alerts")
    .where("createdAt", ">=", Timestamp.fromMillis(Date.now() - WINDOW_MS))
    .limit(WINDOW_READ_LIMIT)
    .get();
  const sameKind = recent.docs.filter((d) => {
    const p: unknown = d.get("permission");
    const name = typeof p === "string" ? p.toLowerCase() : "";
    return d.get("kind") === "permission_lost" && (KNOWN_PERMISSIONS.has(name) ? name : "other") === permission;
  });
  if (sameKind.length > MAX_ALERTS_PER_PERMISSION) {
    logWarn("too many alerts for one permission; push skipped", { fn: "notifyParentAlert", outcome: "rate-limited" });
    return null;
  }
  return pushToParent(deps, "notifyParentAlert", event.parentUid, {
    type: "alert",
    deviceId: event.deviceId,
    alertId: event.alertId,
    device: cleanLabel(device.get("name"), "Your child's phone"),
    permission,
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
