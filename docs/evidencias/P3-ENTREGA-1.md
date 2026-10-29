# Evidencia · P3 – Entrega 1 · Gateway GraphQL

Generado por `docs/evidencias/flujo-p3.mjs` el 2026-10-29T01:51:58.368Z contra el stack real
(`docker compose --profile full up -d --build`). Cada bloque es una petición real y su respuesta
sin editar. Fecha de prueba: `2026-10-30` (hora local de Bogotá).

## Resumen

**31 de 31 verificaciones pasaron.**

| Verificación | Resultado |
|---|---|
| agenda-service saludable | ✅ |
| notificaciones-service saludable | ✅ |
| analitica-service saludable | ✅ |
| gateway-graphql saludable | ✅ |
| Reserva creada | ✅ |
| analitica-service proyectó las reservas | ✅ |
| panelRecepcion compone agenda y métricas sin errores | ✅ |
| Celular enmascarado según el contrato GraphQL | ✅ |
| topServicios con nombre real (no un UUID) | ✅ |
| negocio sale de agenda-service, no de datos fijos en el código | ✅ |
| agenda(profesionalId) funciona con cualquier profesional, no solo uno fijo | ✅ |
| crearServicio | ✅ |
| actualizarServicio | ✅ |
| crearProfesional | ✅ |
| definirHorario | ✅ |
| crearBloqueo con citas en el rango → CITAS_EN_RANGO | ✅ |
| crearBloqueo en un rango libre | ✅ |
| bloqueos (antes no tenía resolver y fallaba) | ✅ |
| cancelarCita | ✅ |
| registrarAsistencia sobre cita cancelada → error de dominio en español | ✅ |
| registrarAsistencia NO_ASISTIO | ✅ |
| desactivarServicio | ✅ |
| notificaciones-service consumió la cancelación hecha desde el panel | ✅ |
| Sin X-Negocio-Id → DATOS_INVALIDOS | ✅ |
| Negocio inexistente → NO_ENCONTRADO (el 404 de agenda-service, traducido) | ✅ |
| Input inválido → DATOS_INVALIDOS sin llegar a agenda-service | ✅ |
| ID mal formado → DATOS_INVALIDOS | ✅ |
| Con analitica caído, la agenda sale y metricasDelMes = null con SERVICIO_NO_DISPONIBLE | ✅ |
| Consulta de 19 niveles se corta antes de ejecutarse | ✅ |
| GraphiQL puede introspectar en desarrollo (los límites no lo rompen) | ✅ |
| Rate limit responde 429 con formato de error GraphQL | ✅ |

## 0. Los cuatro servicios están arriba

Un solo comando levanta todo; cada servicio expone `/actuator/health`.

- `agenda-service` → HTTP 200 `{"status":"UP","groups":["liveness","readiness"]}`
- `notificaciones-service` → HTTP 200 `{"status":"UP"}`
- `analitica-service` → HTTP 200 `{"status":"UP"}`
- `gateway-graphql` → HTTP 200 `{"status":"UP"}`

## 1. El cliente reserva por REST público (fuera del gateway)

Reservar no pasa por GraphQL: es la ruta pública, sin cuenta (ver encabezado de `schema.graphqls`).

`GET /v1/publico/barberia-el-corte/disponibilidad?...` → HTTP 200, 10 cupos. Se toma el primero: `2026-10-30T13:00:00Z` (08:00).

**Petición** `POST /v1/publico/barberia-el-corte/citas`

```json
{
  "servicioId": "22222222-2222-2222-2222-222222222222",
  "profesionalId": "33333333-3333-3333-3333-333333333333",
  "inicio": "2026-10-30T13:00:00Z",
  "clienteNombre": "Juan Pérez",
  "clienteCelular": "3001234567"
}
```

**Respuesta** HTTP 201

