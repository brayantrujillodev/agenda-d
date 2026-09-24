# analitica-service

Consumidor Kafka de AGENDA-D. Escucha `citas.reservadas`, `citas.canceladas`
y `citas.estado`, deduplica por `eventoId` contra
`analitica.evento_procesado` y actualiza `analitica.metrica_diaria` con
upserts atómicos. Expone `GET /v1/metricas`, que `gateway-graphql` resuelve
para `panelRecepcion.metricasDelMes` y para la query `metricas`.

**Alcance de esta versión (docs/TAREAS.md #19, adelantado de Fase 3):**

- [x] Consume los tres tópicos, deduplica igual que `notificaciones-service`
- [x] `GET /v1/metricas` (`X-Negocio-Id`, `desde`, `hasta`)
- [x] `gateway-graphql` ya no usa el stub fijo de métricas
- [ ] `topServicios` — devuelve vacío: la tabla solo guarda `servicioId`, no
      el nombre del servicio (requiere leer `agenda.servicio`, otro esquema)
- [ ] `ocupacion` real contra `agenda.horario_atencion` — hoy usa una
      jornada configurable (ver abajo), no el horario real de cada
      profesional

## Arranque

Necesita Postgres y Kafka arriba (perfil `infra`):

```bash
docker compose --profile infra up -d
```

Luego, localmente:

```bash
cd analitica-service
mvn spring-boot:run
```

Por defecto se conecta a `localhost:5432` y `localhost:29092`, los mismos
puertos que expone `docker-compose.yml` para el equipo.
`/actuator/health` responde en `http://localhost:8083/actuator/health`.

## Probar de punta a punta

```bash
# 1. Reservar una cita real contra agenda-service (puerto 8081)
# 2. Marcarla ATENDIDA: PATCH /v1/citas/{id}/estado
# 3. Consultar las métricas del día:
curl -H "X-Negocio-Id: 11111111-1111-1111-1111-111111111111" \
  "http://localhost:8083/v1/metricas?desde=2026-09-24&hasta=2026-09-24"
```

## Por qué `ocupacion` es una estimación

Calcularla de verdad —minutos ocupados sobre minutos realmente disponibles—
requiere el horario de atención de cada profesional
(`agenda.horario_atencion`), que vive en el esquema de `agenda-service`.
`analitica.*` no tiene llaves foráneas a otros esquemas por diseño (cada
servicio es dueño de sus datos, ver `CLAUDE.md`), así que en vez de eso se
multiplica `profesionales-día con actividad × minutos_habiles_dia`, una
constante configurable (`AGENDAD_MINUTOS_HABILES_DIA`, 600 por defecto =
jornada 08:00–18:00). Es una simplificación honesta, no una cifra
inventada: se calcula sobre actividad real registrada, solo que asume una
jornada fija en vez de leer el horario exacto. Integrarlo de verdad
requeriría o una llamada REST a `agenda-service`, o que el contrato de
eventos empezara a llevar el horario — ninguna de las dos existía cuando
se escribió esto.

## Por qué no hay migración Flyway aquí

Las tablas de `analitica.*` ya las crea `db/V1__esquema_inicial.sql` al
arrancar Postgres. `agenda-service` es el único dueño de la migración del
esquema completo (ver CLAUDE.md) — mismo patrón que `notificaciones-service`.
