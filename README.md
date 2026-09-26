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

**Fase 2 cerrada.** El circuito REST → persistencia → outbox → Kafka →
`notificaciones-service` → gateway GraphQL (`panelRecepcion`) está cerrado y
**verificado a mano contra Docker real**: los seis contenedores arriba,
una cita reservada por `POST /v1/publico/{slug}/citas` y confirmada tal
cual en la respuesta de `panelRecepcion` — no son datos de ejemplo.
Detalle de tareas y dueños en [`docs/TAREAS.md`](docs/TAREAS.md).

| Componente | Estado |
|---|---|
| PostgreSQL con el esquema y datos de prueba | ✅ funciona |
| Kafka en KRaft y su consola | ✅ funciona |
| Contratos OpenAPI, GraphQL y de eventos | ✅ en el repo (`docs/`) |
| Migración con el `EXCLUDE` (`db/V1__esquema_inicial.sql`) | ✅ en el repo |
| `agenda-service` | ✅ completo — servicios, disponibilidad, reserva con outbox, gestión por token, agenda del profesional y registro de asistencia ([#6](../../pull/6), [#7](../../pull/7), [#10](../../pull/10), [#11](../../pull/11), [#20](../../pull/20)) |
| `notificaciones-service` | ✅ consume `citas.reservadas`/`citas.canceladas`, deduplica por `eventoId` ([#9](../../pull/9)) |
| `gateway-graphql` | ✅ `panelRecepcion` contra `agenda-service` real, verificado con una reserva real de punta a punta ([#14](../../pull/14), fix de URI en [#23](../../pull/23)) |
| `analitica-service` | ✅ consume los tres tópicos, actualiza `metrica_diaria`, `GET /v1/metricas` — adelantado de Fase 3, ver nota |
| PWA de reserva | ✅ flujo completo contra `agenda-service` real, instalable ([#12](../../pull/12), [#17](../../pull/17), [#18](../../pull/18), [#19](../../pull/19)) |

**Release publicado:** [`v1.0.2`](../../releases/tag/v1.0.2) — imágenes de
`agenda-service`, `notificaciones-service` y `gateway-graphql` en GHCR.
(`v1.0.1` quedó publicado con un bug real en `gateway-graphql` que hacía que
`panelRecepcion` siempre devolviera datos de ejemplo aunque el backend
respondiera bien — no usar esa versión, se deja el tag por trazabilidad.
`analitica-service` es posterior a este tag, todavía no tiene su propia
imagen publicada.)

> **Avance · 2026-09-24.** Se construyó `analitica-service` completo,
> adelantado de Fase 3 (docs/TAREAS.md #19) porque Luis no está activo y el
> resto del equipo necesitaba la pieza para seguir probando el panel
> completo. Consume `citas.reservadas`, `citas.canceladas` y `citas.estado`,
> deduplica por `eventoId` y actualiza `analitica.metrica_diaria` con
> upserts atómicos. `gateway-graphql` ya no usa el stub fijo de métricas:
> `panelRecepcion.metricasDelMes` resuelve contra datos reales, verificado
> a mano reservando una cita, marcándola `ATENDIDA` y viendo las cifras
> correctas tanto en `GET /v1/metricas` como en GraphQL. Dos
> simplificaciones quedan documentadas en el contrato: `ocupacion` usa una
> jornada configurable en vez de leer el horario real (cruzar esquemas
> entre servicios no es el patrón del proyecto) y `topServicios` devuelve
> vacío (la tabla no guarda el nombre del servicio, solo su id).
>
> También se agregaron los contratos OpenAPI de `notificaciones-service` y
> `gateway-graphql` (documentan su superficie HTTP real — ninguno tiene API
> de negocio REST) y se corrigió un bug real en `docker-compose.yml`: dos
> servicios mapeaban el puerto equivocado.
>
> Se confirmó con el docente que Docker no es obligatorio para correr los
> microservicios: los tres ya se probaron corriendo directo con
> `mvn spring-boot:run` contra Postgres/Kafka en Docker (`--profile infra`),
> sin empaquetarlos en contenedor — ver "Arranque sin Docker" más abajo.
>
> Sigue pendiente para Fase 3: DLQ y reintentos, y el recordatorio de 24 h
> con recuperación tras reinicio.
>
> **Avance · 2026-09-25.** Se cerró la única pieza de Fase 3 que
> `CLAUDE.md` marca como obligatoria: la prueba de concurrencia con
> Testcontainers (`agenda-service/.../ReservaConcurrenciaTest`) — 100
> hilos reales contra un Postgres real intentando reservar el mismo cupo
> al mismo tiempo, 1 éxito y 99 rechazados por `cita_sin_solape`. La
> prueba encontró un bug real: bajo esa contención extrema, Postgres a
> veces resuelve el choque como **deadlock** en vez de la violación limpia
> de la restricción, algo que el código no manejaba. Se corrigió con un
> reintento acotado (hasta 8 intentos) con espera aleatoria entre cada uno
> en `ReservaService` — sin la espera, los mismos hilos volvían a chocar
> en el mismo instante y encadenaban deadlock tras deadlock. Confirmado en
> verde en el CI (Linux). No se pudo correr en la máquina de desarrollo
> (Windows): un problema de compatibilidad entre Testcontainers y el
> transporte por named pipe de Docker Desktop, ajeno al código — `docker
> info`/`docker ps` funcionan bien por el mismo pipe.
> Reparto activo: un servicio por persona (ver [`docs/EQUIPO.md`](docs/EQUIPO.md)).

---

## Arranque

```bash
git clone <url> && cd agenda-d

# Base de datos + bus de eventos + agenda-service
docker compose --profile core up -d

# Todo, incluidos gateway-graphql, notificaciones-service y analitica-service
docker compose --profile full up -d
```

Con eso arriba puedes conectarte a la base y ver el esquema ya creado con sus
datos de prueba. La migración se aplica sola la primera vez.

### Arranque sin Docker (los microservicios, no la base de datos)

El docente confirmó que Docker no es obligatorio para correr los
microservicios — solo hace falta para Postgres y Kafka, que sí conviene
dejar en contenedor. Con Java 21 y Maven instalados (o, como en esta
máquina, ya empaquetados con IntelliJ IDEA en
`plugins/maven/lib/maven3`), cada servicio corre directo:

```bash
docker compose --profile infra up -d   # solo Postgres + Kafka + consola

cd agenda-service          && mvn spring-boot:run   # puerto 8081
cd notificaciones-service  && mvn spring-boot:run   # puerto 8082
cd gateway-graphql         && mvn spring-boot:run   # puerto 8080
cd analitica-service       && mvn spring-boot:run   # puerto 8083
```

Los `application.yml` de los cuatro ya apuntan a `localhost` por defecto
(`SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`), así que no hace
falta ninguna variable de entorno extra para esta forma de correrlos.

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
`agenda-service`. `full` levanta también `notificaciones-service`,
`gateway-graphql` y `analitica-service` — los cuatro tienen ya su `pom.xml`
y `Dockerfile`.

| Servicio | URL | Estado |
|---|---|---|
| PostgreSQL | localhost:5432 · `agendad`/`agendad` | activo |
| Kafka desde el equipo | localhost:29092 | activo |
| Consola de Kafka | <http://localhost:8090> | activo |
| agenda-service | <http://localhost:8081> | activo |
| gateway GraphQL | <http://localhost:8080/graphiql> | activo |
| analitica-service | <http://localhost:8083/v1/metricas> | activo |
| notificaciones-service | localhost:8082 (sin UI, solo `/actuator/health`) | activo |

> Alguien del equipo tiene 8 GB de RAM. Usa el perfil más pequeño que te sirva.

Para bajar todo y empezar de cero:

```bash
docker compose --profile full down -v
```

---

## Despliegue

GitHub Actions publica dos cosas por separado — ninguna necesita más cuenta
que GitHub:

- **`web/` → GitHub Pages**, automático en cada push a `main` que toque esa
  carpeta ([`.github/workflows/pages.yml`](.github/workflows/pages.yml)), ya
  activo en <https://brayantrujillodev.github.io/agenda-d/>.
  `web/config.js` tiene hoy `useMock: false` y `apiBase: 'http://localhost:8081'`,
  así que esa URL pública solo reserva contra un `agenda-service` que corra
  en la máquina de quien la abre — no es un backend compartido. Para volver
  a la demo sin backend, pon `useMock: true`.
- **Imágenes de los cuatro servicios → GHCR**, al empujar un tag `vX.Y.Z`
  ([`.github/workflows/release.yml`](.github/workflows/release.yml)). Crea
  además el GitHub Release con las notas.

El despliegue del backend en un servidor (`docker-compose.prod.yml`, ya con
Postgres real) queda listo pero apagado hasta tener dónde correrlo: se activa
solo con la variable de repo `DEPLOY_ENABLED=true` y los secrets
`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` (*Settings → Secrets and
variables → Actions*). Sin esos tres secrets el job de despliegue se salta
solo, no falla.

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

### Fase 1 · Semanas 1–5 · Cimientos — ✅ cerrada

- [x] Contratos OpenAPI, GraphQL y de eventos en el repo (`docs/`)
- [x] Migración con el `EXCLUDE` en el repo (`db/V1__esquema_inicial.sql`)
- [x] Migración Flyway con el `EXCLUDE` corriendo desde `agenda-service`
- [x] `docker compose --profile core up` levanta agenda-service con `/actuator/health`
- [x] `agenda-service` consulta cupos y reserva contra la BD real

### Fase 2 · Semanas 6–10 · Flujo mínimo completo — ✅ cerrada

**El hito del semestre.** El sistema recorre el circuito entero.

- [x] Outbox publicando `citas.reservadas`
- [x] `notificaciones-service` consumiendo y guardando el mensaje
- [x] `gateway-graphql` respondiendo la consulta `panelRecepcion` con datos
      reales de `agenda-service` — verificado a mano contra Docker real
      ([#14](../../pull/14), fix de bug real en [#23](../../pull/23))
- [x] PWA pública de reserva contra la API real

Cada pieza en su versión más simple. Lo importante es que el circuito cierre.
Detalle y dueño de cada tarea pendiente: [`docs/TAREAS.md`](docs/TAREAS.md).

### Fase 3 · Semanas 11–15 · Robustez

- [ ] DLQ con reintentos y espera creciente
- [ ] Idempotencia verificada
- [x] Testcontainers con la prueba de concurrencia (100 hilos) — encontró y
      corrigió un deadlock real bajo contención extrema
- [ ] Recordatorio de 24 h con recuperación tras reinicio
- [x] `analitica-service` — adelantado; falta el panel de recepción en la PWA
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
