# Construcción del flujo mínimo

El docente pidió esto textualmente:

> *"prioricen el flujo mínimo que ustedes mismos definieron: REST → persistencia/outbox
> → Kafka → consumidor → GraphQL, antes de incorporar DLQ, analítica y demás
> mecanismos de robustez."*

Cinco pasos. **No se pasa al siguiente hasta que el anterior funcione de verdad.**
Cada paso es una rama, un PR y una revisión de alguien de la otra pareja.

Antes de empezar, siempre:

```bash
docker compose --profile infra up -d
```

---

## Paso 1 · agenda-service arranca y responde

**Quién:** Luis
**Rama:** `feature/1-esqueleto-agenda`
**Termina cuando:** `curl localhost:8081/actuator/health` devuelve `{"status":"UP"}`
y Swagger abre en `localhost:8081/swagger-ui.html`.

Prompt para Claude Code:

> Lee CLAUDE.md. Crea el proyecto `agenda-service`: Spring Boot 3.3, Java 21,
> Maven, con su propio pom.xml (sin proyecto padre). Dependencias: web, data-jpa,
> postgresql, flyway, validation, actuator, springdoc-openapi y spring-kafka.
> Puerto 8081. Mueve `db/V1__esquema_inicial.sql` a
> `src/main/resources/db/migration/`. Añade un Dockerfile multi-stage.
> No escribas todavía ningún controlador ni entidad: solo que arranque, conecte
> a Postgres y aplique la migración.

Al terminar, `docker compose --profile core up -d --build` debe funcionar. Si
funciona, actualiza la tabla de estado del README.

---

## Paso 2 · REST + persistencia

**Quién:** Brayan y Andrés
**Rama:** `feature/2-reservar-cita`
**Termina cuando:** puedes consultar cupos y reservar con `curl`, y la cita
queda en la tabla.

Prompt:

> Lee CLAUDE.md y docs/openapi/agenda-service.yaml. Implementa en agenda-service:
> las entidades JPA del esquema agenda, el cálculo de disponibilidad
> (`GET /v1/publico/{slug}/disponibilidad`) cruzando horarios, bloqueos y citas
> existentes con conversión de hora local a UTC, y la reserva
> (`POST /v1/publico/{slug}/citas`).
>
> La reserva NO consulta disponibilidad antes de insertar: intenta el INSERT,
> captura la violación de la restricción `cita_sin_solape` y la traduce a un 409
> con los cupos más cercanos. Usa Instant, nunca LocalDateTime.

Prueba manual obligatoria:

```bash
# 1. Ver cupos
curl "localhost:8081/v1/publico/barberia-el-corte/disponibilidad?servicioId=22222222-2222-2222-2222-222222222222&fecha=2026-09-01"

# 2. Reservar
curl -X POST "localhost:8081/v1/publico/barberia-el-corte/citas" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" \
  -d '{"servicioId":"22222222-2222-2222-2222-222222222222",
       "profesionalId":"33333333-3333-3333-3333-333333333333",
       "inicio":"2026-09-01T15:00:00Z","clienteNombre":"Juan Perez",
       "clienteCelular":"3001234567"}'

# 3. Repetir el paso 2 con OTRA Idempotency-Key → debe dar 409
```

Si el paso 3 devuelve 201, algo está mal: probablemente están validando en
Java en vez de dejar que la base rechace.

---

## Paso 3 · Outbox

**Quién:** Andrés
**Rama:** `feature/3-outbox`
**Termina cuando:** reservar deja una fila en `agenda.outbox` con `enviado_en`
en nulo, y el relay la publica en Kafka y la marca.

Prompt:

> Lee CLAUDE.md y docs/eventos/CONTRATO-EVENTOS.md. En agenda-service, el caso
> de uso de reserva debe insertar en `agenda.cita` y en `agenda.outbox` dentro
> de la MISMA transacción. Nunca llames a kafkaTemplate desde el caso de uso.
>
> Añade un relay con `@Scheduled` cada 2 segundos que lea las filas con
> `enviado_en IS NULL`, publique en el tópico `citas.reservadas` con clave de
> partición `profesionalId`, y marque la fila como enviada. Sin reintentos ni
> DLQ todavía.

**La prueba que vale:**

```bash
docker compose stop kafka       # apagas el bus
# reservas 2 citas por curl → responden 201 normalmente
docker compose start kafka      # lo prendes
# esperas unos segundos y los 2 eventos aparecen publicados
```

Esa es la demostración de la sustentación. Ténganla ensayada.

---

## Paso 4 · El consumidor

**Quién:** Johan
**Rama:** `feature/4-notificaciones`
**Termina cuando:** reservar por REST deja un registro en la tabla de envíos,
sin que nadie llame a `notificaciones-service`.

Prompt:

> Lee CLAUDE.md y docs/eventos/CONTRATO-EVENTOS.md. Crea el proyecto
> `notificaciones-service` (Spring Boot 3.3, Java 21, puerto 8082, pom propio,
> Dockerfile) que consuma el tópico `citas.reservadas`.
>
> Antes de procesar, deduplica por `eventoId` contra la tabla
> `notificaciones.evento_procesado`. Define una interfaz `CanalNotificacion`
> con una implementación `RegistroCanal` que guarda el mensaje en
> `notificaciones.programacion` en vez de enviarlo. Sin recordatorios todavía.

Aquí es donde se ve el desacople: `agenda-service` no sabe que este servicio
existe, y aun así el mensaje llega.

---

## Paso 5 · El gateway GraphQL

**Quién:** Luis
**Rama:** `feature/5-gateway`
**Termina cuando:** una consulta en `localhost:8080/graphiql` devuelve la
agenda del día y la configuración del negocio de una sola vez.

Prompt:

> Lee CLAUDE.md y docs/graphql/schema.graphqls. Crea el proyecto
> `gateway-graphql` (Spring Boot 3.3, Java 21, puerto 8080, spring-boot-starter-graphql,
> pom propio, Dockerfile). Carga el esquema y resuelve la consulta
> `panelRecepcion` llamando a agenda-service por REST con RestClient.
>
> El gateway no tiene base de datos ni lógica de negocio. Como analitica-service
> aún no existe, `metricasDelMes` devuelve ceros de momento; déjalo marcado con
> un TODO. Habilita /graphiql.

Consulta de prueba:

```graphql
{
  panelRecepcion(fecha: "2026-09-01") {
    citasDelDia { horaLocal clienteNombre servicio { nombre } }
    negocio { nombre servicios { nombre precio } }
  }
}
```

---

## Cuando los cinco pasos estén

**El circuito está cerrado.** Ahí ya tienen todos los componentes obligatorios
funcionando: REST con OpenAPI, persistencia, Kafka con productor y consumidor,
GraphQL y Docker Compose.

Actualicen la tabla de estado del README y avisen al docente. Solo entonces
empiecen con la Fase 3: DLQ, Testcontainers, recordatorios, analítica y panel.

---

## Reglas mientras construyen

1. **Un paso a la vez.** Si el paso 2 no funciona con curl, no se empieza el 3.
2. **Rama por paso, PR revisado por alguien de la otra pareja.**
3. **Actualicen el README** cada vez que un componente pase a funcionar. El
   docente ya nos llamó la atención por prometer lo que no existía.
4. **Si Claude Code propone validar el solapamiento en Java, dile que no** y
   recuérdale que lea CLAUDE.md.