```json
{
  "id": "d491fd82-bef2-4186-b9ba-31f724cdfcfd",
  "inicio": "2026-10-30T13:00:00Z",
  "fin": "2026-10-30T14:00:00Z",
  "horaLocal": "08:00",
  "servicioNombre": "Corte de cabello",
  "profesionalNombre": "Laura",
  "estado": "CONFIRMADA",
  "enlaceGestion": "http://localhost:8081/v1/gestion/893bdfc9b5a78afe28b93bc09911c08a6ebd104a60182361"
}
```

- ✅ **Reserva creada** — cita d491fd82-bef2-4186-b9ba-31f724cdfcfd

Segunda reserva, con Andrés a las 09:00, para que el panel componga a dos profesionales.

## 2. El evento llega a analitica por outbox → Kafka

Se espera a que el relay publique `citas.reservadas` y analitica-service lo proyecte.

**Petición** `GET /v1/metricas?desde=2026-10-01&hasta=2026-10-30`

**Respuesta** HTTP 200

```json
{
  "desde": "2026-10-01",
  "hasta": "2026-10-30",
  "totalCitas": 2,
  "atendidas": 0,
  "noAsistio": 0,
  "canceladas": 0,
  "ocupacion": 10,
  "tasaInasistencia": 0,
  "topServicios": [
    {
      "servicioId": "22222222-2222-2222-2222-222222222222",
      "cantidad": 2
    }
  ]
}
```

- ✅ **analitica-service proyectó las reservas** — topServicios trae solo `servicioId`: el nombre lo pone el gateway

## 3. `panelRecepcion`: una consulta, dos servicios

Agenda del día de **todos** los profesionales (agenda-service) + configuración del negocio (agenda-service) + métricas del mes (analitica-service), con el nombre de cada servicio de `topServicios` resuelto por el gateway.

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
query Panel($fecha: Date) {
  panelRecepcion(fecha: $fecha) {
    fecha
    negocio { nombre zonaHoraria }
    citasDelDia { id horaLocal clienteNombre clienteCelular estado servicio { nombre } profesional { nombre } }
    metricasDelMes { desde hasta totalCitas atendidas noAsistio canceladas ocupacion tasaInasistencia
                     topServicios { cantidad servicio { nombre } } }
  }
}
```

Variables: `{"fecha":"2026-10-30"}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "panelRecepcion": {
      "fecha": "2026-10-30",
      "negocio": {
        "nombre": "Barbería El Corte",
        "zonaHoraria": "America/Bogota"
      },
      "citasDelDia": [
        {
          "id": "d491fd82-bef2-4186-b9ba-31f724cdfcfd",
          "horaLocal": "08:00",
          "clienteNombre": "Juan Pérez",
          "clienteCelular": "300****567",
          "estado": "CONFIRMADA",
          "servicio": {
            "nombre": "Corte de cabello"
          },
          "profesional": {
            "nombre": "Laura"
          }
        },
        {
          "id": "ff15de64-b373-4876-98a0-4053e1ab61f3",
          "horaLocal": "09:00",
          "clienteNombre": "María Gómez",
          "clienteCelular": "310****543",
          "estado": "CONFIRMADA",
          "servicio": {
            "nombre": "Corte de cabello"
          },
          "profesional": {
            "nombre": "Andrés"
          }
        }
      ],
      "metricasDelMes": {
        "desde": "2026-10-01",
        "hasta": "2026-10-30",
        "totalCitas": 2,
        "atendidas": 0,
        "noAsistio": 0,
        "canceladas": 0,
        "ocupacion": 10,
        "tasaInasistencia": 0,
        "topServicios": [
          {
            "cantidad": 2,
            "servicio": {
              "nombre": "Corte de cabello"
            }
          }
        ]
      }
    }
  }
}
```

- ✅ **panelRecepcion compone agenda y métricas sin errores**

- ✅ **Celular enmascarado según el contrato GraphQL**

- ✅ **topServicios con nombre real (no un UUID)**

## 4. Otras consultas

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
{ negocio { nombre slugPublico servicios { nombre duracionMin precio activo }
  profesionales { nombre servicios { nombre } horarios { diaSemana horaInicio horaFin } } } }
```

**Respuesta** HTTP 200

