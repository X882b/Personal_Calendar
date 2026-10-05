// Bump this string every time you change index.html, or phones keep the old copy.
const VERSION = "schedule-v1";

const FILES = [
  "./",
  "./index.html",
  "./manifest.json",
  "./icon-192.png",
  "./icon-512.png",
  "./icon-maskable.png"
];

// On install, pull everything into the cache and take over immediately.
self.addEventListener("install", e => {
  e.waitUntil(
    caches.open(VERSION)
      .then(c => c.addAll(FILES))
      .then(() => self.skipWaiting())
  );
});

// On activate, throw away caches from older versions.
self.addEventListener("activate", e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== VERSION).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// Serve the app from cache first so it opens with no signal, and refresh the
// cached copy in the background. Calls to GitHub (the shared calendar) are
// never touched: they must always hit the network.
self.addEventListener("fetch", e => {
  if (e.request.method !== "GET") return;
  if (new URL(e.request.url).origin !== self.location.origin) return;
  e.respondWith(
    caches.match(e.request, { ignoreSearch: true }).then(hit => {
      const live = fetch(e.request).then(res => {
        if (res && res.status === 200 && res.type === "basic") {
          const copy = res.clone();
          caches.open(VERSION).then(c => c.put(e.request, copy));
        }
        return res;
      }).catch(() => hit);
      return hit || live;
    })
  );
});
