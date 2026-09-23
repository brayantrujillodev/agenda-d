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

## Estado y próximos pasos · 2026-09-23

**Fase 2 cerrada de punta a punta.** Con el gateway GraphQL mergeado
([PR #14](../../pull/14)), el circuito completo funciona: REST →
persistencia → outbox → Kafka → `notificaciones-service` consumiendo →
gateway GraphQL respondiendo `panelRecepcion` con datos reales de
`agenda-service`, más la PWA pública instalable.

Luis y Johan no están contribuyendo activamente por ahora. El resto del
equipo sigue con el backlog de Fase 3, reasignando lo que estaba en su
cabeza si hace falta:

| Quién | Qué tiene pendiente ahora mismo | Nota |
|---|---|---|
| **Brayan** | #17 · Testcontainers + prueba de concurrencia — es la única pieza de Fase 3 que `CLAUDE.md` marca obligatoria y que nunca se borra | Camino crítico para la sustentación |
| **Andrés** | #16 · Reintentos y DLQ; #21 · Separación entre negocios | Ya activo, sigue con lo suyo |
| **Luis** | #19 · `analitica-service` — sin arrancar | Retomar si vuelve a estar disponible; si no, se recorta primero (es 🟢) |
| **Johan** | #18 · Recordatorio 24h; #20 · PWA panel de recepción — sin arrancar | Retomar si vuelve a estar disponible; si no, se recortan (🟡 y 🟢) |

Pendiente menor: `feature/10-notificaciones-service` (PR #16 en GitHub) **no
se mergea tal como está** — sube a Java 25 / Spring Boot 3.5 y `CLAUDE.md`
fija Java 21 · Spring Boot 3.3 para todo el proyecto. Si la motivación real
era el CVE de PostgreSQL JDBC, subir solo esa dependencia (`42.7.12`).

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

### 🔶 #4 · Esqueletos de los servicios Spring Boot — parcial

**Asignado:** Luis · **Toca:** `*/pom.xml`, `*/Dockerfile`, `*/Application.java`, `*/application.yml`
**Depende de:** #2

- [x] Java 21, Spring Boot 3.3 — `agenda-service` y `notificaciones-service`
- [ ] `gateway-graphql` y `analitica-service` siguen sin `pom.xml` (solo `.gitkeep`)
- [ ] Dockerfile multi-stage por servicio
- [x] `/actuator/health` respondiendo en `agenda-service` y `notificaciones-service`
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

> El docente lo incluyó dentro del flujo mínimo. Las métricas devuelven
> valores fijos mientras no exista `analitica-service` (Fase 3). Las
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

### 🟡 #16 · Reintentos y cola de mensajes fallidos

**Asignado:** Andrés · **Depende de:** #9
Tres reintentos con espera creciente, luego `citas.dlq`. Endpoint que lista lo
caído y alerta en el panel. **Aceptación:** un consumidor que falla siempre
deja el mensaje en la DLQ y no bloquea la partición.

### 🟡 #17 · Pruebas con Testcontainers

**Asignado:** Brayan · **Depende de:** #8
Postgres y Kafka reales. Incluye la **prueba de concurrencia**: 100 hilos sobre
el mismo cupo, 1 con 201 y 99 con 409. **Aceptación:** pasa 10 veces seguidas.
**No se borra ni se marca `@Disabled` nunca.**

### 🟡 #18 · Recordatorio de 24 horas

**Asignado:** Johan · **Depende de:** #10
Programación persistida en tabla, `@Scheduled` que dispara los vencidos,
recuperación al reiniciar, cancelación al cancelar la cita.
**Aceptación:** se programa, se reinicia el contenedor y el recordatorio sale.

### 🟢 #19 · analitica-service

**Asignado:** Luis · **Depende de:** #9
Consume los tres tópicos y actualiza `metrica_diaria`. `GET /v1/metricas`.
Se conecta al `panelRecepcion` que ya existe.
**Aceptación:** las cifras cuadran con la tabla `cita`.

### 🟢 #20 · PWA · panel de recepción

**Asignado:** Johan · **Depende de:** #11, #19
Agenda del día, configuración y métricas, en una sola consulta GraphQL.

### 🟢 #21 · Separación entre negocios

**Asignado:** Andrés · **Depende de:** #14
Prueba negativa que intenta leer una cita de otro negocio por su UUID directo
y debe obtener respuesta vacía.

### 🟢 #22 · Documentación final y guion de sustentación

**Asignado:** los cuatro · **Depende de:** todo
README al día y un guion de 10 minutos que **cualquiera** pueda dar.
Ensayarlo dos veces.

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
