// Browser push notifications (Web Push): registers the service worker and tells the backend where to send messages.
import { getPushKey, savePushSubscription, removePushSubscription } from "@/api/extra";

export const pushSupported = () =>
  typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;

const toKey = (base64: string) => {
  const padding = "=".repeat((4 - (base64.length % 4)) % 4);
  const raw = atob((base64 + padding).replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(Array.from(raw).map((c) => c.charCodeAt(0)));
};

async function subscription(): Promise<PushSubscription | null> {
  const reg = await navigator.serviceWorker.register("/sw.js");
  await navigator.serviceWorker.ready;
  return reg.pushManager.getSubscription();
}

/** Asks for permission (when needed), subscribes this browser and registers it for the user. */
export async function enablePush(userId: string): Promise<"on" | "denied" | "unsupported" | "unavailable"> {
  if (!pushSupported()) return "unsupported";
  const permission = Notification.permission === "default" ? await Notification.requestPermission() : Notification.permission;
  if (permission !== "granted") return "denied";
  const { enabled, publicKey } = await getPushKey();
  if (!enabled || !publicKey) return "unavailable";
  const reg = await navigator.serviceWorker.register("/sw.js");
  await navigator.serviceWorker.ready;
  let sub = await reg.pushManager.getSubscription();
  if (!sub) {
    sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: toKey(publicKey) });
  }
  await savePushSubscription(userId, sub.toJSON());
  return "on";
}

/** After a login: when the user already allowed notifications, make sure this browser is registered for them. */
export async function syncPush(userId: string): Promise<boolean> {
  if (!pushSupported() || Notification.permission !== "granted") return false;
  try {
    const result = await enablePush(userId);
    return result === "on";
  } catch (e) {
    return false;
  }
}

export async function disablePush(userId: string) {
  if (!pushSupported()) return;
  const sub = await subscription();
  if (sub) {
    await removePushSubscription(userId, sub.endpoint).catch(() => null);
    await sub.unsubscribe().catch(() => null);
  }
}