```json
{
  "data": {
    "negocio": {
      "nombre": "Barbería El Corte",
      "slugPublico": "barberia-el-corte",
      "servicios": [
        {
          "nombre": "Barba",
          "duracionMin": 30,
          "precio": "15000.00",
          "activo": true
        },
        {
          "nombre": "Corte de cabello",
          "duracionMin": 60,
          "precio": "25000.00",
          "activo": true
        }
      ],
      "profesionales": [
        {
          "nombre": "Andrés",
          "servicios": [
            {
              "nombre": "Corte de cabello"
            }
          ],
          "horarios": [
            {
              "diaSemana": 1,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            },
            {
              "diaSemana": 2,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            },
            {
              "diaSemana": 3,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            },
            {
              "diaSemana": 4,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            },
            {
              "diaSemana": 5,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            },
            {
              "diaSemana": 6,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            }
          ]
        },
        {
          "nombre": "Laura",
          "servicios": [
            {
              "nombre": "Corte de cabello"
            },
            {
              "nombre": "Barba"
            }
          ],
          "horarios": [
            {
              "diaSemana": 1,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            },
            {
              "diaSemana": 2,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            },
            {
              "diaSemana": 3,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            },
            {
              "diaSemana": 4,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            },
            {
              "diaSemana": 5,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            },
            {
              "diaSemana": 6,
              "horaInicio": "08:00",
              "horaFin": "18:00"
            }
          ]
        }
      ]
    }
  }
}
```

- ✅ **negocio sale de agenda-service, no de datos fijos en el código**

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
query($p: ID!, $f: Date!) { agenda(profesionalId: $p, fecha: $f) { horaLocal clienteNombre estado } }
```

Variables: `{"p":"33333333-3333-3333-3333-333333333334","f":"2026-10-30"}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "agenda": [
      {
        "horaLocal": "09:00",
        "clienteNombre": "María Gómez",
        "estado": "CONFIRMADA"
      }
    ]
  }
}
```

- ✅ **agenda(profesionalId) funciona con cualquier profesional, no solo uno fijo**

## 5. Mutaciones

Cada una es una llamada a agenda-service; los eventos los publica agenda-service por su outbox.

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($e: ServicioInput!) { crearServicio(entrada: $e) { id nombre duracionMin precio activo } }
```

Variables: `{"e":{"nombre":"Corte y barba","duracionMin":90,"precio":35000}}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "crearServicio": {
      "id": "6201be8c-95e3-4340-a05a-7859f3409570",
      "nombre": "Corte y barba",
      "duracionMin": 90,
      "precio": "35000.00",
      "activo": true
    }
  }
}
```

- ✅ **crearServicio**

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($id: ID!, $e: ServicioInput!) { actualizarServicio(id: $id, entrada: $e) { nombre precio } }
```

Variables: `{"id":"6201be8c-95e3-4340-a05a-7859f3409570","e":{"nombre":"Corte y barba premium","duracionMin":90,"precio":"40000"}}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "actualizarServicio": {
      "nombre": "Corte y barba premium",
      "precio": "40000.00"
    }
  }
}
```

- ✅ **actualizarServicio**

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($e: ProfesionalInput!) { crearProfesional(entrada: $e) { id nombre activo servicios { nombre } horarios { diaSemana } } }
```

Variables: `{"e":{"nombre":"Camila","serviciosIds":["6201be8c-95e3-4340-a05a-7859f3409570","22222222-2222-2222-2222-222222222222"]}}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "crearProfesional": {
      "id": "15737e73-de5b-4134-aa93-2b57e07b54d9",
      "nombre": "Camila",
      "activo": true,
      "servicios": [
        {
          "nombre": "Corte y barba premium"
        },
        {
          "nombre": "Corte de cabello"
        }
      ],
      "horarios": []
    }
  }
}
```

