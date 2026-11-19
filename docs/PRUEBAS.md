# Pruebas y evidencias · AGENDA-D

Todo lo marcado como **Medido** se ejecutó el 2026-11-18 contra el sistema
real (contenedores de Docker, PostgreSQL y Kafka reales). Lo marcado como
**Simulado** o **Pendiente** no se presenta como resultado.

## 1. Cómo se clasifica cada resultado

| Tipo | Significado |
|---|---|
| **Medido** | Se ejecutó y se observó el resultado; hay evidencia en el repositorio. |
| **Simulado** | Se ejecutó con un sustituto del componente real (por ejemplo, el aviso se guarda en la base de datos en vez de enviarse por SMS). |
| **Pendiente** | No se ejecutó porque la función no existe o está fuera de alcance. |

## 2. Resumen de resultados medidos

| Prueba | Resultado | Evidencia |
|---|---|---|
| Pruebas automatizadas (JUnit, `mvn verify`, Testcontainers con Postgres y Kafka reales) | **116 de 116** | [`pruebas-maven.txt`](evidencias/pruebas-maven.txt) |
| Flujo completo de extremo a extremo (script propio) | **31 de 31** verificaciones | [`P3-ENTREGA-1.md`](evidencias/P3-ENTREGA-1.md) |
| Colección de Postman con Newman | **51 peticiones, 77 aserciones, 0 fallos** | [`newman-coleccion.txt`](evidencias/newman-coleccion.txt) |
| Arranque limpio de los contenedores | 8 de 8 arriba; 4 servicios `healthy` | [`estado-contenedores-y-bd.txt`](evidencias/estado-contenedores-y-bd.txt) |
| Integración continua en GitHub Actions (`build`, `lint`, `pages`) | Verde en `main` | Pestaña *Actions* del repositorio |

Pruebas automatizadas por servicio:

| Servicio | Pruebas | Clases |
|---|---|---|
| `agenda-service` | 70 | `AgendaServiceTest` (12), `SeparacionEntreNegociosTest` (7), `ConfiguracionServiceTest` (18), `GestionServiceTest` (7), `OutboxRelayTest` (4), `DisponibilidadServiceTest` (2), `ReservaRequestValidacionTest` (4), `ReservaConcurrenciaTest` (1), `ReservaServiceTest` (7), `ValidadorCupoTest` (8) |
| `notificaciones-service` | 7 | `CitasEventoConsumidorTest` (5), `RecordatorioSchedulerTest` (2) |
| `analitica-service` | 9 | `MetricasConsumidorTest` (5), `MetricasControllerTest` (4) |
| `gateway-graphql` | 30 | `ClientesHttpTest` (6), `LimitePeticionesFilterTest` (4), `GatewayGraphqlTest` (20) |

## 3. Matriz de pruebas

Estado: **Pasó** (el resultado obtenido coincide con el esperado), **Parcial**
o **Pendiente**.

### 3.1 Reserva y reglas de dominio (REST, PostgreSQL)

| ID | Caso | Resultado esperado | Resultado obtenido | Estado | Tipo | Evidencia |
|---|---|---|---|---|---|---|
| P-01 | Consultar los servicios del negocio público | 200 con la lista de servicios | 200, 2 servicios (Corte de cabello, Barba) | Pasó | Medido | Newman carpeta 1; captura [01](evidencias/capturas/01-pwa-servicios.jpg) |
| P-02 | Consultar disponibilidad de un día | Cupos libres, solo futuros | 200; las horas ya pasadas no se ofrecen | Pasó | Medido | Newman; captura [02](evidencias/capturas/02-pwa-cupos-futuros.jpg); `ValidadorCupoTest` |
| P-03 | Reservar un cupo libre con datos válidos | 201 y estado `CONFIRMADA` | 201 `CONFIRMADA` con enlace de gestión | Pasó | Medido | Newman; capturas [03](evidencias/capturas/03-pwa-datos-cliente.jpg), [04](evidencias/capturas/04-pwa-cita-confirmada.jpg) |
| P-04 | Reenviar la misma `Idempotency-Key` | Devuelve la cita original, no crea otra | Misma cita | Pasó | Medido | Newman «Reenviar la MISMA Idempotency-Key» |
| P-05 | Reservar el mismo cupo con otra clave | 409 con mensaje en español y cupos cercanos | 409 con alternativas | Pasó | Medido | Newman «Mismo cupo con otra clave → 409» |
| P-06 | 100 hilos reservando el mismo cupo a la vez | 1 éxito y 99 conflictos; ninguna cita solapada | 1 éxito, 99 conflictos | Pasó | Medido | `ReservaConcurrenciaTest` en [`pruebas-maven.txt`](evidencias/pruebas-maven.txt) |
| P-07 | Cita de 60 min a las 10:00 y otra a las 10:30 del mismo profesional | La segunda se rechaza (los rangos se solapan) | `INSERT` de 15:00–16:00 UTC y luego 15:30–16:30 UTC del mismo profesional, ejecutados por `psql` en una transacción revertida (2026-11-19): el segundo falla con `conflicting key value violates exclusion constraint "cita_sin_solape"` | Pasó | Medido | [`estado-contenedores-y-bd.txt`](evidencias/estado-contenedores-y-bd.txt) |
| P-08 | Datos inválidos: nombre con números, celular que no empieza por 3, clave que no es UUID, campos extra | 400 con mensaje en español | 400 en cada caso | Pasó | Medido | Newman carpeta 2; `ReservaRequestValidacionTest` |
| P-09 | Cupo fuera de horario, no alineado a la grilla, dentro de un bloqueo, pasado o más allá de 60 días | 400 con mensaje en español | Rechazado en cada caso | Pasó | Medido | `ValidadorCupoTest` (8 casos) |
| P-10 | Un negocio intenta ver o modificar datos de otro (agenda, estado, cancelación, configuración, bloqueos) | No encuentra nada ajeno y no toca nada; con el negocio correcto sí funciona | Correcto en los 7 casos | Pasó | Medido | `SeparacionEntreNegociosTest` (7, Postgres real) |
| P-11 | Consultar y cancelar la cita con el token de gestión | 200 al consultar; 204 al cancelar | Correcto; 400 si ya tiene resultado | Pasó | Medido | Newman carpeta 1; `GestionServiceTest` |
| P-12 | Crear servicio, profesional, horario y bloqueo; bloqueo con citas dentro | Se crean; bloqueo con citas responde `CITAS_EN_RANGO` | Correcto | Pasó | Medido | `ConfiguracionServiceTest` (18); flujo §5 «Mutaciones» |

