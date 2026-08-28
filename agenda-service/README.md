# agenda-service

Dominio principal de AGENDA-D: citas, cupos y configuración del negocio.
Productor de eventos vía outbox.

Java 21 · Spring Boot 3.3 · Maven (pom propio, sin proyecto padre) · puerto 8081.

## Estado

| Rama | Alcance |
|---|---|
| `feature/1-esqueleto-agenda` | Arranca, conecta a Postgres, Flyway resuelve el esquema. Sin entidades ni controladores. |
| `feature/2-entidades-disponibilidad` | *(esta)* Entidades JPA del esquema `agenda` + `GET /v1/publico/{slug}/servicios` y `GET /v1/publico/{slug}/disponibilidad` |
| `feature/3-...` | `POST /v1/publico/{slug}/citas` con el 409 del `EXCLUDE` + outbox |

### Endpoints de esta rama

- `GET /v1/publico/{slug}/servicios` — servicios activos del negocio
- `GET /v1/publico/{slug}/disponibilidad?servicioId=&fecha=&profesionalId=` — cupos
  libres: cruza horario de atención, bloqueos y citas; convierte hora local a UTC
  con `negocio.zona_horaria`. `fecha` en hora local del negocio (`AAAA-MM-DD`).

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
