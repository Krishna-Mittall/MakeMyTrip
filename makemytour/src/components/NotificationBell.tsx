import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/router";
import { useSelector } from "react-redux";
import { Bell, BellRing, Check, Plane, RotateCcw, Star, Tag } from "lucide-react";
import { errorMessage, getNotifications, markAllNotificationsRead, markNotificationRead, sendPushTest } from "@/api/extra";
import { enablePush, pushSupported, syncPush } from "@/lib/push";
import { useEvents } from "@/hooks/useEvents";
import { toast } from "@/lib/toast";
import { ago } from "@/lib/format";

const icons: Record<string, any> = { FLIGHT_STATUS: Plane, REFUND: RotateCcw, REVIEW: Star, PRICE: Tag };

/** The bell in the top bar: live notifications (flight status, refunds, review replies ...). */
const NotificationBell = () => {
  const user = useSelector((state: any) => state.user.user);
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<any[]>([]);
  const [unread, setUnread] = useState(0);
  const box = useRef<HTMLDivElement>(null);
  const [permission, setPermission] = useState<string>("default");
  const [pushOn, setPushOn] = useState(false);

  const load = useCallback(async () => {
    if (!user?.id) return;
    try {
      const data = await getNotifications(user.id);
      setItems(data.items);
      setUnread(data.unread);
    } catch (e) {}
  }, [user?.id]);

  useEffect(() => {
    if (!user?.id) {
      setItems([]);
      setUnread(0);
      return;
    }
    load();
    if (typeof Notification !== "undefined") setPermission(Notification.permission);
    // the browser already allowed notifications: make sure it is registered for push messages
    syncPush(user.id).then(setPushOn);
  }, [user?.id, load]);

  // a new notification arrives while the site is open
  useEvents(["NOTIFICATION"], (n) => {
    setItems((list) => [n, ...list.filter((x) => x.id !== n.id)].slice(0, 30));
    setUnread((u) => u + 1);
    toast({ title: n.title, message: n.message, tone: n.type === "FLIGHT_STATUS" ? "warning" : "info", href: n.link });
    if (!pushOn && typeof Notification !== "undefined" && Notification.permission === "granted" && document.hidden) {
      try {
        new Notification(n.title, { body: n.message });
      } catch (e) {}
    }
  });

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  if (!user) return null;

  const openItem = async (n: any) => {
    setOpen(false);
    if (!n.read) {
      setItems((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
      setUnread((u) => Math.max(0, u - 1));
      markNotificationRead(n.id, user.id).catch(() => null);
    }
    if (n.link) router.push(n.link);
  };
  const readAll = async () => {
    setItems((list) => list.map((x) => ({ ...x, read: true })));
    setUnread(0);
    markAllNotificationsRead(user.id).catch(() => null);
  };
  const enableAlerts = async () => {
    if (typeof Notification === "undefined") return;
    try {
      const result = await enablePush(user.id);
      setPermission(Notification.permission);
      if (result === "on") {
        setPushOn(true);
        sendPushTest(user.id).catch(() => null);
      } else if (result === "denied") {
        toast({ title: "Notifications are blocked", message: "Allow notifications for this site in your browser settings.", tone: "warning" });
      } else if (result === "unavailable") {
        toast({ title: "Push is not available right now", tone: "warning" });
      }
    } catch (e) {
      // push services can be unreachable (for example offline); the in-app alerts keep working
      setPermission(Notification.permission);
      toast({ title: "Could not turn on push notifications", message: errorMessage(e), tone: "warning" });
    }
  };

  return (
    <div className="relative" ref={box}>
      <button
        aria-label="Notifications"
        className="relative h-9 w-9 rounded-full hover:bg-gray-100 flex items-center justify-center"
        onClick={() => {
          setOpen(!open);
          if (!open) load();
        }}
      >
        {unread > 0 ? <BellRing className="w-5 h-5 text-red-500" /> : <Bell className="w-5 h-5 text-gray-700" />}
        {unread > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-red-600 text-white text-[10px] font-semibold flex items-center justify-center">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="fixed sm:absolute right-2 left-2 sm:left-auto sm:right-0 top-16 sm:top-11 sm:w-96 bg-white border rounded-xl shadow-xl z-50 overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 border-b">
            <h3 className="font-semibold">Notifications</h3>
            {unread > 0 && (
              <button className="text-xs text-blue-600 hover:underline flex items-center gap-1" onClick={readAll}>
                <Check className="w-3 h-3" /> Mark all read
              </button>
            )}
          </div>
          {pushSupported() && !pushOn && permission !== "denied" && (
            <button
              onClick={enableAlerts}
              className="w-full text-left text-xs bg-blue-50 text-blue-700 px-4 py-2 hover:bg-blue-100"
            >
              Turn on push notifications for flight updates, refunds and price alerts
            </button>
          )}
          {pushSupported() && pushOn && (
            <p className="text-xs bg-green-50 text-green-700 px-4 py-2">Push notifications are on for this browser.</p>
          )}
          <div className="max-h-96 overflow-y-auto">
            {items.length === 0 && <p className="p-6 text-sm text-center text-gray-500">No notifications yet.</p>}
            {items.map((n) => {
              const Icon = icons[n.type] || Bell;
              return (
                <button
                  key={n.id}
                  onClick={() => openItem(n)}
                  className={`w-full text-left flex gap-3 px-4 py-3 border-b last:border-0 hover:bg-gray-50 ${n.read ? "" : "bg-blue-50/50"}`}
                >
                  <Icon className={`w-5 h-5 mt-0.5 shrink-0 ${n.read ? "text-gray-400" : "text-blue-600"}`} />
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900">{n.title}</p>
                    <p className="text-sm text-gray-600 line-clamp-2">{n.message}</p>
                    <p className="text-xs text-gray-400 mt-1">{ago(n.createdAt)}</p>
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

export default NotificationBell;
