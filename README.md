# AGENDA-D

Plataforma distribuida de agendamiento de citas para negocios de servicios.
Proyecto de aula · Programación Avanzada · SOF-G2-NOC · Equipo 2

**Brayan Trujillo · Andrés Bonilla · Johan Camacho · Luis Moncada**

---

## Qué hace

Cualquier negocio de servicios (barbería, consultorio, taller, veterinaria)
publica un enlace o un QR. El cliente entra, ve los cupos libres, reserva sin
crear cuenta y recibe un recordatorio 24 horas antes. El negocio ve su agenda
y sus indicadores de ocupación e inasistencia.

**Fuera de alcance por acuerdo con el docente:** autenticación de usuarios.
El sistema opera como si el usuario ya estuviera autenticado.

---

## Estado actual

**Fase 2 en curso.** El circuito REST → persistencia → outbox → Kafka →
consumidor está cerrado y probado de punta a punta con Docker real; falta el
gateway GraphQL para completar la Fase 2.

| Componente | Estado |
|---|---|
| PostgreSQL con el esquema y datos de prueba | ✅ funciona |
| Kafka en KRaft y su consola | ✅ funciona |
| Contratos OpenAPI, GraphQL y de eventos | ✅ en el repo (`docs/`) |
| Migración con el `EXCLUDE` (`db/V1__esquema_inicial.sql`) | ✅ en el repo |
| `agenda-service` | ✅ completo — servicios, disponibilidad, reserva con outbox, gestión por token ([#6](../../pull/6), [#7](../../pull/7), [#10](../../pull/10), [#11](../../pull/11)). Falta `GET /v1/agenda/{profesionalId}` (ver [issue #1](../../issues/1)) |
| `notificaciones-service` | ✅ consume `citas.reservadas`/`citas.canceladas`, deduplica por `eventoId` ([#9](../../pull/9)) |
| `gateway-graphql` | 🔶 en curso — [PR #14](../../pull/14) |
| `analitica-service` | ⬜ por construir |
| PWA de reserva | ✅ flujo completo contra `agenda-service` real, instalable ([#12](../../pull/12), [#18](../../pull/18), [#19](../../pull/19)) |

> **Avance · 2026-09-14.** Con #6/#7/#10/#11 mergeados, `agenda-service`
> cubre todo lo público y de gestión. Se encontró y arregló un bloqueo real:
> sin CORS ([#17](../../pull/17)) el navegador rechazaba toda llamada de la
> PWA aunque curl funcionara bien. Verificado en Chrome real (no mock, no
> curl) contra Postgres + Kafka + `agenda-service` reales: reservar,
> idempotencia, el `409` con alternativas, gestión y cancelación — 0 errores
> de consola, outbox publicando los dos eventos. Sigue sin existir una vista
> administrativa de reservas: sin `GET /v1/agenda/{profesionalId}`, el
> `panelRecepcion` del gateway cae a datos de ejemplo (ver issue #1).

> **Avance · 2026-08-28.** Repo publicado con `.gitignore`, licencia MIT,
> contratos, migración y `docker-compose`. `agenda-service` arrancado en dos
> PRs apilados: #6 (esqueleto Spring Boot 3.3 que arranca y aplica la
> migración) y #7 (entidades del esquema `agenda` + `GET /v1/publico/{slug}/servicios`
> y `GET /v1/publico/{slug}/disponibilidad`). Ningún PR mergeado todavía y el
> `docker compose --profile core` aún no se ha verificado de punta a punta.
> Reparto activo: un servicio por persona (ver [`docs/EQUIPO.md`](docs/EQUIPO.md)).

---

## Arranque

```bash
git clone <url> && cd agenda-d

# Lo único que funciona hoy: base de datos + bus de eventos
docker compose --profile infra up -d
```

Con eso arriba puedes conectarte a la base y ver el esquema ya creado con sus
datos de prueba. La migración se aplica sola la primera vez.

```bash
# Ver las tablas
docker exec -it agd-postgres psql -U agendad -d agendad -c "\dt agenda.*"

# Comprobar que el EXCLUDE funciona: la segunda debe fallar
docker exec -it agd-postgres psql -U agendad -d agendad -c "
INSERT INTO agenda.cita (negocio_id, servicio_id, profesional_id, inicio, fin, cliente_nombre, cliente_celular)
VALUES ('11111111-1111-1111-1111-111111111111','22222222-2222-2222-2222-222222222222',
        '33333333-3333-3333-3333-333333333333','2026-09-01 15:00:00+00','2026-09-01 16:00:00+00','A','3001111111');"

# Ver los tópicos de Kafka
docker exec agd-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

**Perfiles.** `infra` levanta base de datos, Kafka y la consola. `core` añade
`agenda-service` (ya funciona) y `full` añade el resto — `gateway-graphql` y
`analitica-service` empezarán a funcionar a medida que existan sus proyectos.

| Servicio | URL | Estado |
|---|---|---|
| PostgreSQL | localhost:5432 · `agendad`/`agendad` | activo |
| Kafka desde el equipo | localhost:29092 | activo |
| Consola de Kafka | <http://localhost:8090> | activo |
| agenda-service | <http://localhost:8081> | activo |
| gateway GraphQL | <http://localhost:8080/graphiql> | por construir |

> Alguien del equipo tiene 8 GB de RAM. Usa el perfil más pequeño que te sirva.

Para bajar todo y empezar de cero:

```bash
docker compose --profile full down -v
```

---

## Estructura

```text
agenda-d/
├─ docker-compose.yml
├─ agenda-service/          Dominio: citas, cupos, configuración. Productor.
├─ notificaciones-service/  Consumidor. Confirmaciones y recordatorios.
├─ analitica-service/       Consumidor. Ocupación e inasistencia.
├─ gateway-graphql/         Compone la pantalla del panel en una consulta.
├─ web/                     2 pantallas: reserva pública y panel de recepción.
└─ docs/
   ├─ openapi/              Contratos REST — SE ACUERDAN ANTES DE PROGRAMAR
   └─ eventos/              Contrato de los eventos de Kafka
```

Cada servicio tiene su propio `pom.xml`. No hay proyecto padre: son
independientes y se construyen por separado.

---

## Las dos decisiones que hay que entender

Cualquiera del equipo puede ser elegido para sustentar. Estas dos cosas
**tienen que saberlas los cuatro**.

### 1. El solapamiento lo impide la base de datos, no el código

```sql
ALTER TABLE agenda.cita ADD CONSTRAINT cita_sin_solape
    EXCLUDE USING gist (
        profesional_id WITH =,
        tstzrange(inicio, fin, '[)') WITH &&
    ) WHERE (estado <> 'CANCELADA');
```

Validarlo en Java no sirve: entre el `SELECT` que pregunta si está libre y el
`INSERT` que escribe, cabe otra petición. Y un `UNIQUE(profesional_id, inicio)`
tampoco basta: una cita de 60 minutos a las 10:00 y otra a las 10:30 tienen
inicios distintos y aun así se pisan. Por eso comparamos **rangos** con `&&`,
no instantes con `=`.

Cuando el motor rechaza la escritura, el servicio traduce ese error en un
`409` con los cupos más cercanos. El usuario nunca ve un error técnico.

### 2. El evento se escribe en la misma transacción que la cita (outbox)

Guardar la cita y publicar en Kafka son dos operaciones que pueden fallar por
separado. Si publicáramos directo, un cliente podría quedar con una cita
confirmada de la que la cocina —perdón, el profesional— nunca se entera.

Por eso el `INSERT` en `cita` y el `INSERT` en `outbox` van en la misma
transacción: o las dos, o ninguna. Un relay periódico lee lo pendiente, lo
publica y lo marca. Se puede apagar Kafka, seguir reservando, y al reconectar
se publica todo.

---

## Plan de trabajo

**El docente aprobó la arquitectura** y pidió completar primero el *flujo
mínimo*: REST → persistencia → Kafka → consumidor → GraphQL. Las decisiones
de robustez (DLQ, Testcontainers, analítica, recordatorios persistentes) son
correctas pero van después. El plan está ordenado según eso.

Capacidad real: ~20 horas semanales entre los cuatro.

### Fase 1 · Semanas 1–5 · Cimientos

- [x] Contratos OpenAPI, GraphQL y de eventos en el repo (`docs/`)
- [x] Migración con el `EXCLUDE` en el repo (`db/V1__esquema_inicial.sql`)
- [ ] Migración Flyway con el `EXCLUDE` corriendo desde `agenda-service` → PR [#6](../../pull/6)
- [ ] `docker compose --profile core up` levanta agenda-service con `/actuator/health` → PR [#6](../../pull/6)
- [ ] `agenda-service` consulta cupos → PR [#7](../../pull/7) · reserva contra la BD real → rama 3 (pendiente)

### Fase 2 · Semanas 6–10 · Flujo mínimo completo

**El hito del semestre.** Al cerrarlo, el sistema recorre el circuito entero.

- [ ] Outbox publicando `citas.reservadas`
- [ ] `notificaciones-service` consumiendo y guardando el mensaje
- [ ] `gateway-graphql` respondiendo la consulta `panelRecepcion`
- [ ] PWA pública de reserva contra la API real

Cada pieza en su versión más simple. Lo importante es que el circuito cierre.

### Fase 3 · Semanas 11–15 · Robustez

- [ ] DLQ con reintentos y espera creciente
- [ ] Idempotencia verificada
- [ ] Testcontainers con la prueba de concurrencia (100 hilos)
- [ ] Recordatorio de 24 h con recuperación tras reinicio
- [ ] `analitica-service` y panel de recepción
- [ ] Prueba de separación entre negocios

### Semana 15

Ensayar la sustentación. Los cuatro, todo el flujo.

---

## Reglas del equipo

1. **Rama por tarea, PR revisado por alguien de la otra pareja.**
   Así todos ven todo el código y cualquiera puede sustentar.
   `feature/reserva-cita`, `fix/zona-horaria`

2. **Nadie toca la Fase 3 hasta que el circuito de la Fase 2 cierre de punta a punta.**
   Es la indicación explícita del docente.

3. **Si vamos atrasados, se recorta desde la Fase 3 hacia atrás.**
   Nunca se recorta el `EXCLUDE` ni el circuito completo.

4. **El contrato no se cambia en silencio.** Aviso en el chat + PR.

5. **Dos horas fijas semanales de trabajo conjunto**, aunque sea virtual.
   No para programar: para integrar y para que todos entiendan lo del resto.
   Tres servicios que se hablan por eventos no se integran por chat.

---

## Convenciones

- **Todo instante se guarda en UTC** (`timestamptz`). Los horarios de atención
  se definen en hora local y se convierten al calcular disponibilidad.
  La zona del negocio está en `negocio.zona_horaria`.
- **Mensajes de error en español**, listos para mostrar al usuario.
  Nunca un stacktrace ni un código técnico.
- **El contexto de negocio** llega por la cabecera `X-Negocio-Id` en las rutas
  administrativas, y por el `slug` de la URL en las rutas públicas.
- Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Kafka 3.7 (KRaft)

---