### 3.2 Mensajería (Kafka, outbox, consumidores)

| ID | Caso | Resultado esperado | Resultado obtenido | Estado | Tipo | Evidencia |
|---|---|---|---|---|---|---|
| P-13 | Reservar escribe el evento en la misma transacción | `cita` y `outbox` aparecen juntas; el relay las publica | 13 eventos enviados, 0 pendientes | Pasó | Medido | [`estado-contenedores-y-bd.txt`](evidencias/estado-contenedores-y-bd.txt); `OutboxRelayTest` |
| P-14 | Los tópicos existen con 3 particiones | `citas.reservadas`, `.canceladas`, `.estado`, `.dlq` | Los 4, con 3 particiones; DLQ con 0 mensajes | Pasó | Medido | Captura [07](evidencias/capturas/07-kafka-topicos.jpg) |
| P-15 | Un mensaje repetido no se procesa dos veces | Se deduplica por `eventoId` | Procesado una sola vez | Pasó | Medido | `CitasEventoConsumidorTest`, `MetricasConsumidorTest` |
| P-16 | Mensaje que falla | 3 reintentos (1 s, 4 s, 16 s) y envío a `citas.dlq` | Mensaje no JSON publicado en `citas.reservadas` (2026-11-19): `notificaciones-service` registró `agotó los reintentos y quedó en citas.dlq: failed to deserialize`. Los tiempos exactos de espera (1 s, 4 s, 16 s) no se midieron en esta repetición | Pasó (envío a DLQ); esperas no medidas | Medido | Captura [07](evidencias/capturas/07-kafka-topicos.jpg); `docs/TAREAS.md` (entrega 2 del segundo periodo) |
| P-17 | Cancelar desde el panel publica `citas.canceladas` y el consumidor lo recibe | Evento consumido por `notificaciones-service` | Consumido | Pasó | Medido | Flujo, verificación «notificaciones-service consumió la cancelación hecha desde el panel» |
| P-18 | Recordatorio 24 h antes | Los recordatorios vencidos se envían leyendo las filas `PENDIENTE` de la base (mecanismo de recuperación tras reinicio); cancelar la cita cancela su recordatorio | Se envían todos los vencidos; no llama al canal si no hay; cancelar cancela el recordatorio pendiente | Parcial | Medido (prueba automática) | `RecordatorioSchedulerTest` (2), `CitasEventoConsumidorTest`. El reinicio real del contenedor con un recordatorio pendiente no se midió. |
| P-19 | Envío real del aviso por SMS, WhatsApp o correo | Mensaje recibido por el cliente | El aviso se guarda en la base de datos (`RegistroCanal`) | Pendiente | **Simulado** | [`ARQUITECTURA.md`](ARQUITECTURA.md) §6 |

### 3.3 GraphQL y analítica

