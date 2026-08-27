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

# FASE 1 · Semanas 1–5 · Cimientos y persistencia

Al cerrar la fase, `agenda-service` reserva contra la base de datos real.

---

### 🔴 #1 · Crear el repositorio y la estructura base
**Asignado:** Brayan · **Toca:** raíz, `.github/` · **Depende de:** nada

- [ ] Repo `agenda-d` en GitHub, privado, los cuatro como colaboradores
- [ ] `README.md`, `CLAUDE.md`, `.gitignore` (Java + Maven + IDE)
- [ ] Carpetas de los 3 servicios, el gateway y `web/`
- [ ] Rama `main` protegida: exige 1 aprobación para hacer merge
- [ ] Plantilla de PR en `.github/pull_request_template.md`

**Aceptación:** los cuatro clonan y nadie puede hacer push directo a `main`.

---

### 🔴 #2 · Docker Compose con Postgres y Kafka
**Asignado:** Johan · **Toca:** `docker-compose.yml` · **Depende de:** #1

- [ ] Perfiles `core` y `full`
- [ ] `mem_limit` por contenedor (alguien tiene 8 GB)
- [ ] Kafka en KRaft con listener externo en `localhost:29092`
- [ ] Healthchecks en Postgres y Kafka

**Aceptación:** `docker compose --profile core up -d` deja ambos sanos y
`kafka-topics.sh --list` responde sin error.

> Prompt: *"Lee CLAUDE.md. Crea el docker-compose.yml con perfiles core y full.
> Postgres 16 y Kafka 3.7 en KRaft, healthchecks y límites de memoria. Los
> servicios Java aún no existen, déjalos comentados."*

---

### 🔴 #3 · Migración Flyway con el esquema completo
**Asignado:** Andrés · **Toca:** `agenda-service/src/main/resources/db/migration/`
**Depende de:** #2

- [ ] `V1__esquema_inicial.sql` con los tres esquemas
- [ ] Restricción `EXCLUDE` sobre `cita`
- [ ] Índice único de idempotencia
- [ ] Datos de prueba: un negocio, dos servicios, dos profesionales, horarios

**Aceptación:** corre limpio contra Postgres 16 y estos tres casos se cumplen:
1. Cita 10:00–11:00 con Laura → entra
2. Cita 10:30–11:30 con Laura → **rechazada** por `cita_sin_solape`
3. Cita 10:00–11:00 con Andrés → entra

> El caso 2 es el que un `UNIQUE(profesional_id, inicio)` dejaría pasar.
> El docente señaló justamente esa confusión en la primera entrega.

---

### 🔴 #4 · Esqueletos de los servicios Spring Boot
**Asignado:** Luis · **Toca:** `*/pom.xml`, `*/Dockerfile`, `*/Application.java`, `*/application.yml`
**Depende de:** #2

- [ ] Java 21, Spring Boot 3.3, un `pom.xml` por servicio (sin proyecto padre)
- [ ] Dockerfile multi-stage por servicio
- [ ] `/actuator/health` respondiendo en los cuatro
- [ ] `agenda-service` con springdoc y Flyway conectados

**Aceptación:** `docker compose --profile full up -d --build` levanta todo y
los cuatro `/actuator/health` responden `UP`.

---

### 🟡 #5 · Revisar y aprobar los contratos
**Asignado:** los cuatro · **Toca:** `docs/openapi/`, `docs/graphql/`, `docs/eventos/`
**Depende de:** #1

- [ ] Cada uno lee el OpenAPI, el esquema GraphQL y el contrato de eventos
- [ ] Se discuten los cambios en la sesión semanal y se aprueban por PR

**Aceptación:** los cuatro pueden explicar qué devuelve `/disponibilidad`, qué
resuelve `panelRecepcion` y qué lleva `citas.reservadas`.

---

### 🟡 #6 · Dominio y repositorios
**Asignado:** Brayan · **Toca:** `agenda-service/.../domain/`, `.../repository/`
**Depende de:** #3, #4

- [ ] Entidades JPA: Negocio, Servicio, Profesional, HorarioAtencion, Bloqueo, Cita
- [ ] Repositorios Spring Data, todos filtrando por `negocioId`
- [ ] `Instant` para instantes, nunca `LocalDateTime`

**Aceptación:** test de integración que guarda y lee una cita.

---

### 🟡 #7 · Cálculo de disponibilidad
**Asignado:** Brayan · **Toca:** `.../service/DisponibilidadService.java` · **Depende de:** #6

- [ ] Cruza horario de atención + bloqueos + citas existentes
- [ ] Genera cupos según la duración del servicio
- [ ] Convierte hora local del negocio a UTC correctamente
- [ ] `GET /v1/publico/{slug}/disponibilidad`

**Aceptación:** cubre día sin horario, día con bloqueo, día con cita tomada y
cambio de día por zona horaria (19:00 en Colombia es del día siguiente en UTC).

> La tarea más difícil del proyecto. Que la haga alguien con tiempo.

---

### 🟡 #8 · Reservar cita
**Asignado:** Andrés · **Toca:** `.../service/ReservaService.java`, `.../controller/CitaPublicaController.java`
**Depende de:** #6

