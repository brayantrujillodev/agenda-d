/*
 * AGENDA-D · Service worker
 * -----------------------------------------------------------------------------
 * Cachea SOLO la interfaz (app shell) para que la app abra sin red y sea
 * instalable. Las llamadas a la API (/v1/...) NUNCA se cachean: reservar exige
 * conexión y no hay cola de reservas offline (dos personas reservando el mismo
 * cupo sin red generan un conflicto irresoluble al sincronizar).
 */
const CACHE = 'agenda-d-pwa-v2';

const APP_SHELL = [
  './',
  './index.html',
  './style.css',
  './config.js',
  './mock.js',
  './app.js',
  './manifest.json',
  './icons/favicon-32.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((c) => c.addAll(APP_SHELL)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((claves) => Promise.all(claves.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  const esApi = url.pathname.includes('/v1/');
  const mismoOrigen = url.origin === self.location.origin;

  // API o recurso de otro origen: red directa, sin cache.
  if (esApi || !mismoOrigen) return;

  // App shell: cache primero, luego red; si falla una navegación, sirve index.
  event.respondWith(
    caches.match(req).then((cacheado) => {
      if (cacheado) return cacheado;
      return fetch(req)
        .then((res) => {
          if (res.ok && res.type === 'basic') {
            const copia = res.clone();
            caches.open(CACHE).then((c) => c.put(req, copia));
          }
          return res;
        })
        .catch(() => {
          if (req.mode === 'navigate') return caches.match('./index.html');
          return Response.error();
        });
    })
  );
});