- ✅ **crearProfesional**

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($p: ID!, $h: [HorarioInput!]!) { definirHorario(profesionalId: $p, horarios: $h) { nombre horarios { diaSemana horaInicio horaFin } } }
```

Variables: `{"p":"15737e73-de5b-4134-aa93-2b57e07b54d9","h":[{"diaSemana":1,"horaInicio":"08:00","horaFin":"12:00"},{"diaSemana":1,"horaInicio":"14:00","horaFin":"18:00"},{"diaSemana":3,"horaInicio":"09:00","horaFin":"17:00"}]}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "definirHorario": {
      "nombre": "Camila",
      "horarios": [
        {
          "diaSemana": 1,
          "horaInicio": "08:00",
          "horaFin": "12:00"
        },
        {
          "diaSemana": 1,
          "horaInicio": "14:00",
          "horaFin": "18:00"
        },
        {
          "diaSemana": 3,
          "horaInicio": "09:00",
          "horaFin": "17:00"
        }
      ]
    }
  }
}
```

- ✅ **definirHorario**

Bloquear un rango que contiene la cita de Juan Pérez: agenda-service lo rechaza y el gateway expone su código tal cual.

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($e: BloqueoInput!) { crearBloqueo(entrada: $e) { id inicio fin motivo } }
```

Variables: `{"e":{"profesionalId":"33333333-3333-3333-3333-333333333333","inicio":"2026-10-30T12:00:00.000Z","fin":"2026-10-30T14:00:00.000Z","motivo":"Incapacidad"}}`

**Respuesta** HTTP 200

```json
{
  "errors": [
    {
      "message": "Hay 1 cita confirmada en ese rango. Cancélalas antes de bloquearlo.",
      "locations": [
        {
          "line": 1,
          "column": 31
        }
      ],
      "path": [
        "crearBloqueo"
      ],
      "extensions": {
        "codigo": "CITAS_EN_RANGO",
        "classification": "BAD_REQUEST"
      }
    }
  ],
  "data": null
}
```

- ✅ **crearBloqueo con citas en el rango → CITAS_EN_RANGO**

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($e: BloqueoInput!) { crearBloqueo(entrada: $e) { id inicio fin motivo } }
```

Variables: `{"e":{"profesionalId":"33333333-3333-3333-3333-333333333333","inicio":"2026-12-08T13:00:00Z","fin":"2026-12-08T23:00:00Z","motivo":"Capacitación"}}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "crearBloqueo": {
      "id": "0c3d605a-835c-40ce-be99-97602652ca73",
      "inicio": "2026-12-08T13:00:00Z",
      "fin": "2026-12-08T23:00:00Z",
      "motivo": "Capacitación"
    }
  }
}
```

- ✅ **crearBloqueo en un rango libre**

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
query($p: ID!) { bloqueos(profesionalId: $p) { inicio fin motivo } }
```

Variables: `{"p":"33333333-3333-3333-3333-333333333333"}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "bloqueos": [
      {
        "inicio": "2026-12-08T13:00:00Z",
        "fin": "2026-12-08T23:00:00Z",
        "motivo": "Capacitación"
      }
    ]
  }
}
```

- ✅ **bloqueos (antes no tenía resolver y fallaba)**

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($c: ID!) { cancelarCita(citaId: $c, motivo: "El profesional se incapacitó") { id estado horaLocal clienteCelular } }
```

Variables: `{"c":"d491fd82-bef2-4186-b9ba-31f724cdfcfd"}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "cancelarCita": {
      "id": "d491fd82-bef2-4186-b9ba-31f724cdfcfd",
      "estado": "CANCELADA",
      "horaLocal": "08:00",
      "clienteCelular": "300****567"
    }
  }
}
```

- ✅ **cancelarCita**

Registrar asistencia sobre la cita recién cancelada: la regla la aplica agenda-service y el gateway devuelve su mensaje.

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($c: ID!) { registrarAsistencia(citaId: $c, estado: ATENDIDA) { estado } }
```

Variables: `{"c":"d491fd82-bef2-4186-b9ba-31f724cdfcfd"}`

**Respuesta** HTTP 200

