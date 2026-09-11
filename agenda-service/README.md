# agenda-service

Dominio principal de AGENDA-D: citas, cupos y configuración del negocio.
Productor de eventos vía outbox.

Java 21 · Spring Boot 3.3 · Maven (pom propio, sin proyecto padre) · puerto 8081.

## Estado

| Rama | Alcance |
|---|---|
| `feature/1-esqueleto-agenda` | Arranca, conecta a Postgres, Flyway resuelve el esquema. Sin entidades ni controladores. |
| `feature/2-entidades-disponibilidad` | Entidades JPA del esquema `agenda` + `GET /v1/publico/{slug}/servicios` y `GET /v1/publico/{slug}/disponibilidad` |
| `feature/3-outbox` | `POST /v1/publico/{slug}/citas` (409 del `EXCLUDE` + idempotencia) + tabla `outbox` en la misma transacción + relay `@Scheduled` que publica `citas.reservadas` |
| `feature/12-gestion-token` | *(esta)* `GET` y `DELETE /v1/gestion/{token}` — el cliente consulta y cancela su cita por token; cancelar publica `citas.canceladas` por outbox |

### Endpoints de esta rama

- `GET /v1/publico/{slug}/servicios` — servicios activos del negocio
- `GET /v1/publico/{slug}/disponibilidad?servicioId=&fecha=&profesionalId=` — cupos
  libres: cruza horario de atención, bloqueos y citas; convierte hora local a UTC
  con `negocio.zona_horaria`. `fecha` en hora local del negocio (`AAAA-MM-DD`).
- `POST /v1/publico/{slug}/citas` — reserva. Cabecera `Idempotency-Key` obligatoria
  (reenviarla devuelve la cita original). No consulta disponibilidad: intenta el
  `INSERT` y, si `cita_sin_solape` lo rechaza, responde `409 CUPO_OCUPADO` con los
  cupos más cercanos. En la misma transacción escribe la cita, su token de gestión
  y una fila en `agenda.outbox`.
- `GET /v1/gestion/{token}` — detalle de una sola cita por su token; el celular
  va enmascarado (`300****567`).
- `DELETE /v1/gestion/{token}?confirmar=true` — cancela la cita (libera el cupo:
  `cita_sin_solape` deja de contarla) y encola `citas.canceladas` en `agenda.outbox`.
  `confirmar=true` es obligatorio. Reprogramar no está en el contrato OpenAPI.

### Outbox → Kafka

`OutboxRelay` (`@Scheduled`, cada `AGENDAD_OUTBOX_INTERVALO_MS` ms, 2000 por
defecto) lee las filas de `agenda.outbox` con `enviado_en IS NULL`, las publica en
su tópico (`citas.reservadas`, `citas.canceladas`) con clave de partición
`profesionalId` y las marca como enviadas. Sin reintentos con espera ni DLQ
todavía (Fase 3).

Prueba de la sustentación:

```bash
docker compose stop kafka      # apagas el bus
# reservas 2 citas por curl -> responden 201 igual
docker compose start kafka     # lo prendes
# a los pocos segundos, los 2 eventos aparecen en el tópico
```

## Arrancar

Necesita Postgres y Kafka arriba:

```bash
docker compose --profile infra up -d          # desde la raíz del repo
docker compose --profile core up -d --build   # + agenda-service
curl localhost:8081/actuator/health           # -> {"status":"UP"}
```

Swagger: http://localhost:8081/swagger-ui.html

## Migración

`src/main/resources/db/migration/V1__esquema_inicial.sql` es copia del
`db/V1__esquema_inicial.sql` de la raíz (que el Compose monta en el initdb de
Postgres para el perfil `infra`). Flyway usa `baseline-on-migrate`: si el
esquema `agenda` ya está poblado por ese montaje, lo marca como versión 1 en
vez de reaplicarlo. Sobre una base vacía, aplica V1 completo.

Mantener ambas copias sincronizadas hasta que exista una V2; a partir de ahí,
Flyway es el único que toca el esquema.
