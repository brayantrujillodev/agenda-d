# Estado de implementación

El monorepo ejecutable es `agenda-d/`. Los módulos se construyen por separado
y el perfil `full` los integra mediante Docker Compose.

| Componente | Puerto | Responsabilidad |
| --- | ---: | --- |
| agenda-service | 8081 | REST público, reserva transaccional, outbox y relay Kafka |
| notificaciones-service | 8082 | Confirmaciones y cancelaciones idempotentes en canal `REGISTRO` |
| analitica-service | 8083 | Consumidor idempotente y consulta agregada de métricas |
| gateway-graphql | 8080 | Consulta `panelRecepcion`, sin base de datos propia |
| web | 8084 | PWA de reserva estática |

## Contratos

- REST público/administrativo: `docs/openapi/agenda-service.yaml`.
- REST interno gateway → agenda: `docs/openapi/interno-agenda.yaml`.
- Eventos Kafka: `docs/eventos/CONTRATO-EVENTOS.md`.
- Contrato GraphQL de producto: `docs/graphql/schema.graphqls`; la consulta
  ejecutable `panelRecepcion` exige `negocioId` y nunca usa un negocio fijo.

## Límites conocidos

La PWA implementa la reserva pública. Las mutaciones administrativas y los
recordatorios persistentes están documentados en los contratos, pero requieren
la siguiente iteración de dominio antes de exponerse: no se anuncian como
operativos en la interfaz.