| ID | Caso | Resultado esperado | Resultado obtenido | Estado | Tipo | Evidencia |
|---|---|---|---|---|---|---|
| P-20 | `panelRecepcion` en una consulta | Agenda del día y métricas del mes | Sin errores; negocio leído de `agenda-service` | Pasó | Medido | Flujo; captura [05](evidencias/capturas/05-panel-recepcion.jpg) |
| P-21 | Celular enmascarado en el panel | Solo los últimos dígitos visibles | Enmascarado | Pasó | Medido | Flujo |
| P-22 | Las 8 mutaciones | Cada una funciona; `registrarAsistencia` sobre cita cancelada da error de dominio | Correcto | Pasó | Medido | Flujo §5; `GatewayGraphqlTest` (20) |
| P-23 | Errores: sin `X-Negocio-Id`, negocio inexistente, input inválido, ID mal formado | `DATOS_INVALIDOS` o `NO_ENCONTRADO` en español | Correcto | Pasó | Medido | Flujo §6 |
| P-24 | `analitica-service` caído | La agenda sale; `metricasDelMes` es `null` con `SERVICIO_NO_DISPONIBLE`; sin datos inventados | Correcto | Pasó | Medido | Flujo §6.5 |
| P-25 | Consulta de 19 niveles de profundidad | Se rechaza antes de ejecutarse (límite 15) | Rechazada | Pasó | Medido | Flujo §7.1 |
| P-26 | Más de 120 peticiones por minuto desde una IP | 429 con formato de error GraphQL | 429 | Pasó | Medido | Flujo §7.3; `LimitePeticionesFilterTest` |
| P-27 | La reserva se refleja en las métricas | Ocupación y total del mes suben | «Citas del mes: 1», ocupación 10 % | Pasó | Medido | Captura [05](evidencias/capturas/05-panel-recepcion.jpg) |

### 3.4 PWA, operación y calidad

| ID | Caso | Resultado esperado | Resultado obtenido | Estado | Tipo | Evidencia |
|---|---|---|---|---|---|---|
| P-28 | Reserva completa en el navegador, sin cuenta | Del servicio a «Cita confirmada» | Completada | Pasó | Medido | Capturas [01](evidencias/capturas/01-pwa-servicios.jpg) a [04](evidencias/capturas/04-pwa-cita-confirmada.jpg) |
| P-29 | Nombre con la letra «s» (Carlos Ramirez) | Se acepta | **Fallaba**: se rechazaba; se corrigió la expresión regular y se repitió la reserva | Pasó tras corrección | Medido | Captura [08](evidencias/capturas/08-defecto-validacion-nombre-antes-del-arreglo.jpg) (antes); [04](evidencias/capturas/04-pwa-cita-confirmada.jpg) (después) |
| P-30 | La PWA se ofrece como instalable | Aviso de instalación; `manifest.json` y service worker | Aviso «Instalar» visible; no se midió con Lighthouse | Parcial | Medido (observación) | Capturas [01](evidencias/capturas/01-pwa-servicios.jpg) a [04](evidencias/capturas/04-pwa-cita-confirmada.jpg) |
| P-31 | Documentación de la API | Swagger con todas las rutas | Swagger UI operativo | Pasó | Medido | Captura [06](evidencias/capturas/06-swagger-agenda-service.jpg) |
| P-32 | Arranque limpio sin errores en los logs | 0 `ERROR` o `WARN` reales | 0 | Pasó | Medido | Informe §8.5 |
| P-33 | Reservar sin conexión | Se bloquea con un aviso; no hay cola offline | El código muestra «Sin conexión…» y no encola reservas | Pasó (por diseño) | Revisión de código | `web/app.js` (`estaOffline`, `SinRed`) |
| P-34 | Rendimiento y carga sostenida | Tiempos bajo carga definidos | Solo se midió la prueba de 100 hilos; el promedio de Newman fue 19 ms con un usuario | Pendiente | — | Sin prueba de carga formal |
| P-35 | Autenticación, roles, sesiones | — | Fuera de alcance por acuerdo con el docente | Pendiente | — | `README.md` |
| P-36 | Modo demostración de la PWA sin backend (`useMock: true`) | Responde con datos de ejemplo | Disponible para demostración; no se usa en la entrega | No evaluado | **Simulado** | `web/mock.js` |
| P-37 | PWA servida por nginx desde el Compose (`http://localhost:5173`) | `index.html` y `sw.js` responden 200 | Desde el ZIP extraído en carpeta nueva, `docker compose --profile full up -d --build` levantó todo; `agd-web` quedó `healthy` y `index.html` y `sw.js` respondieron 200 en el puerto 5173 (2026-11-19). Gateway, agenda, notificaciones y analítica respondieron 200 en `/actuator/health` (8080–8083) | Pasó | Medido | Repetir con `docker compose --profile full up -d --build` y abrir <http://localhost:5173/index.html> |

## 4. Capturas

Están en [`docs/evidencias/capturas/`](evidencias/capturas/) y se incrustan
en el informe final. No hay captura de GraphiQL porque su página carga scripts
desde `unpkg.com`, que respondió 503 durante la prueba; la evidencia de
GraphQL está en texto en [`P3-ENTREGA-1.md`](evidencias/P3-ENTREGA-1.md). Tampoco
hay captura de pgAdmin; la consulta equivalente está en
[`estado-contenedores-y-bd.txt`](evidencias/estado-contenedores-y-bd.txt).

## 5. Cómo repetir las pruebas

Ver [`MANUAL-TECNICO.md`](MANUAL-TECNICO.md) §5.
