# AGENDA-D

Plataforma distribuida de agendamiento de citas para negocios de servicios
(barberías, consultorios, talleres, veterinarias). Proyecto de aula,
Programación Avanzada · SOF-G2-NOC · Equipo 2.

Un negocio publica un enlace o QR. El cliente ve cupos libres, reserva sin
crear cuenta y recibe recordatorio 24 h antes. El negocio ve su agenda y sus
indicadores de ocupación e inasistencia.

## Stack

Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Kafka 3.7 (KRaft) · Spring for
GraphQL · Docker Compose · PWA en HTML/CSS/JS sin framework.

## Estructura

```
agenda-d/
├─ docker-compose.yml
├─ agenda-service/          Dominio: citas, cupos, config. Productor Kafka.
├─ notificaciones-service/  Consumidor. Confirmaciones y recordatorios.
├─ analitica-service/       Consumidor. Ocupación e inasistencia.
├─ gateway-graphql/         Compone la pantalla del panel en una consulta.
├─ web/                     PWA: reserva pública + panel de recepción.
└─ docs/
   ├─ openapi/agenda-service.yaml    ← contrato, léelo antes de tocar la API
   └─ eventos/CONTRATO-EVENTOS.md    ← contrato de eventos
```

Cada servicio tiene su propio `pom.xml`. No hay proyecto padre.

---

# REGLAS INVIOLABLES

Estas no son preferencias de estilo. Romperlas rompe el proyecto.

## 1. El solapamiento lo impide la base de datos, NUNCA el código

```sql
ALTER TABLE agenda.cita ADD CONSTRAINT cita_sin_solape
    EXCLUDE USING gist (
        profesional_id WITH =,
        tstzrange(inicio, fin, '[)') WITH &&
    ) WHERE (estado <> 'CANCELADA');
```

**No propongas** validar disponibilidad con un `SELECT` previo al `INSERT`,
ni con `@Transactional` + consulta, ni con bloqueo pesimista, ni con
`synchronized`. Entre el `SELECT` y el `INSERT` cabe otra petición.

**No propongas** `UNIQUE(profesional_id, inicio)`: una cita de 60 min a las
10:00 y otra a las 10:30 tienen inicios distintos y aun así se pisan.

La forma correcta de reservar es: intentar el `INSERT`, capturar la violación
de restricción, y traducirla a `409` con los cupos más cercanos.

## 2. El evento va en la misma transacción que la cita (patrón outbox)

`INSERT` en `cita` + `INSERT` en `agenda.outbox`, misma transacción. Un relay
periódico (`@Scheduled`) lee lo pendiente, publica en Kafka y lo marca.

**Nunca** llames a `kafkaTemplate.send()` dentro del caso de uso de reserva.

## 3. Todo instante en UTC

Columnas `timestamptz`. Los horarios de atención se definen en hora local
(`TIME` + `dia_semana`) y se resuelven a UTC al calcular disponibilidad usando
`negocio.zona_horaria` (por defecto `America/Bogota`).

Usa `Instant` y `ZonedDateTime`. **Nunca** `LocalDateTime` para instantes.
**Nunca** `new Date()`.

## 4. No hay autenticación

Fuera de alcance por acuerdo con el docente. El contexto de negocio llega así:

- Ruta pública: del `slug` en la URL → `/api/v1/publico/{slug}/...`
- Ruta administrativa: de la cabecera `X-Negocio-Id`

Un interceptor lee eso y lo deja en un contexto de petición. **Todas** las
consultas del dominio filtran por `negocio_id`.

**No agregues** Spring Security, JWT, login, registro de usuarios, roles ni
sesiones. Si algo parece necesitarlo, pregunta primero.

## 5. Los mensajes al usuario van en español y son accionables

`"Ese cupo se acaba de ocupar. Estos son los más cercanos."`
Nunca un stacktrace, nunca un código técnico, nunca inglés.

---

# CONVENCIONES

- **Contratos primero.** Antes de tocar un endpoint, lee
  `docs/openapi/agenda-service.yaml`. Si hay que cambiarlo, cámbialo ahí
  primero y avísalo.
