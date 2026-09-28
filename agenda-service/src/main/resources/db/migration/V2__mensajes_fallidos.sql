-- =====================================================================
-- AGENDA-D · V2 · Cola de mensajes fallidos (docs/TAREAS.md #16)
--
-- Cuando un consumidor (notificaciones-service o analitica-service)
-- falla tres veces con espera creciente (1 s, 4 s, 16 s) procesando un
-- evento de citas.reservadas, citas.canceladas o citas.estado, el
-- mensaje se publica en el tópico citas.dlq en vez de perderse o de
-- bloquear la partición (docs/eventos/CONTRATO-EVENTOS.md). notifica-
-- ciones-service consume ese tópico y deja constancia aquí, para que
-- el negocio pueda ver qué no se procesó.
-- =====================================================================

CREATE TABLE notificaciones.mensaje_fallido (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topico_origen   TEXT        NOT NULL,
    payload         TEXT        NOT NULL,
    motivo          TEXT,
    intentos        INTEGER     NOT NULL DEFAULT 0,
    fallido_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mensaje_fallido_fecha ON notificaciones.mensaje_fallido(fallido_en DESC);