- [ ] `POST /v1/publico/{slug}/citas` con `Idempotency-Key`
- [ ] Intenta el `INSERT` y captura la violación de `cita_sin_solape`
- [ ] Traduce el conflicto a `409` con los cupos más cercanos
- [ ] Genera el token de gestión y devuelve el enlace

**Aceptación:** el `409` llega con mensaje en español y alternativas.
**No se acepta** si valida disponibilidad con un `SELECT` antes de insertar.

---

# FASE 2 · Semanas 6–10 · Flujo mínimo completo

**El hito del semestre.** Al cerrarla, el sistema recorre el circuito entero:
REST → persistencia → Kafka → consumidor → GraphQL. Cada pieza en su versión
más simple; lo importante es que cierre.

---

### 🔴 #9 · Tabla outbox y relay de publicación
**Asignado:** Andrés · **Toca:** `agenda-service/.../outbox/` · **Depende de:** #8

- [ ] `INSERT` en outbox dentro de la misma transacción que la cita
- [ ] `@Scheduled` que lee pendientes, publica y marca `enviado_en`
- [ ] Clave de partición = `profesionalId`

**Aceptación:** se apaga Kafka, se reservan 3 citas, se prende Kafka y los 3
eventos aparecen publicados. **Demo estrella de la sustentación.**

> Los reintentos y la DLQ van en la Fase 3. Aquí basta con que publique.

---

### 🔴 #10 · notificaciones-service consumiendo
**Asignado:** Johan · **Toca:** `notificaciones-service/` · **Depende de:** #9

- [ ] Consume `citas.reservadas` y `citas.canceladas`
- [ ] Guarda el mensaje en BD con el adaptador `REGISTRO` (sin costo)

**Aceptación:** reservar desde la API deja un registro en la tabla de envíos.

---

### 🔴 #11 · Gateway GraphQL con `panelRecepcion`
**Asignado:** Luis · **Toca:** `gateway-graphql/` · **Depende de:** #7, #8

- [ ] Esquema cargado desde `schema.graphqls`
- [ ] Resolver de `panelRecepcion` llamando a `agenda-service` por REST
- [ ] `/graphiql` habilitado para la demostración

**Aceptación:** una sola consulta devuelve agenda del día + configuración.

> El docente lo incluyó dentro del flujo mínimo: por eso está aquí y no al
> final. Las métricas pueden devolver valores fijos mientras no exista
> `analitica-service`.

---

### 🟡 #12 · Cancelar y reprogramar por token
**Asignado:** Brayan · **Toca:** `.../controller/GestionController.java` · **Depende de:** #8

- [ ] `GET /v1/gestion/{token}` con el celular enmascarado
- [ ] `DELETE /v1/gestion/{token}?confirmar=true`
- [ ] Token vencido o revocado → 404

**Aceptación:** el token de una cita no da acceso a ninguna otra.

---

### 🟡 #13 · PWA · pantalla pública de reserva
**Asignado:** Johan · **Toca:** `web/index.html`, `web/app.js`, `web/style.css`, `web/manifest.json`, `web/sw.js`
**Depende de:** #7, #8

- [ ] Elegir servicio → ver cupos → nombre y celular → confirmar
- [ ] Muestra el enlace de gestión al confirmar
- [ ] Maneja el `409`: muestra alternativas, no un error feo
- [ ] `manifest.json` + service worker que cachea la interfaz
- [ ] Sin conexión: mensaje claro de que reservar requiere internet

**Aceptación:** instalable en Android desde Chrome y se reserva de punta a
punta contra el backend real.

> HTML, CSS y JS nativo. Sin React ni build.

---

### 🟢 #14 · Configuración del negocio
**Asignado:** Luis · **Toca:** `.../controller/ConfiguracionController.java` · **Depende de:** #6

- [ ] CRUD de servicios y profesionales, horarios y bloqueos
- [ ] Todos leen `X-Negocio-Id`

**Aceptación:** con el `X-Negocio-Id` de un negocio no se ven datos de otro.

---

### 🟢 #15 · Agenda del profesional y asistencia
**Asignado:** Luis · **Toca:** `.../controller/AgendaController.java` · **Depende de:** #6

- [ ] `GET /v1/agenda/{profesionalId}?fecha=`
- [ ] `PATCH /v1/citas/{id}/estado` con `ATENDIDA` o `NO_ASISTIO`

**Aceptación:** no permite marcar estado en una cita cancelada.

---

# FASE 3 · Semanas 11–15 · Robustez

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

# Cómo trabajar cada tarea

1. `git checkout main && git pull`
2. `git checkout -b feature/8-reservar-cita`
3. Abre Claude Code en la raíz. Lee `CLAUDE.md` automáticamente.
4. Arranca con: *"Voy a trabajar el issue #8. Lee CLAUDE.md y
   docs/openapi/agenda-service.yaml antes de proponer nada."*
5. Commits pequeños, en español: `feat: reservar cita con idempotencia`
6. PR contra `main`, describiendo qué se probó
7. **Lo revisa alguien de la otra pareja**, no tu compañero de tarea

## Por qué la revisión cruzada

El profesor elige a **uno solo** para sustentar y la nota es grupal. Si Johan
nunca vio el código del `EXCLUDE` y le toca a él, se hunden los cuatro.
Revisar el código de otro es la forma barata de que todos entiendan todo.
