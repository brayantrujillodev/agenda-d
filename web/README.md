# web · PWA pública de reserva

La única pantalla que ve el cliente final. HTML, CSS y JavaScript nativo.
**Sin framework, sin build, sin `npm`.**

Cierra el issue #4.

## Cómo probarla

Un service worker no funciona con `file://`, hace falta un servidor estático.
Desde la raíz del repo:

```bash
# opción 1 · Python (ya lo tienes por agenda-service)
python -m http.server 5173 --directory web

# opción 2 · Node
npx serve web -l 5173
```

Abre <http://localhost:5173>. Para probar la instalación como PWA en el
celular, sirve por HTTPS o usa el reenvío de puertos de Chrome DevTools
(`chrome://inspect` → Port forwarding).

## Modo demo (sin backend)

Mientras `agenda-service` no exista, `config.js` trae `useMock: true` y
`mock.js` responde los mismos endpoints con los ejemplos de
`docs/openapi/agenda-service.yaml`:

| Endpoint | Mock |
|---|---|
| `GET /v1/publico/{slug}/servicios` | Corte de cabello (60 min) y Barba (30 min) |
| `GET /v1/publico/{slug}/disponibilidad` | jornada 08:00–18:00, lun–sáb, dos profesionales (Laura, Carlos) |
| `POST /v1/publico/{slug}/citas` | valida datos, respeta `Idempotency-Key`, y devuelve `409` en los cupos "fantasma" |
| `GET` / `DELETE /v1/gestion/{token}` | lee y cancela la cita creada (persiste en `localStorage`) |

- El estado del mock vive en `localStorage`. En la consola: `mockReset()` lo borra.
- **Probar el `409`:** cada día tiene 2 cupos que se ven libres pero rebotan al
  reservar (simulan "se acaba de ocupar entre que lo viste y confirmaste").
  Prueba varias horas hasta que uno dé el aviso con alternativas.

## Cuando `agenda-service` esté arriba

Edita **solo `config.js`**:

```js
window.CONFIG = {
  useMock: false,
  apiBase: 'http://localhost:8081',   // host real de agenda-service
  slug: 'barberia-el-corte',          // slug del negocio en la base
  ...
};
```

Con eso basta. Si quieres, luego puedes quitar `mock.js` y su línea
`<script src="mock.js">` de `index.html`; `app.js` no lo necesita cuando
`useMock` es `false`.

> ⚠️ El OpenAPI declara `servers: http://localhost:8080` pero `CLAUDE.md` y
> `docs/CONSTRUCCION.md` exponen `agenda-service` en `:8081`. Pendiente de
> aclarar con el equipo; `config.js` usa 8081 por ahora.

## Prueba rápida (sin navegador)

```bash
node web/pruebas/mock.node.mjs
```

Solo Node, sin `npm install`. Verifica que `mock.js` respeta el contrato:
formas de respuesta, `409` con alternativas, idempotencia, enmascarado del
celular, domingo cerrado. No sustituye probarlo en el navegador.

## Archivos

| Archivo | Qué es |
|---|---|
| `index.html` | shell de la app + plantillas `<template>` de cada vista |
| `style.css` | estilos, mobile-first, con modo oscuro |
| `config.js` | **lo único que se edita** al conectar el backend |
| `app.js` | router por hash, vistas y capa de red |
| `mock.js` | backend simulado con los datos del contrato |
| `manifest.json` | metadatos de instalación |
| `sw.js` | service worker: cachea la interfaz, nunca la API |
| `icons/` | íconos generados por `generar-iconos.py` (stdlib, sin deps) |
| `pruebas/mock.node.mjs` | chequeo del mock contra el contrato, solo con Node |

## Flujo

```
#/                     elegir servicio
#/reservar?servicio=…  elegir día y cupo
#/datos                nombre + celular  →  POST /citas
#/confirmada           enlace de gestión
#/gestion/{token}      ver / cancelar la cita
```

## Reglas que respeta (CLAUDE.md)

- Reservar **exige conexión**. No hay cola de reservas offline.
- El service worker cachea solo el shell; las rutas `/v1/...` van siempre a la red.
- Todos los mensajes al usuario en español y accionables; nunca un código técnico.
- El `409` no se muestra como error: se ofrecen los cupos alternativos.
