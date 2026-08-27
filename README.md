# AGENDA-D

Plataforma distribuida de agendamiento de citas para negocios de servicios.
Proyecto de aula · Programación Avanzada · SOF-G2-NOC · Equipo 2

**Brayan Trujillo · Andrés Bonilla · Johan Camacho · Luis Moncada**

---

## Qué hace

Cualquier negocio de servicios (barbería, consultorio, taller, veterinaria)
publica un enlace o un QR. El cliente entra, ve los cupos libres, reserva sin
crear cuenta y recibe un recordatorio 24 horas antes. El negocio ve su agenda
y sus indicadores de ocupación e inasistencia.

**Fuera de alcance por acuerdo con el docente:** autenticación de usuarios.
El sistema opera como si el usuario ya estuviera autenticado.

---

## Estado actual

**Fase 1 en curso.** Hoy funciona la infraestructura; los servicios Java
todavía no tienen proyecto, así que sus carpetas están vacías y no se pueden
construir. El Compose los deja definidos porque representan la infraestructura
prevista, pero solo el perfil `infra` levanta sin errores.

| Componente | Estado |
|---|---|
| PostgreSQL con el esquema y datos de prueba | ✅ funciona |
| Kafka en KRaft y su consola | ✅ funciona |
| Contratos OpenAPI, GraphQL y de eventos | ✅ acordados |
| `agenda-service` | ⬜ por construir |
| `notificaciones-service` | ⬜ por construir |
| `gateway-graphql` | ⬜ por construir |
| `analitica-service` | ⬜ por construir |
| PWA de reserva | ⬜ por construir |

---

## Arranque

```bash
git clone <url> && cd agenda-d

# Lo único que funciona hoy: base de datos + bus de eventos
docker compose --profile infra up -d
```

Con eso arriba puedes conectarte a la base y ver el esquema ya creado con sus
datos de prueba. La migración se aplica sola la primera vez.

```bash
# Ver las tablas
docker exec -it agd-postgres psql -U agendad -d agendad -c "\dt agenda.*"

# Comprobar que el EXCLUDE funciona: la segunda debe fallar
docker exec -it agd-postgres psql -U agendad -d agendad -c "
INSERT INTO agenda.cita (negocio_id, servicio_id, profesional_id, inicio, fin, cliente_nombre, cliente_celular)
VALUES ('11111111-1111-1111-1111-111111111111','22222222-2222-2222-2222-222222222222',
        '33333333-3333-3333-3333-333333333333','2026-09-01 15:00:00+00','2026-09-01 16:00:00+00','A','3001111111');"

# Ver los tópicos de Kafka
docker exec agd-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

**Perfiles.** `infra` levanta base de datos, Kafka y la consola. `core` añade
`agenda-service` y `full` añade el resto — esos dos empezarán a funcionar a
medida que existan los proyectos con su `pom.xml` y su `Dockerfile`.

| Servicio | URL | Estado |
|---|---|---|
| PostgreSQL | localhost:5432 · `agendad`/`agendad` | activo |
| Kafka desde el equipo | localhost:29092 | activo |
| Consola de Kafka | http://localhost:8090 | activo |
| agenda-service | http://localhost:8081 | por construir |
| gateway GraphQL | http://localhost:8080/graphiql | por construir |

> Alguien del equipo tiene 8 GB de RAM. Usa el perfil más pequeño que te sirva.

Para bajar todo y empezar de cero:
```bash
docker compose --profile full down -v
```

---

## Estructura

```
agenda-d/
├─ docker-compose.yml
├─ agenda-service/          Dominio: citas, cupos, configuración. Productor.
├─ notificaciones-service/  Consumidor. Confirmaciones y recordatorios.
├─ analitica-service/       Consumidor. Ocupación e inasistencia.
├─ gateway-graphql/         Compone la pantalla del panel en una consulta.
├─ web/                     2 pantallas: reserva pública y panel de recepción.
└─ docs/
   ├─ openapi/              Contratos REST — SE ACUERDAN ANTES DE PROGRAMAR
   └─ eventos/              Contrato de los eventos de Kafka
```

Cada servicio tiene su propio `pom.xml`. No hay proyecto padre: son
independientes y se construyen por separado.

---

## Las dos decisiones que hay que entender

Cualquiera del equipo puede ser elegido para sustentar. Estas dos cosas
**tienen que saberlas los cuatro**.

### 1. El solapamiento lo impide la base de datos, no el código

```sql
ALTER TABLE agenda.cita ADD CONSTRAINT cita_sin_solape
    EXCLUDE USING gist (
        profesional_id WITH =,
        tstzrange(inicio, fin, '[)') WITH &&
    ) WHERE (estado <> 'CANCELADA');
