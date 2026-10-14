/* Service worker: shows browser push notifications (flight status, refunds, price alerts) even when the site is closed. */
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    data = { title: "MakeMyTour", body: event.data ? event.data.text() : "" };
  }
  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
      // the site is open and visible: the in-app toast already shows this message
      if (windows.some((w) => w.visibilityState === "visible")) return;
      await self.registration.showNotification(data.title || "MakeMyTour", {
        body: data.body || "",
        tag: data.tag || "mmt",
        data: { url: data.url || "/" },
        icon: "/favicon.ico",
        badge: "/favicon.ico",
      });
    })()
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || "/";
  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
      for (const w of windows) {
        if ("focus" in w) {
          await w.focus();
          if ("navigate" in w) await w.navigate(url);
          return;
        }
      }
      await self.clients.openWindow(url);
    })()
  );
});
