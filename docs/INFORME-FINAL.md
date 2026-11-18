# Informe final · AGENDA-D

Plataforma distribuida de agendamiento de citas para negocios de servicios.
Proyecto de aula · Programación Avanzada · SOF-G2-NOC · Equipo 2.

| | |
|---|---|
| **Repositorio** | <https://github.com/brayantrujillodev/agenda-d> |
| **Release** | [`v1.0.2`](https://github.com/brayantrujillodev/agenda-d/releases/tag/v1.0.2) (imágenes en GHCR) |
| **PWA publicada** | <https://brayantrujillodev.github.io/agenda-d/> |
| **Equipo** | Brayan Trujillo · Andrés Bonilla · Johan Camacho · Luis Moncada |
| **Periodo** | 2026-08-27 (primer commit) a 2026-11-18 (cierre de este informe) |
| **Stack** | Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Kafka 3.7 (KRaft) · Spring for GraphQL · Docker Compose · PWA en HTML/CSS/JS |

## 1. Resumen

AGENDA-D permite que un negocio de servicios (barbería, consultorio, taller,
veterinaria) publique un enlace o un QR. El cliente ve los cupos libres,
reserva **sin crear cuenta** y recibe confirmación y un recordatorio 24 horas
antes. El negocio ve su agenda del día y sus indicadores de ocupación e
inasistencia en una sola pantalla.

El sistema está formado por tres servicios de negocio, un gateway GraphQL y
una PWA, comunicados por REST y por eventos en Kafka. Las tres fases del plan
están cerradas: contratos y cimientos, circuito mínimo de extremo a extremo y
robustez (reintentos con DLQ, recordatorio con recuperación tras reinicio,
analítica, panel y prueba de separación entre negocios).

Resultados medibles de la verificación final (sección 8):

| Indicador | Resultado |
|---|---|
| Verificaciones del flujo completo contra el stack real | **31 de 31** |
| Colección de Postman corrida con Newman | **51 peticiones, 77 aserciones, 0 fallos** |
| Pruebas automatizadas (JUnit) | **115**, 0 fallos en el último reporte registrado |
| Prueba de concurrencia: 100 hilos sobre el mismo cupo | 1 éxito y 99 conflictos, en verde en el CI de GitHub |
| Servicios sanos con `docker compose --profile full up -d --build` | 4 de 4 (`/actuator/health` = 200) |
| Errores y advertencias en el log de un arranque limpio | 0 |

## 2. Planteamiento del problema

Los negocios pequeños de servicios agendan por mensajes y llamadas. El
problema técnico de fondo es doble: dos personas pueden pedir el mismo cupo
al mismo tiempo, y avisar al cliente no debe hacer fallar ni retrasar la
reserva. Una solución ingenua (validar con un `SELECT` y luego hacer el
`INSERT`, o llamar a un servicio de notificaciones dentro de la reserva) se
rompe justamente bajo carga o cuando un servicio está caído.

**Fuera de alcance por acuerdo con el docente:** autenticación de usuarios.
El contexto de negocio llega por el `slug` de la URL (rutas públicas) o por
la cabecera `X-Negocio-Id` (rutas administrativas).

## 3. Objetivos

### Objetivo general

Construir una plataforma distribuida de agendamiento de citas en la que
ninguna cita pueda solaparse con otra del mismo profesional y en la que las
notificaciones y la analítica funcionen de forma asíncrona sin poner en
riesgo la reserva.

### Objetivos específicos

| # | Objetivo específico | Cómo se cumplió | Evidencia |
|---|---|---|---|
| 1 | Garantizar que no existan citas solapadas aun bajo concurrencia | Restricción `EXCLUDE USING gist` sobre `tstzrange(inicio, fin, '[)')` en la base de datos; el servicio intenta el `INSERT` y traduce la violación a `409` con los cupos más cercanos | Prueba de 100 hilos (1 éxito, 99 conflictos), sección 8.4 |
| 2 | Desacoplar la reserva de las notificaciones y la analítica | Patrón outbox: la cita y el evento se escriben en la misma transacción; un relay publica en Kafka | Contrato de eventos, `OutboxRelayTest`, secciones 5 y 8 |
| 3 | Tolerar fallos de los consumidores sin perder mensajes | Deduplicación por `eventoId`, 3 reintentos con espera creciente (1 s, 4 s, 16 s) y cola `citas.dlq` visible desde el panel | Pruebas de los consumidores y `GET /v1/mensajes-fallidos` |
| 4 | Enviar un recordatorio 24 h antes que sobreviva a un reinicio | Recordatorio persistido como fila `PENDIENTE` y recuperado por un scheduler | `RecordatorioSchedulerTest`, migración `V3` |
| 5 | Entregar la pantalla del negocio en una sola consulta | Gateway GraphQL con `panelRecepcion`, 5 queries y 8 mutaciones | `docs/evidencias/P3-ENTREGA-1.md` (31/31) |
| 6 | Medir ocupación e inasistencia | `analitica-service` consume los tres tópicos y mantiene `metrica_diaria` | `GET /v1/metricas`, `MetricasConsumidorTest` |
| 7 | Permitir reservar desde el celular sin instalar nada | PWA instalable (`manifest.json` + service worker) con reserva y panel | Carpeta `web/`, Pages publicado |
| 8 | Aislar los datos de cada negocio | Todas las consultas del dominio filtran por `negocio_id` | `SeparacionEntreNegociosTest` (7 casos, Postgres real) |
| 9 | Dejar el proyecto reproducible por cualquier integrante | Un comando por perfil de Docker Compose, healthchecks, migraciones Flyway, CI | Sección 7 |

## 4. Metodología

**Marco.** Desarrollo incremental en tres fases, con un hito explícito por
fase y una regla de prioridad acordada con el docente: primero el circuito
completo, después la robustez.

| Fase | Contenido | Estado |
|---|---|---|
| 1 · Cimientos | Contratos OpenAPI/GraphQL/eventos, migración con el `EXCLUDE`, Compose con `/actuator/health`, reserva contra la BD real | Cerrada |
| 2 · Flujo mínimo | REST → persistencia → outbox → Kafka → consumidor → GraphQL, más la PWA pública | Cerrada |
| 3 · Robustez | DLQ y reintentos, idempotencia, Testcontainers con concurrencia, recordatorio de 24 h, analítica, panel, separación entre negocios | Cerrada |

**Prácticas del equipo.**

- **Contratos primero.** El OpenAPI, el esquema GraphQL y el contrato de
  eventos se acuerdan antes de programar y no se cambian en silencio.
- **Rama por tarea y PR revisado de forma cruzada**, para que cualquiera
  pueda sustentar el proyecto completo. 38 PR en el repositorio.
- **Sin mocks en lo que importa.** Las pruebas de concurrencia y de
  separación entre negocios corren con Testcontainers contra Postgres real; la
  evidencia de extremo a extremo corre contra el stack de Docker real.
- **Integración semanal conjunta** de dos horas, para que los servicios que
  se hablan por eventos se integren en una mesa y no por chat.
- **CI en GitHub Actions:** compilación y pruebas (`build.yml`), lint de
  Markdown, YAML, shell y SQL (`lint.yml`), publicación de la PWA
  (`pages.yml`) y de las imágenes (`release.yml`).
- **Probar contra la infraestructura real encontró errores que ningún mock
  habría mostrado** (sección 9).

**Reparto de liderazgo** (no de exclusividad):

| Integrante | Liderazgo |
|---|---|
| Brayan Trujillo | `agenda-service` (dominio, `EXCLUDE`, outbox) |
| Andrés Bonilla | PWA pública de reserva |
| Johan Camacho | `notificaciones-service` (consumidor Kafka) |
| Luis Moncada | `gateway-graphql` |

## 5. Arquitectura

```text
                     ┌────────────────────────────┐
  Cliente (PWA) ───► │ agenda-service :8081       │
  REST público       │  · cupos, reserva, gestión │
                     │  · EXCLUDE en PostgreSQL   │
                     └─────────────┬──────────────┘
                                   │ misma transacción
                       agenda.cita + agenda.outbox
                                   │ relay @Scheduled (2 s)
                                   ▼
        Kafka: citas.reservadas · citas.canceladas · citas.estado
               (clave = profesionalId, 3 particiones, 7 días)
                 │                                   │
                 ▼                                   ▼
   notificaciones-service :8082            analitica-service :8083
   · confirmación / cancelación            · metrica_diaria
   · recordatorio 24 h                     · GET /v1/metricas
   · 3 reintentos ─► citas.dlq
                 
  Panel (PWA) ──► gateway-graphql :8080 ──► agenda-service + analitica-service
                  panelRecepcion en una sola consulta
```

### 5.1 Decisiones de diseño que el equipo debe poder sustentar

**El solapamiento lo impide la base de datos, no el código.**

```sql
ALTER TABLE agenda.cita ADD CONSTRAINT cita_sin_solape
    EXCLUDE USING gist (
        profesional_id WITH =,
        tstzrange(inicio, fin, '[)') WITH &&
    ) WHERE (estado <> 'CANCELADA');
```

Un `SELECT` previo al `INSERT` deja una ventana en la que cabe otra petición.
Un `UNIQUE(profesional_id, inicio)` tampoco sirve: una cita de 60 minutos a
las 10:00 y otra a las 10:30 tienen inicios distintos y aun así se pisan. Por
eso se comparan **rangos** con `&&`. El servicio intenta el `INSERT`, captura
la violación y responde `409` con los cupos más cercanos.

**El evento viaja en la misma transacción que la cita (outbox).** Si la
reserva publicara directo en Kafka, un fallo entre las dos operaciones dejaría
una cita confirmada de la que nadie se entera. Con outbox se puede apagar
Kafka, seguir reservando y publicar todo al reconectar.

**Todo instante en UTC.** Columnas `timestamptz`; los horarios de atención se
guardan en hora local (`TIME` + `dia_semana`) y se resuelven a UTC con
`negocio.zona_horaria` (por defecto `America/Bogota`).

**Idempotencia.** `POST /citas` exige `Idempotency-Key`; reenviarla devuelve
la cita original. Los consumidores deduplican por `eventoId` contra
`evento_procesado`, porque Kafka entrega al menos una vez.

**Orden por profesional.** La clave de partición es siempre `profesionalId`,
así la cancelación nunca se procesa antes que la reserva que la origina.

**Mensajes de error en español y accionables**, con el formato
`{ "codigo": "...", "mensaje": "..." }`. Nunca un stacktrace.

### 5.2 Componentes

| Componente | Puerto | Responsabilidad | Líneas de código (main) | Pruebas |
|---|---|---|---|---|
| `agenda-service` | 8081 | Dominio: servicios, profesionales, horarios, bloqueos, disponibilidad, reserva, gestión por token, outbox | 3 201 | 69 |
| `notificaciones-service` | 8082 | Consume eventos, confirma, cancela, programa y envía el recordatorio, DLQ | 974 | 7 |
| `analitica-service` | 8083 | Consume eventos, ocupación e inasistencia, `topServicios` | 659 | 9 |
| `gateway-graphql` | 8080 | Compone el panel; 5 queries y 8 mutaciones; límites y errores GraphQL | 1 479 | 30 |
| `web/` | pública | PWA de reserva y panel de recepción, instalable | 2 433 (HTML/CSS/JS) | `web/pruebas` |
| PostgreSQL 16 | 5432 | Esquemas `agenda`, `notificaciones`, `analitica` | 3 migraciones Flyway | — |
| Kafka 3.7 (KRaft) | 29092 | 3 tópicos de negocio y `citas.dlq` | — | — |

### 5.3 Modelo de datos

14 tablas en tres esquemas, creadas por Flyway (`db/V1`, `V2`, `V3`):

| Esquema | Tablas |
|---|---|
| `agenda` | `negocio`, `servicio`, `profesional`, `servicio_profesional`, `horario_atencion`, `bloqueo`, `cita`, `token_gestion`, `outbox` |
| `notificaciones` | `programacion`, `evento_procesado`, `mensaje_fallido` |
| `analitica` | `metrica_diaria`, `evento_procesado` |

Nunca se usa `ddl-auto: update`.

### 5.4 Contratos

| Contrato | Archivo | Contenido |
|---|---|---|
| REST | `docs/openapi/agenda-service.yaml` | 15 rutas: públicas por `slug`, administrativas por `X-Negocio-Id` |
| GraphQL | `docs/graphql/schema.graphqls` | 5 queries, 8 mutaciones, `panelRecepcion` |
| Eventos | `docs/eventos/CONTRATO-EVENTOS.md` | 3 tópicos más DLQ; todos con `eventoId`, `version`, `negocioId`, `correlationId` |

El gateway añade límites de seguridad básica: profundidad máxima 15,
complejidad 300, 120 peticiones por minuto por IP (429 con `Retry-After`),
timeouts HTTP e introspección apagada en el perfil `prod`.

## 6. Interfaz

La PWA no usa frameworks ni paso de compilación. Tiene dos pantallas:

- **Reserva pública** (`web/index.html`): servicios, cupos libres, datos del
  cliente con validación en español y confirmación. **La reserva exige
  conexión**: no hay cola offline, porque dos personas reservando el mismo
  cupo sin red generarían un conflicto imposible de resolver al sincronizar.
- **Panel de recepción** (`web/panel.html`): agenda del día, métricas del mes
  y alerta de mensajes caídos, todo con una sola consulta GraphQL.

El service worker cachea la interfaz y `manifest.json` la hace instalable.

## 7. Cómo ejecutarlo

Requisitos: Docker con Compose. No hace falta Java ni Maven.

```bash
git clone https://github.com/brayantrujillodev/agenda-d.git
cd agenda-d

docker compose --profile core up -d            # Postgres + Kafka + agenda-service
docker compose --profile full up -d --build    # los cuatro servicios + consolas
docker compose --profile full down -v          # bajar todo y empezar de cero
```

Alguien del equipo tiene 8 GB de RAM: `core` es el perfil por defecto.

| Servicio | URL |
|---|---|
| API pública | <http://localhost:8081> |
| Swagger | <http://localhost:8081/swagger-ui.html> |
| GraphiQL | <http://localhost:8080/graphiql> |
| Analítica | <http://localhost:8083/v1/metricas> |
| Consola de Kafka | <http://localhost:8090> |
| pgAdmin (sin login) | <http://localhost:5050> |
| PostgreSQL | `localhost:5432` · `agendad` / `agendad` |

Para regenerar la evidencia con el stack arriba:

```bash
node docs/evidencias/flujo-p3.mjs                                  # 31 verificaciones
npx newman run docs/postman/AGENDA-D.postman_collection.json       # 51 peticiones
```

## 8. Pruebas y evidencias

### 8.1 Verificación final del arranque (2026-11-18)

Se ejecutó `docker compose --profile full down -v` seguido de
`docker compose --profile full up -d --build` sobre una máquina Windows 11 con
Docker 29.8. Resultado:

| Contenedor | Estado |
|---|---|
| `agd-postgres` | healthy |
| `agd-kafka` | healthy |
| `agd-agenda` | healthy |
| `agd-analitica` | healthy |
| `agd-gateway` | healthy |
| `agd-notificaciones` | up |
| `agd-kafka-ui`, `agd-pgadmin` | up |

`/actuator/health` respondió 200 en los puertos 8080, 8081, 8082 y 8083.

### 8.2 Flujo completo contra el stack real: 31 de 31

`docs/evidencias/flujo-p3.mjs` recorre el circuito completo y escribe cada
petición con su respuesta sin editar en
[`docs/evidencias/P3-ENTREGA-1.md`](evidencias/P3-ENTREGA-1.md). Se regeneró el
2026-11-18 y cubre:

- Reserva por REST público y confirmación consumida por notificaciones.
- `panelRecepcion` compone agenda y métricas; el celular sale enmascarado.
- Las 8 mutaciones: crear, actualizar y desactivar servicio; crear profesional;
  definir horario; crear bloqueo (con `CITAS_EN_RANGO` si hay citas); cancelar
  cita; registrar asistencia.
- Errores de dominio en español, sin `X-Negocio-Id`, negocio inexistente, input
  inválido e ID mal formado.
- Con `analitica-service` caído, la agenda sale igual y `metricasDelMes` es
  `null` con `SERVICIO_NO_DISPONIBLE` (respuesta parcial, sin datos inventados).
- Una consulta de 19 niveles se corta antes de ejecutarse.
- El límite de peticiones responde 429 con formato de error GraphQL.

### 8.3 Colección de Postman: 51 peticiones, 77 aserciones, 0 fallos

Corrida con Newman el 2026-11-18. Salida completa en
[`docs/evidencias/newman-coleccion.txt`](evidencias/newman-coleccion.txt).

| | Ejecutadas | Fallidas |
|---|---|---|
| Peticiones | 51 | 0 |
| Scripts de prueba | 51 | 0 |
| Aserciones | 77 | 0 |

Duración total 5,8 s; tiempo medio de respuesta 34 ms (mínimo 7 ms, máximo 185 ms).

### 8.4 Pruebas automatizadas: 115

| Servicio | Pruebas | Qué cubren |
|---|---|---|
| `agenda-service` | 69 | Administración, configuración, gestión, outbox, disponibilidad, validación de reserva, reserva, validador de cupo, **concurrencia** y **separación entre negocios** |
| `notificaciones-service` | 7 | Consumo con deduplicación, reintentos, recordatorio |
| `analitica-service` | 9 | Consumo y proyección, controlador de métricas |
| `gateway-graphql` | 30 | Cliente HTTP (regresión de `PATCH`), límite de peticiones, contrato GraphQL |

**Prueba de concurrencia** (`ReservaConcurrenciaTest`, obligatoria): 100 hilos
reales contra un Postgres real intentan reservar el mismo cupo; el resultado
esperado y obtenido en el CI de GitHub (Linux) es **1 éxito y 99 conflictos**
por `cita_sin_solape`. **Prueba de separación entre negocios**
(`SeparacionEntreNegociosTest`): 7 casos contra Postgres real.

**Nota honesta sobre el entorno local.** En la máquina de desarrollo
(Windows con Docker Desktop 29) las dos pruebas que usan Testcontainers 1.20.4
no pueden iniciar: Testcontainers recibe un `400` vacío del daemon al
consultar `/info`. Es una incompatibilidad entre esa versión y Docker Desktop,
ajena al código, y ya figuraba en el README. Por eso esas pruebas se
verifican en el **CI de GitHub Actions** (workflow `build`, en verde en
`feat/gateway-graphql-p3`). Mejora propuesta: subir Testcontainers a una
versión compatible con Docker Desktop reciente.

### 8.5 Calidad de los logs

En un arranque limpio no aparece ningún `ERROR` ni `WARN` real. Se encontraron
y corrigieron dos fuentes de ruido en `docker-compose.yml`:

| Origen | Síntoma | Corrección |
|---|---|---|
| Consumidores Kafka | `DisconnectException` impreso por `FetchSessionHandler` en cada reconexión normal | Nivel `WARN` para `FetchSessionHandler` en `notificaciones-service` y `analitica-service` |
| `kafka-ui` | Cientos de líneas `DEBUG` del planificador de métricas | `LOGGING_LEVEL_ROOT=INFO` |

Los `ERROR` que quedaban en contenedores antiguos eran historia de volúmenes
reutilizados durante semanas (un conflicto `cita_sin_solape` de una prueba
manual y desconexiones al suspender Docker Desktop), no fallos del sistema.

### 8.6 Lint y CI

`markdownlint`, `yamllint`, `shellcheck` y `sqlfluff` corren en cada push. En
esta entrega `markdownlint` (11 archivos) y `yamllint` quedaron en 0 errores y
0 advertencias; `.postman/` se excluye de `yamllint` por ser generado.

## 9. Errores reales encontrados al probar contra infraestructura real

| Hallazgo | Cómo apareció | Corrección |
|---|---|---|
| Deadlock de Postgres en vez de la violación limpia del `EXCLUDE` bajo contención extrema | Prueba de 100 hilos | Reintento acotado (hasta 8) con espera aleatoria en `ReservaService` |
| El cálculo de «cupos más cercanos» del `409` también podía hacer deadlock | Prueba de concurrencia en CI | El `409` responde sin alternativas en ese caso, en vez de un error genérico |
| `notificaciones.programacion.canal` era `NOT NULL`; el recordatorio es la primera fila pendiente sin canal | La primera reserva con recordatorio cayó en `citas.dlq` | Migración `V3__canal_opcional_mientras_pendiente.sql` |
| `registrarAsistencia` fallaba siempre: `HttpURLConnection` no admite `PATCH` | Flujo completo contra Docker | Cliente HTTP del JDK, con prueba de regresión |
| `v1.0.1` devolvía datos de ejemplo en `panelRecepcion` aunque el backend respondiera | Verificación a mano | Corregido en `v1.0.2`; el tag viejo se deja por trazabilidad |
| El gateway inventaba datos si un servicio caía; negocio y profesional fijos en código | Auditoría de P3 | Errores GraphQL `SERVICIO_NO_DISPONIBLE`, negocio leído de `GET /v1/negocio` |
| Dos servicios mapeaban el puerto equivocado en el Compose | Revisión del arranque | Puertos corregidos y healthchecks |
| 130 conexiones simultáneas desde el script de evidencia provocaban `ECONNRESET` en Windows | Regeneración de evidencia | Envío en serie en `flujo-p3.mjs` |

## 10. Resultados frente a los objetivos

| Objetivo | Resultado |
|---|---|
| 1 · Sin solapes bajo concurrencia | Cumplido. Lo impide el motor; 100 hilos, 1 éxito |
| 2 · Reserva desacoplada | Cumplido. Outbox en la misma transacción |
| 3 · Tolerancia a fallos de consumidores | Cumplido. Deduplicación, reintentos y DLQ visibles |
| 4 · Recordatorio de 24 h con recuperación | Cumplido |
| 5 · Panel en una consulta | Cumplido. 31/31 |
| 6 · Ocupación e inasistencia | Cumplido, con una simplificación documentada: la ocupación usa una jornada configurable en lugar de leer el horario real del otro esquema |
| 7 · PWA instalable | Cumplido. Reserva exige conexión por diseño |
| 8 · Aislamiento entre negocios | Cumplido. 7 casos en Postgres real |
| 9 · Reproducibilidad | Cumplido. Un comando por perfil, verificado desde cero |

## 11. Costos

**Costo de infraestructura: \$0.** Todo corre localmente con Docker Compose
(Postgres, Kafka, consolas). La publicación usa niveles gratuitos de GitHub:
Pages para la PWA, GHCR para las imágenes, Actions para el CI. No se
contrataron servidores ni licencias.

**Costo de esfuerzo.** El equipo planificó unas 20 horas semanales entre los
cuatro integrantes durante 15 semanas:

| Concepto | Cantidad |
|---|---|
| Horas planificadas por semana (equipo) | 20 h |
| Semanas del plan | 15 |
| **Horas totales estimadas** | **300 h** |

Para valorarlo en dinero se multiplican las horas por la tarifa que defina el
equipo: `costo de esfuerzo = 300 h × tarifa por hora`. Esa tarifa no está
registrada en el repositorio, por eso no se fija aquí un valor inventado. Un
despliegue en servidor propio (`docker-compose.prod.yml`) queda preparado pero
apagado; su costo mensual dependería del proveedor elegido.

## 12. Limitaciones y trabajo futuro

- **Sin autenticación**, por acuerdo con el docente. Cualquiera que conozca un
  `X-Negocio-Id` actúa sobre ese negocio; para producción requeriría
  identidad y roles.
- **Sin backend público compartido.** La PWA en Pages reserva contra un
  `agenda-service` local; el despliegue en servidor queda listo pero apagado.
- **`analitica-service` sin imagen propia** en GHCR (es posterior al tag
  `v1.0.2`).
- **Ocupación aproximada**: usa una jornada configurable, no el horario real.
- **Testcontainers 1.20.4** no arranca contra Docker Desktop 29 en local;
  se verifica en CI. Actualizar la dependencia lo resolvería.
- **Canales de notificación**: hoy el aviso se registra en la base de datos (adaptador `RegistroCanal`, sin costo); un
  canal real (SMS, WhatsApp o correo) sería el siguiente paso.

## 13. Conclusiones

1. **Delegar la exclusión en la base de datos fue la decisión correcta.** Una
   restricción `EXCLUDE` sobre rangos cierra el problema de raíz y la prueba
   de 100 hilos lo demuestra: 1 reserva y 99 conflictos limpios, sin
   validaciones en Java que puedan quedar desfasadas.
2. **El outbox permite que reservar no dependa de Kafka.** La reserva responde
   igual con el bus caído y los eventos se publican al volver, con
   deduplicación en los consumidores para tolerar entregas repetidas.
3. **Probar contra infraestructura real fue lo más rentable del proyecto.**
   Los defectos más serios (deadlocks, una columna `NOT NULL`, un verbo HTTP no
   soportado, datos inventados por el gateway) solo aparecieron con Postgres y
   Kafka reales y carga real.
4. **Los contratos primero evitaron integraciones a ciegas.** Con el OpenAPI,
   el esquema GraphQL y el contrato de eventos acordados, cuatro personas
   trabajaron en servicios distintos sin bloquearse.
5. **Un fallo parcial se informa, no se disfraza.** Si un servicio cae, el
   gateway devuelve un error en español en ese campo y deja pasar el resto, en
   lugar de inventar datos.
6. **Se cumplió el alcance acordado con el docente:** circuito completo
   primero y robustez después, con las tres fases cerradas y la arquitectura
   reproducible desde cero con un solo comando.

## 14. Anexos

| Documento | Ruta |
|---|---|
| README del proyecto | [`README.md`](../README.md) |
| Contrato REST | [`docs/openapi/agenda-service.yaml`](openapi/agenda-service.yaml) |
| Contrato GraphQL | [`docs/graphql/schema.graphqls`](graphql/schema.graphqls) |
| Contrato de eventos | [`docs/eventos/CONTRATO-EVENTOS.md`](eventos/CONTRATO-EVENTOS.md) |
| Evidencia del flujo (31/31) | [`docs/evidencias/P3-ENTREGA-1.md`](evidencias/P3-ENTREGA-1.md) |
| Salida de Newman | [`docs/evidencias/newman-coleccion.txt`](evidencias/newman-coleccion.txt) |
| Colección de Postman | [`docs/postman/AGENDA-D.postman_collection.json`](postman/AGENDA-D.postman_collection.json) |
| Pasos del flujo mínimo | [`docs/CONSTRUCCION.md`](CONSTRUCCION.md) |
| Backlog | [`docs/TAREAS.md`](TAREAS.md) |
| Guion de sustentación | [`docs/GUION-SUSTENTACION.md`](GUION-SUSTENTACION.md) |
| Equipo | [`docs/EQUIPO.md`](EQUIPO.md) |
