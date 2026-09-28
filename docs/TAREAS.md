# Tareas · AGENDA-D

Cada bloque es un **issue de GitHub**. Copia título y cuerpo, y asígnalo.
El campo *Toca* indica qué archivos modifica: dos tareas de la misma fase
nunca tocan los mismos archivos, para que nadie se pise.

> **Orden aprobado por el docente.** La arquitectura quedó aprobada, con la
> indicación de completar primero el flujo mínimo —REST → persistencia →
> Kafka → consumidor → GraphQL— antes que las decisiones de robustez.
> Este backlog está ordenado según eso.

**Leyenda:** 🔴 bloqueante · 🟡 camino crítico · 🟢 puede esperar

---

## Estado y próximos pasos · 2026-09-28

**Fase 3 cerrada.** Las cinco tareas que quedaban (#16, #18, #20, #21, #22)
están hechas y mergeadas: reintentos + DLQ, recordatorio de 24 h con
recuperación tras reinicio, panel de recepción en la PWA, prueba de
separación entre negocios y esta actualización de documentación. Con eso el
backlog completo de las tres fases queda cerrado.

Luis y Johan siguieron sin contribuir activamente durante el cierre; el
resto del trabajo de Fase 3 lo terminó Brayan, sin repartir por persona
como en el resto del backlog (ver commits/PRs #31–#34).

**Un hallazgo real encadenado:** la DLQ de #16 fue quien encontró el bug
del recordatorio (#18) — la primera reserva real después de agregarlo
cayó en `citas.dlq` con `not-null property references a null value: Programacion.canal`,
porque antes del recordatorio toda fila de `programacion` nacía y se
enviaba en la misma transacción (el canal siempre se fijaba antes del
`INSERT`). El recordatorio es la primera fila que de verdad queda pendiente
sin canal, y el `NOT NULL` original de `V1` nunca se había probado contra
ese caso. Se corrigió con `V3__canal_opcional_mientras_pendiente.sql` y se
confirmó reservando de nuevo: la misma reserva se procesa sin caer al DLQ.
Sin la DLQ real (no un mock), este bug se habría notado en producción, no
en desarrollo.

**La prueba de concurrencia obligatoria (#17) sigue cerrada** y de paso
encontró un segundo hallazgo en esta ronda: bajo la carga de CI, el cálculo
de "cupos más cercanos" que se ofrece junto al `409` también podía
deadlockear (es una lectura aparte, sin la protección con reintentos que sí
tiene el `INSERT`). Corregido devolviendo el `409` sin alternativas en vez
de un error genérico cuando eso pasa — ver #17 más abajo.

Nota histórica sin acción pendiente: la rama `feature/10-notificaciones-service`
que subía a Java 25 / Spring Boot 3.5 (contra la regla de `CLAUDE.md` de
Java 21 · Spring Boot 3.3 para todo el proyecto) terminó como PR de GitHub
cerrado sin mergear, no borrado por error — no hace falta ninguna acción
sobre ella.

---

## FASE 1 · Semanas 1–5 · Cimientos y persistencia

Al cerrar la fase, `agenda-service` reserva contra la base de datos real.

---

### ✅ #1 · Crear el repositorio y la estructura base — cerrado

**Asignado:** Brayan · **Toca:** raíz, `.github/` · **Depende de:** nada

- [x] Repo `agenda-d` en GitHub, privado, los cuatro como colaboradores
- [x] `README.md`, `CLAUDE.md`, `.gitignore` (Java + Maven + IDE)
- [x] Carpetas de los 3 servicios, el gateway y `web/`
- [ ] Rama `main` protegida: exige 1 aprobación para hacer merge — **verificar en GitHub, no se puede confirmar desde el repo local**
- [x] Plantilla de PR en `.github/pull_request_template.md`

**Aceptación:** los cuatro clonan y nadie puede hacer push directo a `main`.

---

### ✅ #2 · Docker Compose con Postgres y Kafka — cerrado

**Asignado:** Johan · **Toca:** `docker-compose.yml` · **Depende de:** #1

- [x] Perfiles `core` y `full`
- [x] `mem_limit` por contenedor (alguien tiene 8 GB)
- [x] Kafka en KRaft con listener externo en `localhost:29092`
- [x] Healthchecks en Postgres y Kafka

> Requirió un hotfix aparte (`3e13239`): `bitnami/kafka` dejó de publicarse
> en Docker Hub, se cambió a `bitnamilegacy/kafka:3.7`.

**Aceptación:** `docker compose --profile core up -d` deja ambos sanos y
`kafka-topics.sh --list` responde sin error.

> Prompt: *"Lee CLAUDE.md. Crea el docker-compose.yml con perfiles core y full.
> Postgres 16 y Kafka 3.7 en KRaft, healthchecks y límites de memoria. Los
> servicios Java aún no existen, déjalos comentados."*

---

### ✅ #3 · Migración Flyway con el esquema completo — cerrado

**Asignado:** Andrés · **Toca:** `agenda-service/src/main/resources/db/migration/`
**Depende de:** #2

- [x] `V1__esquema_inicial.sql` con los tres esquemas
- [x] Restricción `EXCLUDE` sobre `cita`
- [x] Índice único de idempotencia
- [x] Datos de prueba: un negocio, dos servicios, dos profesionales, horarios

**Aceptación:** corre limpio contra Postgres 16 y estos tres casos se cumplen:

1. Cita 10:00–11:00 con Laura → entra
2. Cita 10:30–11:30 con Laura → **rechazada** por `cita_sin_solape`
3. Cita 10:00–11:00 con Andrés → entra

> El caso 2 es el que un `UNIQUE(profesional_id, inicio)` dejaría pasar.
> El docente señaló justamente esa confusión en la primera entrega.

---

### ✅ #4 · Esqueletos de los servicios Spring Boot — cerrado

**Asignado:** Luis · **Toca:** `*/pom.xml`, `*/Dockerfile`, `*/Application.java`, `*/application.yml`
**Depende de:** #2

- [x] Java 21, Spring Boot 3.3 — los cuatro servicios
- [x] Dockerfile multi-stage por servicio
- [x] `/actuator/health` respondiendo en los cuatro
- [x] `agenda-service` con springdoc y Flyway conectados

> ⚠️ Una rama sin mergear (`feature/10-notificaciones-service`) sube
> `notificaciones-service` a Java 25 / Spring Boot 3.5. **No mergear así**:
> CLAUDE.md fija Java 21 · Spring Boot 3.3 para todo el proyecto.

**Aceptación:** `docker compose --profile full up -d --build` levanta todo y
los cuatro `/actuator/health` responden `UP`.

---

### 🟡 #5 · Revisar y aprobar los contratos

**Asignado:** los cuatro · **Toca:** `docs/openapi/`, `docs/graphql/`, `docs/eventos/`
**Depende de:** #1

- [ ] Cada uno lee el OpenAPI, el esquema GraphQL y el contrato de eventos — **confirmar en la próxima sesión semanal**
- [ ] Se discuten los cambios en la sesión semanal y se aprueban por PR

**Aceptación:** los cuatro pueden explicar qué devuelve `/disponibilidad`, qué
resuelve `panelRecepcion` y qué lleva `citas.reservadas`.

---

### ✅ #6 · Dominio y repositorios — cerrado

**Asignado:** Brayan · **Toca:** `agenda-service/.../domain/`, `.../repository/`
**Depende de:** #3, #4

- [x] Entidades JPA: Negocio, Servicio, Profesional, HorarioAtencion, Bloqueo, Cita
- [x] Repositorios Spring Data, todos filtrando por `negocioId`
- [x] `Instant` para instantes, nunca `LocalDateTime`

**Aceptación:** test de integración que guarda y lee una cita.

---

### ✅ #7 · Cálculo de disponibilidad — cerrado

**Asignado:** Brayan · **Toca:** `.../service/DisponibilidadService.java` · **Depende de:** #6

- [x] Cruza horario de atención + bloqueos + citas existentes
- [x] Genera cupos según la duración del servicio
- [x] Convierte hora local del negocio a UTC correctamente
- [x] `GET /v1/publico/{slug}/disponibilidad`

**Aceptación:** cubre día sin horario, día con bloqueo, día con cita tomada y
cambio de día por zona horaria (19:00 en Colombia es del día siguiente en UTC).

> La tarea más difícil del proyecto. Que la haga alguien con tiempo.

---

### ✅ #8 · Reservar cita — cerrado

**Asignado:** Andrés · **Toca:** `.../service/ReservaService.java`, `.../controller/CitaPublicaController.java`
**Depende de:** #6

- [x] `POST /v1/publico/{slug}/citas` con `Idempotency-Key`
- [x] Intenta el `INSERT` y captura la violación de `cita_sin_solape`
- [x] Traduce el conflicto a `409` con los cupos más cercanos
- [x] Genera el token de gestión y devuelve el enlace

**Aceptación:** el `409` llega con mensaje en español y alternativas.
**No se acepta** si valida disponibilidad con un `SELECT` antes de insertar.

---

## FASE 2 · Semanas 6–10 · Flujo mínimo completo

**El hito del semestre.** Al cerrarla, el sistema recorre el circuito entero:
REST → persistencia → Kafka → consumidor → GraphQL. Cada pieza en su versión
más simple; lo importante es que cierre.

---

### ✅ #9 · Tabla outbox y relay de publicación — cerrado

**Asignado:** Andrés · **Toca:** `agenda-service/.../outbox/` · **Depende de:** #8

- [x] `INSERT` en outbox dentro de la misma transacción que la cita
- [x] `@Scheduled` que lee pendientes, publica y marca `enviado_en`
- [x] Clave de partición = `profesionalId`

**Aceptación:** se apaga Kafka, se reservan 3 citas, se prende Kafka y los 3
eventos aparecen publicados. **Demo estrella de la sustentación.**

> Los reintentos y la DLQ van en la Fase 3. Aquí basta con que publique.

---

### 🔶 #10 · notificaciones-service consumiendo — funcional, con un pendiente

**Asignado:** Johan · **Toca:** `notificaciones-service/` · **Depende de:** #9

- [x] Consume `citas.reservadas` y `citas.canceladas`
- [x] Guarda el mensaje en BD con el adaptador `REGISTRO` (sin costo)
- [ ] Revertir el bump a Java 25 / Spring Boot 3.5 de `feature/10-notificaciones-service`
      antes de mergear — mantener Java 21 · Spring Boot 3.3 (regla de CLAUDE.md).
      Si la motivación era el CVE de PostgreSQL JDBC, subir solo la versión de esa
      dependencia (`42.7.12`), no el JDK ni el Spring Boot padre.

**Aceptación:** reservar desde la API deja un registro en la tabla de envíos.

---

### ✅ #11 · Gateway GraphQL con `panelRecepcion` — cerrado

**Asignado:** Luis · **Toca:** `gateway-graphql/` · **Depende de:** #7, #8

- [x] `pom.xml` + esqueleto Spring Boot con Spring for GraphQL
- [x] Esquema cargado desde `docs/graphql/schema.graphqls` (idéntico al contrato)
- [x] Resolver de `panelRecepcion` llamando a `agenda-service` por REST, con
      fallback a datos de ejemplo si el backend no responde
- [x] `/graphiql` habilitado para la demostración

**Aceptación:** una sola consulta devuelve agenda del día + configuración.
Cumplida vía [PR #14](../../pull/14).

> El docente lo incluyó dentro del flujo mínimo. Las métricas ya resuelven
> contra `analitica-service` real (#19, adelantado de Fase 3). Las
> mutaciones del esquema (`crearServicio`, `crearBloqueo`, etc.) quedan para
> cuando exista el `ConfiguracionController` (#14 de este backlog) — no
> bloquean el cierre de la Fase 2, que solo exige que `panelRecepcion`
> resuelva con datos reales.

---

### ✅ #12 · Cancelar y reprogramar por token — cerrado

**Asignado:** Brayan · **Toca:** `.../controller/GestionController.java` · **Depende de:** #8

- [x] `GET /v1/gestion/{token}` con el celular enmascarado
- [x] `DELETE /v1/gestion/{token}?confirmar=true`
- [ ] Token vencido o revocado → 404 — **confirmar con un test manual/E2E**

**Aceptación:** el token de una cita no da acceso a ninguna otra.

---

### 🔶 #13 · PWA · pantalla pública de reserva — falta conectar al backend real

**Asignado:** Johan (líder original) · construida en la práctica por Andrés vía PR #12
**Toca:** `web/index.html`, `web/app.js`, `web/style.css`, `web/manifest.json`, `web/sw.js`
**Depende de:** #7, #8

- [x] Elegir servicio → ver cupos → nombre y celular → confirmar
- [x] Muestra el enlace de gestión al confirmar
- [x] Maneja el `409`: muestra alternativas, no un error feo
- [x] `manifest.json` + service worker que cachea la interfaz
- [ ] Sin conexión: mensaje claro de que reservar requiere internet — **verificar**
- [ ] **Conectar contra `agenda-service` real** (hoy corre contra `web/mock.js`).
      Ya existen dos ramas remotas listas para esto: `fix/agenda-service-cors`
      (habilita CORS) y `feature/4-pwa-conectar-backend` (apunta la PWA al
      backend real). Falta abrir/mergear los PR.

**Aceptación:** instalable en Android desde Chrome y se reserva de punta a
punta contra el backend real.

> HTML, CSS y JS nativo. Sin React ni build.

---

### 🟢 #14 · Configuración del negocio

**Asignado:** ~~Luis~~ → **Brayan**, si sobra tiempo tras la revisión cruzada
**Toca:** `.../controller/ConfiguracionController.java` · **Depende de:** #6

No existe todavía ningún `ConfiguracionController`. No bloquea el cierre de
Fase 2, así que solo se toma si Luis sigue ocupado con `gateway-graphql` (#11).

- [ ] CRUD de servicios y profesionales, horarios y bloqueos
- [ ] Todos leen `X-Negocio-Id`

**Aceptación:** con el `X-Negocio-Id` de un negocio no se ven datos de otro.

---

### ✅ #15 · Agenda del profesional y asistencia — cerrado (sin mergear)

**Asignado:** Brayan · **Toca:** `agenda-service/.../administracion/` · **Depende de:** #6

Implementado en rama local `feature/15-agenda-profesional`, pendiente de PR:

- [x] `GET /v1/agenda/{profesionalId}?fecha=` — `AgendaController` + `AgendaService`,
      valida que el profesional pertenezca al `X-Negocio-Id`, celular sin
      enmascarar (a diferencia de la ruta de gestión, aquí lo pide el negocio)
- [x] `PATCH /v1/citas/{id}/estado` con `ATENDIDA` o `NO_ASISTIO`, publica
      `citas.estado` por outbox (clave de partición `profesionalId`)
- [x] No permite marcar estado en una cita cancelada ni en una ya cerrada
- [x] Pruebas unitarias (`AgendaServiceTest`, estilo `GestionServiceTest`)
- [x] `ManejadorErrores` ahora traduce `X-Negocio-Id` faltante al formato
      `{codigo, mensaje}` — primera ruta administrativa que exige esa cabecera
- [x] Refactor menor: el DTO `CitaDetalle` (antes solo de `gestion/`) se movió
      a `comun/dto/`, porque ahora lo comparten dos rutas con distinto
      criterio de enmascarado

**Aceptación:** no permite marcar estado en una cita cancelada.

> **Pendiente:** abrir el PR contra `main` y que lo revise alguien de la otra
> pareja (Andrés o Johan), como manda la regla de revisión cruzada.

---

## FASE 3 · Semanas 11–15 · Robustez

Solo cuando el circuito de la Fase 2 cierre de punta a punta. Si vamos
atrasados, se recorta desde aquí hacia arriba.

---

### ✅ #16 · Reintentos y cola de mensajes fallidos — cerrado

**Asignado:** Andrés (terminado por Brayan) · **Depende de:** #9

- [x] Backoff exponencial (1 s / 4 s / 16 s, 3 intentos) en los listeners de
      `citas.reservadas`, `citas.canceladas` y `citas.estado` —
      `notificaciones-service` y `analitica-service`
- [x] Al agotar los reintentos, `DeadLetterPublishingRecoverer` publica en
      `citas.dlq` en vez de perder el mensaje o bloquear la partición
- [x] `GET /v1/mensajes-fallidos` en `notificaciones-service`, con su propio
      consumidor de `citas.dlq` que deja constancia en
      `notificaciones.mensaje_fallido` (`V2__mensajes_fallidos.sql`)
- [x] Alerta en el panel de recepción (#20) cuando hay mensajes caídos

**Aceptación cumplida:** verificado contra Kafka/Postgres reales, no solo
que compila — ver el hallazgo real documentado arriba en el estado de la
Fase 3 (#18 lo encontró, la DLQ lo capturó en vez de perderlo).

### ✅ #17 · Prueba de concurrencia con Testcontainers — cerrado (parcial)

**Asignado:** Brayan · **Depende de:** #8

- [x] `ReservaConcurrenciaTest`: 100 hilos reales contra Postgres real
      (Testcontainers, no H2 ni mocks) reservando el mismo cupo al mismo
      tiempo, cada uno con su propia `Idempotency-Key`
- [x] **Aceptación cumplida:** 1 éxito, 99 rechazados por `cita_sin_solape`
- [x] "Pasa varias veces seguidas" — confirmado en verde en más de una
      corrida de CI durante el cierre de Fase 3 (PRs #32 y #34), después de
      corregir el segundo hallazgo de abajo

**No se borra ni se marca `@Disabled` nunca** (CLAUDE.md).

> **Primer hallazgo de esta prueba:** bajo 100 hilos genuinamente
> simultáneos, Postgres no siempre resuelve el choque como la violación
> limpia de `cita_sin_solape` — a veces lo resuelve como **deadlock**
> (SQLSTATE 40P01) entre las comprobaciones del índice GiST, algo que
> `PersistenciaReserva`/`ReservaService` no manejaban. Se agregó un
> reintento acotado (hasta 8 intentos) con espera aleatoria entre cada
> uno (backoff con jitter) en `ReservaService.crearConReintentos`: sin la
> espera, los mismos hilos volvían a chocar en el mismo instante y
> encadenaban deadlock tras deadlock. Con la espera, cero deadlocks en la
> corrida limpia. Sin Testcontainers con hilos reales, este caso nunca se
> habría encontrado — un mock nunca deadlockea.
>
> **Segundo hallazgo, durante el cierre de Fase 3:** bajo más contención en
> el runner de CI, incluso con los reintentos del `INSERT`, algunos hilos
> agotaban sus 8 intentos y caían al camino del `409` — que calcula "cupos
> más cercanos" con una lectura aparte (`disponibilidadService.calcular`)
> sin ninguna protección contra deadlock. Esa lectura también podía
> deadlockear, y como no se esperaba, tumbaba el hilo entero con una
> excepción no controlada en vez de responder el `409` normal. Corregido en
> `ReservaService.alternativasOMejorVacio`: ante un deadlock en esa
> lectura, se responde el `409` sin alternativas cercanas en lugar de un
> error genérico — no vale la pena reintentar una lectura cuando el cupo ya
> quedó resuelto como ocupado de todas formas.
>
> **No se pudo correr en verde en Windows** (esta máquina): el transporte
> por named pipe de `docker-java` no negocia bien la versión de la API
> contra Docker Desktop reciente. Confirmado que no es un problema del
> código: `docker info`/`docker ps` funcionan perfectamente por el mismo
> pipe. CI corre en Linux (socket Unix normal) y ahí sí pasa limpio.

### ✅ #18 · Recordatorio de 24 horas — cerrado

**Asignado:** Johan (terminado por Brayan) · **Depende de:** #10

- [x] Al confirmar una cita se programa `RECORDATORIO_24H` en `PENDIENTE`
      (`enviarEn = inicio - 24h`), sin enviarlo aún
- [x] `RecordatorioScheduler` (`@Scheduled`) dispara los vencidos
- [x] Si la cita se cancela antes de su hora, el recordatorio pendiente pasa
      a `CANCELADO` y nunca sale
- [x] "Recuperación tras reinicio" es consecuencia de dónde vive el estado
      (`notificaciones.programacion`, no memoria): si el contenedor se cae,
      el scheduler encuentra los vencidos en su siguiente ciclo, no hace
      falta código aparte para "recuperar"
- [x] `V3__canal_opcional_mientras_pendiente.sql`: corrige el `NOT NULL`
      original de `canal` en la tabla `programacion` — ver el hallazgo real
      documentado arriba, encontrado por la DLQ de #16 contra Kafka/Postgres
      reales

**Aceptación cumplida:** probado en vivo — reserva real → confirmación +
recordatorio pendiente creado; cancelación → recordatorio pasa a
`CANCELADO`; reinicio del contenedor → el pendiente sigue ahí y se dispara
en su momento porque nunca estuvo solo en memoria.

### ✅ #19 · analitica-service — cerrado

**Asignado:** Luis (retomado por Brayan) · **Depende de:** #9

- [x] Consume `citas.reservadas`, `citas.canceladas` y `citas.estado`,
      deduplica por `eventoId` (mismo patrón que notificaciones-service)
- [x] Actualiza `analitica.metrica_diaria` con upserts atómicos
- [x] `GET /v1/metricas` (`X-Negocio-Id`, `desde`, `hasta`)
- [x] `gateway-graphql` ya no usa el stub fijo: `panelRecepcion.metricasDelMes`
      y la query `metricas` resuelven contra este servicio (con fallback a
      ceros si no responde, mismo patrón que `AgendaClient`)

**Aceptación:** verificado a mano contra Docker real — reservar una cita,
marcarla `ATENDIDA` y ver las cifras reales en `GET /v1/metricas` y en
`panelRecepcion` por GraphQL.

> Simplificación documentada (ver `docs/openapi/analitica-service.yaml`):
> `ocupacion` estima minutos disponibles con una jornada configurable
> (600 min/día por defecto) en vez de leer `agenda.horario_atencion` —
> cruzar esquemas entre servicios no es el patrón del proyecto.
> `topServicios` devuelve vacío: la tabla solo guarda `servicioId`, no el
> nombre. Ambas son mejoras válidas para retomar después, no bloquean el
> cierre de esta tarea.

### ✅ #20 · PWA · panel de recepción — cerrado

**Asignado:** Johan (terminado por Brayan) · **Depende de:** #11, #19

- [x] `web/panel.html`: agenda del día, negocio y métricas del mes en una
      sola consulta GraphQL (`panelRecepcion`) — página standalone, HTML/CSS/JS
      nativo, mismo criterio que `app.js`
- [x] Alerta de mensajes fallidos (`GET /v1/mensajes-fallidos`, #16), no
      crítica: si `notificaciones-service` no responde, el panel igual funciona
- [x] CORS en `gateway-graphql` y `notificaciones-service` (`agenda-service`
      ya lo tenía de antes)
- [x] Se agrega al app shell del service worker

**Aceptación cumplida:** verificado en vivo con el navegador — estado
vacío, error de CORS reproducido y corregido, y una reserva real hecha por
API apareciendo correctamente en la tabla tras recargar, sin errores en
consola.

### ✅ #21 · Separación entre negocios — cerrado

**Asignado:** Andrés (terminado por Brayan) · **Depende de:** #14

- [x] `SeparacionEntreNegociosTest`: contra Postgres real (Testcontainers),
      dos negocios, una cita del profesional de uno; consultar su agenda o
      registrar su estado con el `X-Negocio-Id` del otro (id correcto, negocio
      equivocado) devuelve "no encontrado", nunca la cita ajena
- [x] Control positivo: con el negocio correcto, la misma consulta sí funciona

**Aceptación cumplida:** el aislamiento ya existía —
`findByIdAndNegocioId` en `ProfesionalRepository`/`CitaRepository` ya
filtraba por negocio—, esta prueba lo deja como garantía formal verificada
contra Postgres real, no como algo que "se supone que funciona".

### ✅ #22 · Documentación final y guion de sustentación — cerrado

**Asignado:** los cuatro (terminado por Brayan) · **Depende de:** todo

- [x] `README.md` al día con el estado real de las tres fases
- [x] Este backlog (`docs/TAREAS.md`) cerrado tarea por tarea
- [x] `docs/GUION-SUSTENTACION.md` actualizado con Fase 3 completa
- [ ] Ensayar la sustentación dos veces — **pendiente del equipo, no es
      trabajo de código**

---

## Cómo trabajar cada tarea

1. `git checkout main && git pull`
2. `git checkout -b feature/8-reservar-cita`
3. Abre Claude Code en la raíz. Lee `CLAUDE.md` automáticamente.
4. Arranca con: *"Voy a trabajar el issue #8. Lee CLAUDE.md y
   docs/openapi/agenda-service.yaml antes de proponer nada."*
5. Commits pequeños, en español: `feat: reservar cita con idempotencia`
6. PR contra `main`, describiendo qué se probó
7. **Lo revisa alguien de la otra pareja**, no tu compañero de tarea

### Por qué la revisión cruzada

El profesor elige a **uno solo** para sustentar y la nota es grupal. Si Johan
nunca vio el código del `EXCLUDE` y le toca a él, se hunden los cuatro.
Revisar el código de otro es la forma barata de que todos entiendan todo.
