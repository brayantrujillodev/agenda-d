/*
 * AGENDA-D · Service worker
 * -----------------------------------------------------------------------------
 * Cachea SOLO la interfaz (app shell) para que la app abra sin red y sea
 * instalable. Las llamadas a la API (/v1/...) NUNCA se cachean: reservar exige
 * conexión y no hay cola de reservas offline (dos personas reservando el mismo
 * cupo sin red generan un conflicto irresoluble al sincronizar).
 *
 * Estrategias:
 *   - Navegaciones (HTML): red primero, cache si no hay red. Con conexión
 *     siempre se ve la página publicada más reciente.
 *   - Resto del app shell (JS, CSS, íconos): cache primero para abrir rápido,
 *     y en paralelo se pide a la red y se actualiza la cache
 *     (stale-while-revalidate). Un cambio en app.js llega a más tardar en la
 *     siguiente visita, sin depender de que alguien suba el número de CACHE.
 *   - Cuando cambia ESTE archivo, el SW nuevo toma el control y la página
 *     muestra "Hay una versión nueva" (ver app.js, banner-version).
 */
const CACHE = 'agenda-d-pwa-v4';

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
  // Panel de recepción (docs/TAREAS.md #20) — mismo criterio de app shell.
  './panel.html',
  './panel.css',
  './panel.js',
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

function guardar(req, res) {
  if (res.ok && res.type === 'basic') {
    const copia = res.clone();
    caches.open(CACHE).then((c) => c.put(req, copia));
  }
  return res;
}

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  const esApi = url.pathname.includes('/v1/');
  const mismoOrigen = url.origin === self.location.origin;

  // API o recurso de otro origen: red directa, sin cache.
  if (esApi || !mismoOrigen) return;

  // Navegación: red primero; sin red, la copia guardada (o index si no hay).
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => guardar(req, res))
        .catch(() => caches.match(req).then((c) => c || caches.match('./index.html')))
    );
    return;
  }

  // App shell: cache primero y revalidación en segundo plano.
  event.respondWith(
    caches.match(req).then((cacheado) => {
      const deRed = fetch(req).then((res) => guardar(req, res));
      if (cacheado) {
        event.waitUntil(deRed.catch(() => { /* sin red: se queda la copia */ }));
        return cacheado;
      }
      return deRed.catch(() => Response.error());
    })
  );
});