- **Idempotencia:** `POST /citas` exige cabecera `Idempotency-Key`. Reenviar
  la misma devuelve la cita original, no crea otra.
- **Consumidores Kafka:** deduplican por `eventoId` contra la tabla
  `evento_procesado` antes de procesar. Kafka entrega *al menos una vez*.
- **Clave de partición:** siempre `profesionalId`. Garantiza el orden.
- **Migraciones:** Flyway, en `src/main/resources/db/migration`. Nunca
  `ddl-auto: update`.
- **Tests:** Testcontainers con Postgres y Kafka reales. La prueba de
  concurrencia (100 hilos sobre el mismo cupo, 1 éxito y 99 conflictos) es
  obligatoria y no se borra.
- **Respuestas de error:** `{ "codigo": "...", "mensaje": "..." }`.

## PWA

Instalable, con `manifest.json` y service worker que cachea la interfaz.
**La reserva exige conexión.** No implementes cola de reservas offline: dos
personas reservando el mismo cupo sin red generan un conflicto irresoluble
cuando sincronizan.

HTML, CSS y JS nativo. Sin React, sin Vue, sin build. El backend es lo que
se evalúa.

## Comandos

```bash
docker compose --profile infra up -d   # postgres + kafka + consola · FUNCIONA HOY
docker compose --profile core up -d    # + agenda-service (cuando exista el proyecto)
docker compose --profile full up -d    # todo (cuando existan los cuatro)
docker compose --profile full down -v  # empezar de cero
```

**Los proyectos Java todavía no existen.** Solo `infra` levanta sin errores.
A medida que crees cada servicio, actualiza la tabla de estado del README:
el docente ya observó que el README prometía un arranque que no funcionaba.

Alguien del equipo tiene 8 GB de RAM: **usa `core` por defecto**.

| | |
|---|---|
| API pública | http://localhost:8081 |
| Swagger | http://localhost:8081/swagger-ui.html |
| GraphQL | http://localhost:8080/graphiql |
| Consola Kafka | http://localhost:8090 |
| Postgres | localhost:5432 · `agendad`/`agendad` |

---

# ORDEN DE TRABAJO

El docente aprobó la arquitectura y pidió expresamente **completar primero el
flujo mínimo**: REST → persistencia → Kafka → consumidor → GraphQL. Las
decisiones de robustez son correctas pero no deben retrasar ese circuito.

Equipo de 4 personas, ~20 horas semanales entre todas.

- **Fase 1** — Contratos, migración Flyway con el EXCLUDE, Compose con
  `/actuator/health`. Al cierre: agenda-service reserva contra la BD real.
- **Fase 2** — *(actual)* **Flujo mínimo de extremo a extremo:** REST →
  persistencia → outbox → Kafka → notificaciones-service consumiendo →
  gateway GraphQL respondiendo `panelRecepcion`. Más la PWA pública.
  Cada pieza en su versión más simple, pero el circuito completo.
- **Fase 3** — Robustez: DLQ y reintentos, idempotencia verificada,
  Testcontainers con la prueba de concurrencia, recordatorio de 24 h con
  recuperación tras reinicio, analitica-service, panel de recepción,
  prueba de separación entre negocios.

**Regla de prioridad:** si algo de la Fase 3 amenaza con retrasar el cierre
del circuito de la Fase 2, se aplaza. La excepción es el `EXCLUDE`, que ya
está en la migración y cuesta cero.

No adelantes fases. Si te piden algo de la Fase 3 y el circuito de la Fase 2
no está cerrado, dilo.

---

# QUÉ NO HACER

- Validar solapamiento en Java.
- Publicar a Kafka fuera del outbox.
- `LocalDateTime` para instantes.
- Agregar autenticación.
- Agregar frameworks de frontend.
- Agregar servicios nuevos. Son tres, y ya se justificó por qué.
- Adelantar DLQ, Testcontainers o analítica antes de cerrar el circuito completo.
- Avro o Schema Registry. Es JSON con campo `version`.
- `ddl-auto: update`.
