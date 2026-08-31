# notificaciones-service

Consumidor Kafka de AGENDA-D. Escucha `citas.reservadas` y `citas.canceladas`
y deja constancia del aviso en `notificaciones.programacion`, sin enviarlo
por ningún canal real todavía (adaptador `REGISTRO`).

**Alcance de esta versión (issue #10, Fase 2):**
- [x] Consume `citas.reservadas` y `citas.canceladas`
- [x] Deduplica por `eventoId` contra `notificaciones.evento_procesado`
- [x] Interfaz `CanalNotificacion` con la implementación `RegistroCanal`
- [ ] Recordatorio de 24 h — **Fase 3, issue #18.** No está implementado a propósito.

## Arranque

Necesita Postgres y Kafka arriba (perfil `infra` ya funciona hoy):

```bash
docker compose --profile infra up -d
```

Luego, localmente (sin esperar a que `agenda-service` exista):

```bash
cd notificaciones-service
mvn spring-boot:run
```

Por defecto se conecta a `localhost:5432` (Postgres) y `localhost:29092`
(listener externo de Kafka), que son justo los puertos que expone
`docker-compose.yml` para el equipo. Dentro de Docker Compose (perfil
`full`) usa en cambio `SPRING_DATASOURCE_URL`/`SPRING_KAFKA_BOOTSTRAP_SERVERS`,
ya definidos en el servicio `notificaciones-service` del compose.

`/actuator/health` responde en `http://localhost:8082/actuator/health`.

## Probar sin que `agenda-service` exista todavía

Este es justo el punto de la Fase 2: el consumidor no sabe ni le importa
quién publica. Se puede probar publicando el evento a mano
(docs/eventos/CONTRATO-EVENTOS.md tiene el comando base):

```bash
docker exec -i agd-kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic citas.reservadas
```

Y pegar una línea (el productor la lee hasta el `\n`, tiene que ir en una
sola línea):

```json
{"eventoId":"6f2a91c4-8b3d-4e21-9f77-2c1a5b8e0d33","version":1,"ocurridoEn":"2026-09-01T14:32:07Z","correlationId":"corr-demo-1","negocioId":"11111111-1111-1111-1111-111111111111","citaId":"44444444-4444-4444-4444-444444444444","profesionalId":"33333333-3333-3333-3333-333333333333","profesionalNombre":"Laura","servicioId":"22222222-2222-2222-2222-222222222222","servicioNombre":"Corte de cabello","inicio":"2026-09-01T15:00:00Z","fin":"2026-09-01T16:00:00Z","zonaHoraria":"America/Bogota","cliente":{"nombre":"Juan Perez","celular":"3001234567"},"tokenGestion":"tok-demo-1"}
```

Verificar el resultado:

```bash
docker exec -it agd-postgres psql -U agendad -d agendad -c \
  "SELECT tipo, canal, destinatario, estado, cuerpo FROM notificaciones.programacion ORDER BY enviado_en DESC LIMIT 5;"

docker exec -it agd-postgres psql -U agendad -d agendad -c \
  "SELECT * FROM notificaciones.evento_procesado ORDER BY procesado_en DESC LIMIT 5;"
```

Pegar la **misma línea otra vez**: como el `eventoId` ya está en
`evento_procesado`, no debe aparecer una segunda fila en `programacion`
(así se prueba la deduplicación sin escribir código).

Para `citas.canceladas` es el mismo mecanismo, cambiando `--topic` y el
payload según el contrato (sin `zonaHoraria`, con `canceladaPor`).

## Por qué no hay migración Flyway aquí

Las tablas de `notificaciones.*` ya las crea `db/V1__esquema_inicial.sql`
al arrancar Postgres (montado como `docker-entrypoint-initdb.d`). Cuando
`agenda-service` exista, ese archivo se muda a su
`src/main/resources/db/migration/` y Flyway toma el relevo — sigue siendo
el único dueño de la migración del esquema completo (ver CLAUDE.md).
