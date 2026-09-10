-- =====================================================================
-- AGENDA-D · Migración inicial
-- Ubicación en el repo: agenda-service/src/main/resources/db/migration/
--
-- Un esquema por servicio. Sin llaves foráneas entre esquemas:
-- cada servicio es dueño de sus datos.
-- Todos los instantes se almacenan en UTC (timestamptz).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";    -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "btree_gist";  -- necesaria para el EXCLUDE

CREATE SCHEMA IF NOT EXISTS agenda;
CREATE SCHEMA IF NOT EXISTS notificaciones;
CREATE SCHEMA IF NOT EXISTS analitica;


-- =====================================================================
-- ESQUEMA AGENDA · dominio principal
-- =====================================================================

CREATE TABLE agenda.negocio (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre            TEXT        NOT NULL,
    slug_publico      TEXT        NOT NULL UNIQUE,   -- va en la URL: /publico/{slug}/...
    zona_horaria      TEXT        NOT NULL DEFAULT 'America/Bogota',
    canal_primario    TEXT        NOT NULL DEFAULT 'REGISTRO',
    canal_respaldo    TEXT,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT canal_primario_valido
        CHECK (canal_primario IN ('REGISTRO','WHATSAPP','SMS','EMAIL')),
    CONSTRAINT canal_respaldo_valido
        CHECK (canal_respaldo IS NULL OR canal_respaldo IN ('REGISTRO','WHATSAPP','SMS','EMAIL'))
);

COMMENT ON COLUMN agenda.negocio.slug_publico IS
    'Identificador del negocio en la ruta pública. Resuelve el contexto sin autenticación.';


CREATE TABLE agenda.servicio (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id    UUID    NOT NULL REFERENCES agenda.negocio(id) ON DELETE CASCADE,
    nombre        TEXT    NOT NULL,
    duracion_min  INTEGER NOT NULL,
    precio        NUMERIC(12,2) NOT NULL DEFAULT 0,
    activo        BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT duracion_positiva CHECK (duracion_min > 0 AND duracion_min <= 480)
);
CREATE INDEX idx_servicio_negocio ON agenda.servicio(negocio_id) WHERE activo;


CREATE TABLE agenda.profesional (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id  UUID    NOT NULL REFERENCES agenda.negocio(id) ON DELETE CASCADE,
    nombre      TEXT    NOT NULL,
    activo      BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX idx_profesional_negocio ON agenda.profesional(negocio_id) WHERE activo;


-- Qué servicios presta cada profesional
CREATE TABLE agenda.servicio_profesional (
    servicio_id     UUID NOT NULL REFERENCES agenda.servicio(id)    ON DELETE CASCADE,
    profesional_id  UUID NOT NULL REFERENCES agenda.profesional(id) ON DELETE CASCADE,
    PRIMARY KEY (servicio_id, profesional_id)
);


-- Horario semanal recurrente, en HORA LOCAL del negocio.
-- Se convierte a UTC al calcular la disponibilidad.
CREATE TABLE agenda.horario_atencion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id      UUID     NOT NULL REFERENCES agenda.negocio(id) ON DELETE CASCADE,
    profesional_id  UUID     NOT NULL REFERENCES agenda.profesional(id) ON DELETE CASCADE,
    dia_semana      SMALLINT NOT NULL,   -- 1 = lunes ... 7 = domingo (ISO-8601)
    hora_inicio     TIME     NOT NULL,
    hora_fin        TIME     NOT NULL,
    CONSTRAINT dia_valido   CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT rango_valido CHECK (hora_fin > hora_inicio)
);
CREATE INDEX idx_horario_prof_dia ON agenda.horario_atencion(profesional_id, dia_semana);


-- Excepciones puntuales: vacaciones, incapacidad, festivo. En UTC.
CREATE TABLE agenda.bloqueo (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id      UUID        NOT NULL REFERENCES agenda.negocio(id) ON DELETE CASCADE,
    profesional_id  UUID        NOT NULL REFERENCES agenda.profesional(id) ON DELETE CASCADE,
    inicio          TIMESTAMPTZ NOT NULL,
    fin             TIMESTAMPTZ NOT NULL,
    motivo          TEXT,
    CONSTRAINT bloqueo_rango_valido CHECK (fin > inicio)
);
CREATE INDEX idx_bloqueo_prof ON agenda.bloqueo(profesional_id, inicio);