```json
{
  "errors": [
    {
      "message": "Esa cita está cancelada; no se puede registrar un resultado.",
      "locations": [
        {
          "line": 1,
          "column": 21
        }
      ],
      "path": [
        "registrarAsistencia"
      ],
      "extensions": {
        "codigo": "DATOS_INVALIDOS",
        "classification": "BAD_REQUEST"
      }
    }
  ],
  "data": null
}
```

- ✅ **registrarAsistencia sobre cita cancelada → error de dominio en español**

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($c: ID!) { registrarAsistencia(citaId: $c, estado: NO_ASISTIO) { estado clienteNombre } }
```

Variables: `{"c":"ff15de64-b373-4876-98a0-4053e1ab61f3"}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "registrarAsistencia": {
      "estado": "NO_ASISTIO",
      "clienteNombre": "María Gómez"
    }
  }
}
```

- ✅ **registrarAsistencia NO_ASISTIO**

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($id: ID!) { desactivarServicio(id: $id) }
```

Variables: `{"id":"6201be8c-95e3-4340-a05a-7859f3409570"}`

**Respuesta** HTTP 200

```json
{
  "data": {
    "desactivarServicio": true
  }
}
```

- ✅ **desactivarServicio**

**Log de notificaciones-service** (la cancelación del panel llegó por `citas.canceladas`):

```text
2026-10-29T01:52:05.183Z  INFO 1 --- [notificaciones-service] [ntainer#1-0-C-1] c.e.f.a.n.c.CitasEventoConsumidor        : citas.canceladas procesado: evento 7bf5859e-9257-480b-a5fc-f14275daab7f cita d491fd82-bef2-4186-b9ba-31f724cdfcfd correlationId ae81480c-5cee-44f1-a9d6-94ee4ca78b12
```

- ✅ **notificaciones-service consumió la cancelación hecha desde el panel**

## 6. Manejo de errores

Formato estándar de GraphQL: `message` en español, `extensions.codigo` y `extensions.classification`. Nunca un stacktrace.

### 6.1 Sin la cabecera `X-Negocio-Id`

**Petición** `POST /graphql` · **sin cabeceras**

```graphql
{ negocio { nombre } }
```

**Respuesta** HTTP 200

```json
{
  "errors": [
    {
      "message": "Falta la cabecera X-Negocio-Id con el identificador del negocio.",
      "locations": [
        {
          "line": 1,
          "column": 3
        }
      ],
      "path": [
        "negocio"
      ],
      "extensions": {
        "codigo": "DATOS_INVALIDOS",
        "classification": "BAD_REQUEST"
      }
    }
  ],
  "data": null
}
```

- ✅ **Sin X-Negocio-Id → DATOS_INVALIDOS**

### 6.2 Un negocio que no existe

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 99999999-9999-9999-9999-999999999999`

```graphql
{ negocio { nombre } }
```

**Respuesta** HTTP 200

```json
{
  "errors": [
    {
      "message": "No encontramos ese negocio.",
      "locations": [
        {
          "line": 1,
          "column": 3
        }
      ],
      "path": [
        "negocio"
      ],
      "extensions": {
        "codigo": "NO_ENCONTRADO",
        "classification": "NOT_FOUND"
      }
    }
  ],
  "data": null
}
```

- ✅ **Negocio inexistente → NO_ENCONTRADO (el 404 de agenda-service, traducido)**

### 6.3 Validación de entrada en el gateway

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
mutation($e: ServicioInput!) { crearServicio(entrada: $e) { id nombre duracionMin precio activo } }
```

Variables: `{"e":{"nombre":"X","duracionMin":2,"precio":-5}}`

**Respuesta** HTTP 200

```json
{
  "errors": [
    {
      "message": "La duración mínima es de 5 minutos.",
      "locations": [
        {
          "line": 1,
          "column": 32
        }
      ],
      "path": [
        "crearServicio"
      ],
      "extensions": {
        "codigo": "DATOS_INVALIDOS",
        "classification": "BAD_REQUEST"
      }
    }
  ],
  "data": null
}
```

- ✅ **Input inválido → DATOS_INVALIDOS sin llegar a agenda-service**

