# Contrato de eventos · AGENDA-D

Tres tópicos, más una cola de fallidos. Este archivo se acuerda **antes** de
escribir el productor o los consumidores. Cambiarlo requiere PR y aviso al grupo.

## Por qué Kafka en esta arquitectura

Reservar es lo único que no puede fallar ni esperar. Todo lo que pasa
*después* de reservar —avisar al cliente, programar el recordatorio de
24 h, contar la ocupación y la inasistencia— es trabajo de otros dos
servicios que pueden estar caídos, lentos o reiniciándose.

| Sin Kafka (llamadas REST en cadena) | Con Kafka |
|---|---|
| Si `notificaciones-service` está caído, la reserva falla o hay que inventar reintentos en `agenda-service`. | La reserva responde `201` igual; el evento espera en el tópico y se consume cuando el servicio vuelva. |
| `agenda-service` tiene que conocer a cada interesado y llamarlo uno por uno. | `agenda-service` publica un hecho; cada consumidor tiene su propio grupo y su propio ritmo. |
| Agregar analítica obliga a tocar el código de reserva. | `analitica-service` se suscribió a los mismos tópicos sin cambiar el productor. |

Lo que **no** viaja por Kafka: la consulta de cupos y la reserva en sí
(síncronas, el cliente necesita la respuesta ya) y el panel (consulta,
va por GraphQL).

## Flujo implementado

```text
POST /citas ──► agenda.cita + agenda.outbox  (misma transacción)
                         │
                OutboxRelay (@Scheduled, 2 s) ── clave = profesionalId
                         ▼
        citas.reservadas / citas.canceladas / citas.estado   (3 particiones, 7 días)
              │                                   │
   notificaciones-service                 analitica-service
   grupo notificaciones-service           grupo analitica-service
   · confirmación / cancelación           · upsert en metrica_diaria
   · recordatorio 24 h (PENDIENTE)        · GET /v1/metricas → GraphQL
              │  falla 3 veces (1 s, 4 s, 16 s) o no es JSON
              ▼
          citas.dlq ──► notificaciones-service-dlq ──► mensaje_fallido ──► GET /v1/mensajes-fallidos
```

## Declaración de los tópicos

No dependen del auto-create del broker: el contenedor `kafka-init` del
`docker-compose.yml` los crea al arrancar con 3 particiones y
`retention.ms=604800000` (7 días), y termina. Es idempotente
(`--if-not-exists` + `alter`), así que relanzarlo no rompe nada. Los
servicios Java esperan a que termine (`service_completed_successfully`).

## Reglas que aplican a todos los eventos

| Regla | Por qué |
|---|---|
| Clave de partición = `profesionalId` | Garantiza el orden por profesional: la cancelación nunca se procesa antes que la reserva que la origina. |
| Todo evento lleva `eventoId` | Kafka entrega *al menos una vez*. El consumidor deduplica contra su tabla `evento_procesado`. |
| Todo evento lleva `version` | Permite evolucionar el esquema sin romper a los consumidores. |
| Todo evento lleva `negocioId` | El consumidor sitúa el dato en el contexto correcto (RNF-02). |
| Todo evento lleva `correlationId` | Se propaga desde la petición REST. Permite seguir una operación completa en los logs. |
| Instantes en UTC, formato ISO-8601 | RNF-06. El frontend convierte a hora local. |
| 3 particiones, retención 7 días | Suficiente para el volumen del proyecto. |

---

## `citas.reservadas`

**Produce:** `agenda-service` (vía relay de outbox)
**Consumen:** `notificaciones-service`, `analitica-service`

El hecho central del dominio. Dispara la confirmación al cliente, el aviso al
profesional, la programación del recordatorio y el conteo de la métrica.

```json
{
  "eventoId": "6f2a91c4-8b3d-4e21-9f77-2c1a5b8e0d33",
  "version": 1,
  "ocurridoEn": "2026-08-19T14:32:07Z",
  "correlationId": "a1b2c3d4-...",

  "negocioId": "11111111-1111-1111-1111-111111111111",
  "citaId": "44444444-4444-4444-4444-444444444444",
  "profesionalId": "33333333-3333-3333-3333-333333333333",
  "profesionalNombre": "Laura",
  "servicioId": "22222222-2222-2222-2222-222222222222",
  "servicioNombre": "Corte de cabello",

  "inicio": "2026-08-20T13:00:00Z",
  "fin": "2026-08-20T14:00:00Z",
  "zonaHoraria": "America/Bogota",

  "cliente": {
    "nombre": "Juan Pérez",
    "celular": "3001234567"
  },
  "tokenGestion": "9f3c7a2b5e8d1064..."
}
```

> **Nota sobre el celular:** va en el evento porque `notificaciones-service`
> lo necesita para enviar el mensaje y no tiene acceso al esquema de agenda.
> Si más adelante se quiere evitar que circule por el bus, se reemplaza por
> una referencia y el consumidor la resuelve por REST.