-- ---------------------------------------------------------------------
-- CITA · el corazón del sistema
-- ---------------------------------------------------------------------
CREATE TABLE agenda.cita (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id        UUID        NOT NULL REFERENCES agenda.negocio(id),
    servicio_id       UUID        NOT NULL REFERENCES agenda.servicio(id),
    profesional_id    UUID        NOT NULL REFERENCES agenda.profesional(id),
    inicio            TIMESTAMPTZ NOT NULL,
    fin               TIMESTAMPTZ NOT NULL,
    cliente_nombre    TEXT        NOT NULL,
    cliente_celular   TEXT        NOT NULL,
    estado            TEXT        NOT NULL DEFAULT 'CONFIRMADA',
    idempotency_key   TEXT,
    creada_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizada_en    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT cita_rango_valido CHECK (fin > inicio),
    CONSTRAINT cita_estado_valido
        CHECK (estado IN ('CONFIRMADA','CANCELADA','ATENDIDA','NO_ASISTIO'))
);

-- ---------------------------------------------------------------------
-- RNF-01 · AQUÍ VIVE LA GARANTÍA DE QUE NO HAY SOLAPAMIENTO.
--
-- No la ponemos en el código: entre el SELECT que pregunta "¿está libre?"
-- y el INSERT que escribe, cabe otra petición. La base de datos arbitra.
--
-- Y NO sirve un UNIQUE(profesional_id, inicio): una cita de 60 min a las
-- 10:00 y otra a las 10:30 tienen inicios distintos y aun así se pisan.
-- Por eso comparamos RANGOS con && (se superponen), no instantes con =.
--
-- El WHERE excluye las canceladas: ese cupo vuelve a estar libre.
-- ---------------------------------------------------------------------
ALTER TABLE agenda.cita ADD CONSTRAINT cita_sin_solape
    EXCLUDE USING gist (
        profesional_id WITH =,
        tstzrange(inicio, fin, '[)') WITH &&
    ) WHERE (estado <> 'CANCELADA');