### 6.4 Un ID mal formado

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
{ agenda(profesionalId: "../../v1/negocio", fecha: "2026-10-30") { id } }
```

**Respuesta** HTTP 200

```json
{
  "errors": [
    {
      "message": "El identificador de el profesional no es válido.",
      "locations": [
        {
          "line": 1,
          "column": 3
        }
      ],
      "path": [
        "agenda"
      ],
      "extensions": {
        "codigo": "DATOS_INVALIDOS",
        "classification": "BAD_REQUEST"
      }
    }
  ],
  "data": null
}
```

- ✅ **ID mal formado → DATOS_INVALIDOS**

### 6.5 analitica-service caído: respuesta parcial

Se detiene el contenedor `agd-analitica` y se pide el panel. La agenda sale; `metricasDelMes` llega en `null` y el motivo en `errors[]`. Antes, el gateway devolvía ceros que parecían reales.

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
query($fecha: Date) { panelRecepcion(fecha: $fecha) { citasDelDia { horaLocal clienteNombre estado } metricasDelMes { totalCitas } } }
```

Variables: `{"fecha":"2026-10-30"}`

**Respuesta** HTTP 200

```json
{
  "errors": [
    {
      "message": "No pudimos comunicarnos con el servicio de métricas. Intenta de nuevo en unos segundos.",
      "locations": [
        {
          "line": 1,
          "column": 102
        }
      ],
      "path": [
        "panelRecepcion",
        "metricasDelMes"
      ],
      "extensions": {
        "codigo": "SERVICIO_NO_DISPONIBLE",
        "classification": "INTERNAL_ERROR"
      }
    }
  ],
  "data": {
    "panelRecepcion": {
      "citasDelDia": [
        {
          "horaLocal": "08:00",
          "clienteNombre": "Juan Pérez",
          "estado": "CANCELADA"
        },
        {
          "horaLocal": "09:00",
          "clienteNombre": "María Gómez",
          "estado": "NO_ASISTIO"
        }
      ],
      "metricasDelMes": null
    }
  }
}
```

- ✅ **Con analitica caído, la agenda sale y metricasDelMes = null con SERVICIO_NO_DISPONIBLE**

## 7. Seguridad básica

### 7.1 Límite de profundidad

**Petición** `POST /graphql` · cabeceras: `X-Negocio-Id: 11111111-1111-1111-1111-111111111111`

```graphql
{ negocio { profesionales { agenda { profesional { agenda { profesional { agenda { profesional { agenda { profesional { agenda { profesional { agenda { profesional { agenda { profesional { agenda { profesional { id } } } } } } } } } } } } } } } } } } }
```

**Respuesta** HTTP 200

```json
{
  "errors": [
    {
      "message": "La consulta es demasiado profunda: tiene 19 niveles y el máximo es 15.",
      "extensions": {
        "classification": "ExecutionAborted"
      }
    }
  ]
}
```

- ✅ **Consulta de 19 niveles se corta antes de ejecutarse**

### 7.2 Introspección

La consulta de introspección completa (la que usa GraphiQL) en el perfil de desarrollo → HTTP 200, 32 tipos.

En el perfil `prod` (`docker-compose.prod.yml` → `SPRING_PROFILES_ACTIVE=prod`) la introspección y GraphiQL quedan apagados: ver `gateway-graphql/src/main/resources/application-prod.yml`.

- ✅ **GraphiQL puede introspectar en desarrollo (los límites no lo rompen)**

### 7.3 Rate limiting

Se disparan 130 consultas seguidas desde la misma IP (límite: 120 por minuto, contando las de los pasos anteriores). Rechazadas con 429: **31**.

Ejemplo de rechazo (cabecera `Retry-After: 43`):

```json
{
  "errors": [
    {
      "message": "Hiciste demasiadas consultas seguidas. Espera 43 segundos e intenta de nuevo.",
      "extensions": {
        "codigo": "DEMASIADAS_PETICIONES",
        "classification": "BAD_REQUEST"
      }
    }
  ]
}
```

- ✅ **Rate limit responde 429 con formato de error GraphQL**