```

Validarlo en Java no sirve: entre el `SELECT` que pregunta si está libre y el
`INSERT` que escribe, cabe otra petición. Y un `UNIQUE(profesional_id, inicio)`
tampoco basta: una cita de 60 minutos a las 10:00 y otra a las 10:30 tienen
inicios distintos y aun así se pisan. Por eso comparamos **rangos** con `&&`,
no instantes con `=`.

Cuando el motor rechaza la escritura, el servicio traduce ese error en un
`409` con los cupos más cercanos. El usuario nunca ve un error técnico.

### 2. El evento se escribe en la misma transacción que la cita (outbox)

Guardar la cita y publicar en Kafka son dos operaciones que pueden fallar por
separado. Si publicáramos directo, un cliente podría quedar con una cita
confirmada de la que la cocina —perdón, el profesional— nunca se entera.

Por eso el `INSERT` en `cita` y el `INSERT` en `outbox` van en la misma
transacción: o las dos, o ninguna. Un relay periódico lee lo pendiente, lo
publica y lo marca. Se puede apagar Kafka, seguir reservando, y al reconectar
se publica todo.

---

## Plan de trabajo

**El docente aprobó la arquitectura** y pidió completar primero el *flujo
mínimo*: REST → persistencia → Kafka → consumidor → GraphQL. Las decisiones
de robustez (DLQ, Testcontainers, analítica, recordatorios persistentes) son
correctas pero van después. El plan está ordenado según eso.

Capacidad real: ~20 horas semanales entre los cuatro.

### Fase 1 · Semanas 1–5 · Cimientos
- [ ] Contratos OpenAPI y GraphQL revisados y aprobados por todos
- [ ] Migración Flyway con el `EXCLUDE` corriendo
- [ ] Contrato de eventos acordado
- [ ] `docker compose --profile core up` levanta agenda-service con `/actuator/health`
- [ ] `agenda-service` consulta cupos y reserva contra la BD real

### Fase 2 · Semanas 6–10 · Flujo mínimo completo
**El hito del semestre.** Al cerrarlo, el sistema recorre el circuito entero.
- [ ] Outbox publicando `citas.reservadas`
- [ ] `notificaciones-service` consumiendo y guardando el mensaje
- [ ] `gateway-graphql` respondiendo la consulta `panelRecepcion`
- [ ] PWA pública de reserva contra la API real

Cada pieza en su versión más simple. Lo importante es que el circuito cierre.

### Fase 3 · Semanas 11–15 · Robustez
- [ ] DLQ con reintentos y espera creciente
- [ ] Idempotencia verificada
- [ ] Testcontainers con la prueba de concurrencia (100 hilos)
- [ ] Recordatorio de 24 h con recuperación tras reinicio
- [ ] `analitica-service` y panel de recepción
- [ ] Prueba de separación entre negocios

### Semana 15
Ensayar la sustentación. Los cuatro, todo el flujo.

---

## Reglas del equipo

1. **Rama por tarea, PR revisado por alguien de la otra pareja.**
   Así todos ven todo el código y cualquiera puede sustentar.
   `feature/reserva-cita`, `fix/zona-horaria`

2. **Nadie toca la Fase 3 hasta que el circuito de la Fase 2 cierre de punta a punta.**
   Es la indicación explícita del docente.

3. **Si vamos atrasados, se recorta desde la Fase 3 hacia atrás.**
   Nunca se recorta el `EXCLUDE` ni el circuito completo.

4. **El contrato no se cambia en silencio.** Aviso en el chat + PR.

5. **Dos horas fijas semanales de trabajo conjunto**, aunque sea virtual.
   No para programar: para integrar y para que todos entiendan lo del resto.
   Tres servicios que se hablan por eventos no se integran por chat.

---

## Convenciones

- **Todo instante se guarda en UTC** (`timestamptz`). Los horarios de atención
  se definen en hora local y se convierten al calcular disponibilidad.
  La zona del negocio está en `negocio.zona_horaria`.
- **Mensajes de error en español**, listos para mostrar al usuario.
  Nunca un stacktrace ni un código técnico.
- **El contexto de negocio** llega por la cabecera `X-Negocio-Id` en las rutas
  administrativas, y por el `slug` de la URL en las rutas públicas.
- Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Kafka 3.7 (KRaft)

---