-- RNF-05 · reenviar la misma petición no crea una segunda cita
CREATE UNIQUE INDEX idx_cita_idempotency
    ON agenda.cita(negocio_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Consultas más frecuentes: agenda del día y cálculo de disponibilidad
CREATE INDEX idx_cita_prof_inicio ON agenda.cita(profesional_id, inicio)
    WHERE estado <> 'CANCELADA';
CREATE INDEX idx_cita_negocio_inicio ON agenda.cita(negocio_id, inicio);


-- Permite al cliente operar su cita sin tener cuenta (RNF-09)
CREATE TABLE agenda.token_gestion (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cita_id      UUID        NOT NULL REFERENCES agenda.cita(id) ON DELETE CASCADE,
    token        TEXT        NOT NULL UNIQUE,   -- aleatorio, no enumerable
    expira_en    TIMESTAMPTZ NOT NULL,
    revocado_en  TIMESTAMPTZ
);
CREATE INDEX idx_token_cita ON agenda.token_gestion(cita_id);


-- ---------------------------------------------------------------------
-- OUTBOX · RNF-04
-- El evento se escribe en la MISMA transacción que la cita.
-- O se guardan las dos, o ninguna. Un relay periódico publica lo pendiente.
-- ---------------------------------------------------------------------
CREATE TABLE agenda.outbox (
    id               BIGSERIAL PRIMARY KEY,
    negocio_id       UUID        NOT NULL,
    tipo_evento      TEXT        NOT NULL,   -- citas.reservadas | citas.canceladas | citas.estado
    clave_particion  TEXT        NOT NULL,   -- profesional_id, garantiza el orden por profesional
    payload          JSONB       NOT NULL,
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_en       TIMESTAMPTZ,            -- NULL mientras esté pendiente
    intentos         INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_pendientes ON agenda.outbox(creado_en) WHERE enviado_en IS NULL;


-- =====================================================================
-- ESQUEMA NOTIFICACIONES · tablas derivadas
-- =====================================================================

CREATE TABLE notificaciones.programacion (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id    UUID        NOT NULL,
    cita_id       UUID        NOT NULL,
    tipo          TEXT        NOT NULL,   -- CONFIRMACION | RECORDATORIO_24H | CANCELACION
    enviar_en     TIMESTAMPTZ NOT NULL,
    canal         TEXT        NOT NULL,
    destinatario  TEXT        NOT NULL,
    cuerpo        TEXT        NOT NULL,
    estado        TEXT        NOT NULL DEFAULT 'PENDIENTE',
    intentos      INTEGER     NOT NULL DEFAULT 0,
    enviado_en    TIMESTAMPTZ,
    CONSTRAINT prog_estado_valido
        CHECK (estado IN ('PENDIENTE','ENVIADO','FALLIDO','CANCELADO'))
);
-- RNF-08: al reiniciar, el servicio recupera las pendientes por este índice
CREATE INDEX idx_prog_pendientes ON notificaciones.programacion(enviar_en)
    WHERE estado = 'PENDIENTE';
-- No programar dos veces el mismo aviso para la misma cita
CREATE UNIQUE INDEX idx_prog_unica ON notificaciones.programacion(cita_id, tipo);

-- Deduplicación de eventos: Kafka entrega "al menos una vez"
CREATE TABLE notificaciones.evento_procesado (
    evento_id     UUID PRIMARY KEY,
    procesado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =====================================================================
-- ESQUEMA ANALITICA · proyecciones de lectura
-- =====================================================================

CREATE TABLE analitica.metrica_diaria (
    negocio_id      UUID     NOT NULL,
    fecha           DATE     NOT NULL,
    profesional_id  UUID     NOT NULL,
    servicio_id     UUID     NOT NULL,
    reservadas      INTEGER  NOT NULL DEFAULT 0,
    canceladas      INTEGER  NOT NULL DEFAULT 0,
    atendidas       INTEGER  NOT NULL DEFAULT 0,
    no_asistio      INTEGER  NOT NULL DEFAULT 0,
    minutos_ocupados INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (negocio_id, fecha, profesional_id, servicio_id)
);

CREATE TABLE analitica.evento_procesado (
    evento_id     UUID PRIMARY KEY,
    procesado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =====================================================================
-- DATOS DE PRUEBA · una barbería de Neiva para poder trabajar desde el día 1
-- =====================================================================

INSERT INTO agenda.negocio (id, nombre, slug_publico, zona_horaria)
VALUES ('11111111-1111-1111-1111-111111111111',
        'Barbería El Corte', 'barberia-el-corte', 'America/Bogota');

INSERT INTO agenda.servicio (id, negocio_id, nombre, duracion_min, precio) VALUES
 ('22222222-2222-2222-2222-222222222222','11111111-1111-1111-1111-111111111111','Corte de cabello',60,25000),
 ('22222222-2222-2222-2222-222222222223','11111111-1111-1111-1111-111111111111','Barba',30,15000);

INSERT INTO agenda.profesional (id, negocio_id, nombre) VALUES
 ('33333333-3333-3333-3333-333333333333','11111111-1111-1111-1111-111111111111','Laura'),
 ('33333333-3333-3333-3333-333333333334','11111111-1111-1111-1111-111111111111','Andrés');

INSERT INTO agenda.servicio_profesional (servicio_id, profesional_id) VALUES
 ('22222222-2222-2222-2222-222222222222','33333333-3333-3333-3333-333333333333'),
 ('22222222-2222-2222-2222-222222222223','33333333-3333-3333-3333-333333333333'),
 ('22222222-2222-2222-2222-222222222222','33333333-3333-3333-3333-333333333334');

-- Lunes a sábado, 8:00 a 18:00 (hora local de Colombia)
INSERT INTO agenda.horario_atencion (negocio_id, profesional_id, dia_semana, hora_inicio, hora_fin)
SELECT '11111111-1111-1111-1111-111111111111', p.id, d, '08:00', '18:00'
FROM agenda.profesional p, generate_series(1,6) AS d
WHERE p.negocio_id = '11111111-1111-1111-1111-111111111111';