---

## `citas.canceladas`

**Produce:** `agenda-service` (vía relay de outbox)
**Consumen:** `notificaciones-service`, `analitica-service`

El cupo vuelve a estar libre. Se avisa al profesional y se corrige la ocupación.

```json
{
  "eventoId": "7a3b02d5-...",
  "version": 1,
  "ocurridoEn": "2026-08-19T18:05:44Z",
  "correlationId": "...",

  "negocioId": "11111111-...",
  "citaId": "44444444-...",
  "profesionalId": "33333333-...",
  "servicioId": "22222222-...",
  "inicio": "2026-08-20T13:00:00Z",
  "fin": "2026-08-20T14:00:00Z",

  "canceladaPor": "CLIENTE",
  "cliente": { "nombre": "Juan Pérez", "celular": "3001234567" }
}
```

`canceladaPor`: `CLIENTE` | `NEGOCIO`

---

## `citas.estado`

**Produce:** `agenda-service`
**Consume:** `analitica-service`

Cierra el ciclo de la cita. Alimenta la tasa de inasistencia, que es el
indicador con el que se mide si el sistema sirvió.

```json
{
  "eventoId": "8c4d13e6-...",
  "version": 1,
  "ocurridoEn": "2026-08-20T14:05:00Z",
  "correlationId": "...",

  "negocioId": "11111111-...",
  "citaId": "44444444-...",
  "profesionalId": "33333333-...",
  "servicioId": "22222222-...",
  "inicio": "2026-08-20T13:00:00Z",
  "fin": "2026-08-20T14:00:00Z",

  "estadoAnterior": "CONFIRMADA",
  "estadoNuevo": "NO_ASISTIO"
}
```

`estadoNuevo`: `ATENDIDA` | `NO_ASISTIO`

---

## `citas.dlq`

Cuando un consumidor falla tres veces con espera creciente (1 s, 4 s, 16 s),
el mensaje se envía aquí en lugar de perderse o de bloquear la partición.
El panel muestra una alerta. Esto es lo que sostiene el RNF-04.

El mensaje original se conserva íntegro y se añaden cabeceras con el motivo,
el tópico de origen y el número de intentos.

Dos tipos de fallo, dos caminos:

| Fallo | Ejemplo | Qué pasa |
|---|---|---|
| El listener lanza una excepción | evento sin `inicio`, base de datos caída | 3 reintentos (1 s, 4 s, 16 s) y luego `citas.dlq`, `intentos = 3` |
| El mensaje ni siquiera es JSON | `esto-no-es-json` | `ErrorHandlingDeserializer` lo marca como no reintentable: va directo a `citas.dlq` con los bytes originales, `intentos = 1` |

Sin el `ErrorHandlingDeserializer`, el segundo caso dejaba al consumidor en
un bucle de error infinito sobre el mismo offset, con la partición
bloqueada y sin pasar nunca por la DLQ (encontrado en la Entrega 2 del
segundo periodo).

## Garantías de entrega y orden

- **Productor:** `acks=all` + `enable.idempotence=true`; el relay espera el
  ack de cada evento antes de marcarlo `enviado_en`.
- **Orden:** si un envío falla, el relay corta el lote y reintenta todo en
  el siguiente ciclo. Seguir con los siguientes publicaría eventos
  posteriores antes que el fallido y rompería el orden por `profesionalId`.
- **Consumidores:** al menos una vez. Cada uno deduplica por `eventoId`
  contra su tabla `evento_procesado` en la misma transacción que el efecto,
  y deja en el log `Evento … ya procesado, se ignora (reentrega de Kafka)`.
- **Trazabilidad:** cada consumidor registra `evento`, `cita` y
  `correlationId` al procesar; el `correlationId` llega desde la cabecera
  `X-Correlation-Id` de la petición REST original.

---

## Cómo probar sin escribir código

Con el perfil `full` arriba:

```bash
# ver los tópicos
docker exec agd-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# escuchar lo que se publica mientras alguien reserva desde la pantalla
docker exec agd-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic citas.reservadas --from-beginning

# publicar un evento a mano para probar al consumidor sin tener el productor listo
docker exec -i agd-kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic citas.reservadas
```

Ese último comando es útil en la Fase 1: permite que quien construya
`notificaciones-service` avance sin esperar a que `agenda-service` esté listo.

Para el productor, la clave de partición se publica separada por tabulador:

```bash
printf '%s\t%s\n' "<profesionalId>" '<json del evento>' | docker exec -i agd-kafka \
  kafka-console-producer.sh --bootstrap-server localhost:9092 --topic citas.reservadas \
  --property parse.key=true --property key.separator=$'\t'
```

Publicar dos veces el mismo `eventoId` es la forma más rápida de ver la
idempotencia: el segundo mensaje deja en el log de ambos consumidores
`Evento … ya procesado, se ignora` y no cambia ni un número en la base.
