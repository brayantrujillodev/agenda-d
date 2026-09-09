/*
 * AGENDA-D · PWA pública de reserva
 * -----------------------------------------------------------------------------
 * ESTE ES EL ÚNICO ARCHIVO QUE HAY QUE TOCAR cuando agenda-service ya corra.
 *
 *   1. Pon  useMock: false
 *   2. Ajusta  apiBase  al host real de agenda-service
 *   3. Ajusta  slug  al identificador público del negocio en la base
 *
 * Mientras useMock sea true, la PWA responde con los ejemplos del contrato
 * docs/openapi/agenda-service.yaml (ver web/mock.js). No necesita backend.
 */
window.CONFIG = {
  // true  -> usa el backend simulado de mock.js (datos del contrato)
  // false -> pega contra agenda-service de verdad
  useMock: true,

  // Base de la API REST de agenda-service.
  // CLAUDE.md y docs/CONSTRUCCION.md (paso 2) exponen agenda-service en :8081.
  // El OpenAPI declara servers: http://localhost:8080 -> discrepancia del
  // contrato pendiente de aclarar con el equipo. Si el backend queda en 8080,
  // cambia solo esta línea.
  apiBase: 'http://localhost:8081',

  // Identificador público del negocio (va en la URL de las rutas /v1/publico/{slug}/...)
  slug: 'barberia-el-corte',

  // Solo para pintar el encabezado antes de que carguen los servicios.
  negocioNombre: 'Barbería El Corte',
};
