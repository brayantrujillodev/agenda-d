#!/usr/bin/env bash
# =====================================================================
# AGENDA-D · Publicar el repo y crear las tareas
#
# Reparto: un servicio completo por persona. Nadie espera a nadie.
# Todos programan contra los contratos ya acordados en docs/.
#
# Requiere GitHub CLI:  https://cli.github.com
#   gh auth login       (una sola vez)
#
# Uso:  bash setup-github.sh
# =====================================================================

# Los cuerpos de los issues van en comillas simples a propósito: son texto
# Markdown literal (con `backticks` y $(comandos) de ejemplo) que NO debe
# expandir el shell. Por eso se silencia SC2016 en todo el archivo.
# shellcheck disable=SC2016
set -e

# ---- CAMBIA ESTO ANTES DE CORRERLO ----------------------------------
USUARIO_GH="brayantrujillodev"   # tu usuario de GitHub
ANDRES="devbonill17"
JOHAN="master2503"
LUIS="Emerson062002"
# ---------------------------------------------------------------------

echo "==> 1. Inicializando el repositorio"
git init -b main
git add .
git commit -m "feat: contratos, esquema de datos e infraestructura del proyecto"

echo "==> 2. Creando el repo en GitHub (privado)"
gh repo create agenda-d --private --source=. --remote=origin --push

echo "==> 3. Invitando al equipo"
for u in "$ANDRES" "$JOHAN" "$LUIS"; do
  gh api -X PUT "repos/$USUARIO_GH/agenda-d/collaborators/$u" -f permission=push
done

echo "==> 4. Protegiendo main (exige PR con 1 aprobación)"
gh api -X PUT "repos/$USUARIO_GH/agenda-d/branches/main/protection" \
  -H "Accept: application/vnd.github+json" \
  -f 'required_pull_request_reviews[required_approving_review_count]=1' \
  -f 'enforce_admins=false' \
  -f 'required_status_checks=null' \
  -f 'restrictions=null' || echo "   (si falla, hazlo por la web: Settings > Branches)"

echo "==> 5. Creando etiquetas"
gh label create "flujo-minimo" --color "0E8A16" --description "Parte del circuito que pidió el docente" --force
gh label create "integracion"  --color "D93F0B" --description "Requiere que los servicios ya existan" --force
gh label create "fase-3"       --color "C5DEF5" --description "Robustez, va después del flujo mínimo" --force

echo "==> 6. Creando las tareas"

# ---------------------------------------------------------------------
gh issue create --title "agenda-service · el núcleo del dominio" \
  --assignee "$USUARIO_GH" --label "flujo-minimo" --body \
'Servicio completo. Es el más grande y el más difícil del proyecto: aquí vive el camino crítico y las dos garantías que sostienen todo.

## Alcance
- [ ] Proyecto Spring Boot 3.3 / Java 21, pom propio, Dockerfile, puerto 8081
- [ ] Migración Flyway con el esquema y la restricción `EXCLUDE`
- [ ] Entidades JPA del esquema agenda
- [ ] `GET /v1/publico/{slug}/disponibilidad` — cruza horarios, bloqueos y citas, con conversión de hora local a UTC
- [ ] `POST /v1/publico/{slug}/citas` — reserva con `Idempotency-Key`
- [ ] `GET`/`DELETE /v1/gestion/{token}` — el cliente opera su cita
- [ ] `GET /v1/agenda/{profesionalId}` y `PATCH /v1/citas/{id}/estado`
- [ ] Tabla outbox escrita en la misma transacción que la cita
- [ ] Relay `@Scheduled` que publica en `citas.reservadas`

## Termina cuando
```bash
# 1. Ver cupos
curl "localhost:8081/v1/publico/barberia-el-corte/disponibilidad?servicioId=22222222-2222-2222-2222-222222222222&fecha=2026-09-01"

# 2. Reservar → 201
curl -X POST "localhost:8081/v1/publico/barberia-el-corte/citas" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"servicioId\":\"22222222-2222-2222-2222-222222222222\",\"profesionalId\":\"33333333-3333-3333-3333-333333333333\",\"inicio\":\"2026-09-01T15:00:00Z\",\"clienteNombre\":\"Juan Perez\",\"clienteCelular\":\"3001234567\"}"

# 3. Repetir con OTRA Idempotency-Key → DEBE dar 409
```
Si el paso 3 devuelve 201, está mal: se está validando en Java en vez de dejar que la base rechace.

Y la prueba del outbox:
```bash
docker compose stop kafka   # reservas 2 citas → responden 201 igual
docker compose start kafka  # los 2 eventos se publican solos
```

## No dependo de nadie
Postgres y Kafka ya funcionan con `docker compose --profile infra up -d`.

## Prompt inicial
> Lee CLAUDE.md y docs/openapi/agenda-service.yaml. Vamos a construir agenda-service por partes, empezando por que arranque y aplique la migración. No propongas nada antes de leer los contratos.

Detalle en `docs/CONSTRUCCION.md`.'

# ---------------------------------------------------------------------
gh issue create --title "notificaciones-service · el consumidor de eventos" \
  --assignee "$JOHAN" --label "flujo-minimo" --body \
'Servicio completo e independiente. Consume los eventos de Kafka y los convierte en mensajes.

## Alcance
- [ ] Proyecto Spring Boot 3.3 / Java 21, pom propio, Dockerfile, puerto 8082
- [ ] Consumidor de `citas.reservadas` y `citas.canceladas`
- [ ] Deduplicación por `eventoId` contra `notificaciones.evento_procesado`
- [ ] Interfaz `CanalNotificacion` con implementación `RegistroCanal` que guarda el mensaje en base de datos en vez de enviarlo
- [ ] Programación del recordatorio de 24 h en tabla, con `@Scheduled` que dispara los vencidos y los recupera al reiniciar

## No dependo de nadie
**No necesitas esperar a agenda-service.** Publica eventos a mano para probar:
```bash
docker compose --profile infra up -d
docker exec -i agd-kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic citas.reservadas
# pegas un JSON del contrato y presionas Enter
```
El JSON exacto está en `docs/eventos/CONTRATO-EVENTOS.md`.

## Termina cuando
- Publicas un evento a mano y aparece un registro en `notificaciones.programacion`
- Publicas **el mismo evento dos veces** y solo queda un registro
- Programas un recordatorio, reinicias el contenedor, y el recordatorio igual sale

## Prompt inicial
> Lee CLAUDE.md y docs/eventos/CONTRATO-EVENTOS.md. Crea notificaciones-service: Spring Boot 3.3, Java 21, puerto 8082, pom propio, Dockerfile. Consume el tópico citas.reservadas con spring-kafka, deduplica por eventoId, y define la interfaz CanalNotificacion con la implementación que guarda en base de datos.'

# ---------------------------------------------------------------------
gh issue create --title "gateway-graphql · la pantalla en una sola consulta" \
  --assignee "$LUIS" --label "flujo-minimo" --body \
'Servicio completo e independiente. Compone datos de varios servicios en una sola respuesta.

## Alcance
- [ ] Proyecto Spring Boot 3.3 / Java 21, pom propio, Dockerfile, puerto 8080
- [ ] Carga `docs/graphql/schema.graphqls`
- [ ] Resolver de `panelRecepcion` llamando a agenda-service por REST con RestClient
- [ ] `/graphiql` habilitado
- [ ] Sin base de datos y sin lógica de negocio

## No dependo de nadie
**No necesitas esperar a agenda-service.** Programa contra el contrato
`docs/openapi/agenda-service.yaml`, que ya está acordado, y mientras tanto
devuelve datos de ejemplo desde el cliente REST. Cuando agenda-service exista,
solo cambias la URL.

## Termina cuando
Esta consulta responde en `localhost:8080/graphiql`:
```graphql
{
  panelRecepcion(fecha: "2026-09-01") {
    citasDelDia { horaLocal clienteNombre servicio { nombre } }
    negocio { nombre servicios { nombre precio } }
  }
}
```

## Ojo con esto
`metricasDelMes` devuelve ceros por ahora, porque analitica-service todavía no
existe. Déjalo con un TODO.

## Prompt inicial
> Lee CLAUDE.md y docs/graphql/schema.graphqls. Crea gateway-graphql: Spring Boot 3.3, Java 21, puerto 8080, spring-boot-starter-graphql, pom propio, Dockerfile. Carga el esquema y resuelve panelRecepcion llamando a agenda-service por REST con RestClient. Mientras agenda-service no exista, devuelve datos de ejemplo. Sin base de datos.'

# ---------------------------------------------------------------------
gh issue create --title "PWA pública de reserva · lo que ve el cliente" \
  --assignee "$ANDRES" --label "flujo-minimo" --body \
'Aplicación completa e independiente. Es la única parte del sistema que ve el cliente final.

## Alcance
- [ ] `web/index.html`, `app.js`, `style.css` — HTML, CSS y JS nativo, sin framework ni build
- [ ] Elegir servicio → ver cupos → nombre y celular → confirmar
- [ ] Muestra el enlace de gestión al confirmar
- [ ] Maneja el `409`: muestra los cupos alternativos, no un error feo
- [ ] `manifest.json` y service worker que cachea la interfaz
- [ ] Sin conexión: mensaje claro de que reservar requiere internet
- [ ] Pantalla mínima para operar la cita desde `/gestion/{token}`

## No dependo de nadie
**No necesitas esperar al backend.** El contrato `docs/openapi/agenda-service.yaml`
trae los ejemplos de respuesta de cada endpoint. Trabaja contra esos datos y
cuando agenda-service exista, cambias la URL base.

## Termina cuando
- Se instala en un celular Android desde Chrome, con ícono propio
- El flujo completo funciona contra los datos de ejemplo
- El `409` muestra alternativas y se ve bien

## Prompt inicial
> Lee CLAUDE.md y docs/openapi/agenda-service.yaml. Crea la PWA de reserva pública en web/ con HTML, CSS y JavaScript nativo, sin framework ni build. Usa los ejemplos del contrato como datos mientras el backend no exista. Incluye manifest.json y service worker que cachee la interfaz, pero recuerda que reservar exige conexión.'

# ---------------------------------------------------------------------
gh issue create --title "INTEGRACIÓN · conectar las cuatro piezas" \
  --label "integracion" --body \
'Cuando los cuatro servicios estén, hay que enchufarlos. Esta es la tarea de riesgo del semestre: trabajamos aislados a propósito, así que aquí es donde aparecen las sorpresas.

## Se hace en sesión conjunta, los cuatro
- [ ] La PWA apunta a agenda-service real
- [ ] El gateway apunta a agenda-service real
- [ ] Reservar desde la PWA deja un registro en notificaciones-service
- [ ] `docker compose --profile full up -d --build` levanta todo
- [ ] Actualizar la tabla de estado del README

## Termina cuando
Se reserva desde el celular y el circuito completo funciona:
REST → persistencia → outbox → Kafka → consumidor → GraphQL.

**Ese es el hito que pidió el docente.** Cuando cierre, le avisamos.

## Después de esto
Recién entonces la Fase 3: DLQ, Testcontainers, analitica-service, panel de
recepción y la prueba de separación entre negocios.'

echo
echo "==> Listo. Repo: https://github.com/$USUARIO_GH/agenda-d"
gh issue list
