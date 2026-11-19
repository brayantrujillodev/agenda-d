# Manual técnico · instalación, ejecución y uso

Guía para dejar AGENDA-D funcionando desde cero y comprobar que responde.
Todo lo que aparece aquí fue ejecutado y verificado.

## 1. Requisitos

| Requisito | Versión | Nota |
|---|---|---|
| Docker (Desktop o Engine) con Compose v2 | Probado con Docker 29.8 | Es lo único obligatorio |
| Memoria libre | `core`: unos 3 GB; `full`: unos 5 GB | La suma de los `mem_limit` de los contenedores es ≈ 2,3 GB (`core`) y ≈ 4,2 GB (`full`); Docker necesita algo más |
| Puertos libres | 5173, 5432, 5050, 8080, 8081, 8082, 8083, 8090, 29092 | Si alguno está ocupado, el contenedor no arranca |
| Internet | Solo la primera ejecución | Descarga imágenes base y dependencias de Maven |
| Node.js 18 o superior | Opcional | Solo para regenerar evidencia (`node docs/evidencias/flujo-p3.mjs`) y correr Newman |
| Java 21 y Maven 3.9 | Opcional | Solo para correr las pruebas fuera de Docker |

No hace falta instalar Java, Maven, PostgreSQL ni Kafka: los servicios se
construyen dentro de contenedores.

## 2. Instalación y arranque

```bash
# 1. Obtener el código (repositorio) o descomprimir el ZIP y entrar a la carpeta
git clone https://github.com/brayantrujillodev/agenda-d.git
cd agenda-d

# 2. (Opcional) variables de ejemplo. Sin este paso se usan los valores por defecto.
cp .env.example .env

# 3. Levantar todo: base de datos, Kafka, 4 servicios, PWA y consolas
docker compose --profile full up -d --build
```

La primera vez tarda entre 3 y 6 minutos (descarga y compilación). Después
arranca en menos de un minuto. Espera a que todo esté `healthy`:

```bash
docker compose --profile full ps
```

### Orden de arranque (lo resuelve Compose con `depends_on`)

1. `postgres` (con healthcheck) aplica las migraciones y los datos ficticios.
2. `kafka` (KRaft) y `kafka-init`, que crea los tópicos con 3 particiones.
3. `agenda-service` → `notificaciones-service` → `analitica-service`.
4. `gateway-graphql`, que espera a `agenda-service` y `analitica-service`.
5. `web` (nginx) con la PWA.

### Perfiles

| Perfil | Levanta | Cuándo |
|---|---|---|
| `infra` | PostgreSQL, Kafka, tópicos, consola de Kafka, pgAdmin | Para correr los servicios Java con `mvn spring-boot:run` |
| `core` | `infra` + `agenda-service` + PWA | Equipos con poca memoria (8 GB) |
| `full` | Todo | Demostración completa |

### Apagar

```bash
docker compose --profile full down        # conserva los datos
docker compose --profile full down -v     # borra datos y empieza de cero
```

## 3. Puertos y accesos

| Qué | URL |
|---|---|
| **PWA de reserva** | <http://localhost:5173/index.html> |
| **Panel de recepción** | <http://localhost:5173/panel.html> |
| API REST de `agenda-service` | <http://localhost:8081> |
| Swagger UI | <http://localhost:8081/swagger-ui.html> |
| GraphQL (endpoint) | <http://localhost:8080/graphql> |
| GraphiQL | <http://localhost:8080/graphiql> (requiere internet: carga scripts de `unpkg.com`) |
| Métricas | <http://localhost:8083/v1/metricas> |
| Salud de cada servicio | `http://localhost:{8080,8081,8082,8083}/actuator/health` |
| Consola de Kafka | <http://localhost:8090> |
| pgAdmin (sin login) | <http://localhost:5050> |
| PostgreSQL | `localhost:5432`, base `agendad`, usuario `agendad`, contraseña `agendad` (ficticia, solo desarrollo) |

## 4. Uso

### 4.1 Reservar una cita (cliente)

1. Abrir <http://localhost:5173/index.html>.
2. Elegir un servicio y pulsar **Continuar**.
3. Elegir el día y un horario libre (solo se ofrecen horas futuras).
4. Escribir nombre y apellido (solo letras) y un celular de 10 dígitos que empiece por 3.
5. **Confirmar reserva**. Aparece «¡Cita confirmada!» con un enlace para consultar o cancelar sin cuenta.

La reserva exige conexión; no hay cola de reservas sin red, a propósito.

### 4.2 Ver la agenda y los indicadores (negocio)

Abrir <http://localhost:5173/panel.html>. Muestra la agenda del día, los
indicadores del mes y una alerta si hay mensajes en la cola de fallidos.

### 4.3 Probar la API

```bash
# Servicios del negocio de prueba
curl http://localhost:8081/v1/publico/barberia-el-corte/servicios

# Panel completo en una consulta GraphQL (la cabecera identifica al negocio)
curl -s http://localhost:8080/graphql -H "Content-Type: application/json" \
  -H "X-Negocio-Id: 11111111-1111-1111-1111-111111111111" \
  -d '{"query":"{ panelRecepcion { fecha negocio { nombre } citasDelDia { horaLocal estado } } }"}'
```

### 4.4 Comprobar la restricción contra solapes

```bash
docker exec agd-postgres psql -U agendad -d agendad -c \
 "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='cita_sin_solape';"
```

## 5. Pruebas

```bash
# Colección de Postman (51 peticiones) con el stack arriba
npx newman run docs/postman/AGENDA-D.postman_collection.json

# Flujo completo, 31 verificaciones (regenera docs/evidencias/P3-ENTREGA-1.md)
node docs/evidencias/flujo-p3.mjs
```

El gateway admite 120 peticiones por minuto por IP: si corres el flujo y
Newman seguidos, espera un minuto entre uno y otro.

Pruebas unitarias e de integración de un servicio (requieren Docker, porque
usan Testcontainers con Postgres y Kafka reales):

```bash
cd agenda-service && mvn verify
```

Resultados, matriz de casos y capturas: [`docs/PRUEBAS.md`](PRUEBAS.md).

## 6. Variables de entorno

Ver [`.env.example`](../.env.example). Cada variable tiene el mismo valor por
defecto en `docker-compose.yml`, así que el proyecto arranca sin crear `.env`.
Todos los valores son ficticios y de desarrollo; no hay secretos reales en el
repositorio. Para un servidor real se usa `docker-compose.prod.yml` con
imágenes de GHCR y una contraseña de base de datos propia.

## 7. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `port is already allocated` | Otro programa usa el puerto. Cierra ese programa o cambia el puerto izquierdo en `ports:` de `docker-compose.yml`. |
| Un servicio queda `unhealthy` al arrancar | Falta memoria o Kafka no terminó de iniciar. Espera 1 minuto; si sigue, `docker compose --profile full down -v` y vuelve a levantar. |
| La PWA muestra «Sin conexión» | `agenda-service` no está arriba. Revisa `docker compose ps` y `http://localhost:8081/actuator/health`. |
| `/graphiql` queda en «Loading...» | No hay acceso a `unpkg.com`. Usa Swagger, `curl` o la colección de Postman. |
| Respuesta 429 del gateway | Superaste 120 peticiones por minuto desde tu IP. Espera un minuto. |
| Las pruebas Maven fallan con «Could not find a valid Docker environment» | Docker no está accesible o es muy nuevo para la versión de Testcontainers. El proyecto usa Testcontainers 1.21.4, compatible con Docker 29. |

## 8. Limitaciones conocidas

- **Sin autenticación** (fuera de alcance por acuerdo con el docente): quien conozca el `X-Negocio-Id` actúa sobre ese negocio.
- **No hay envío real** de mensajes (SMS, WhatsApp, correo): el aviso se registra en la base de datos.
- **No hay servidor público**: la PWA en GitHub Pages reserva contra un `agenda-service` local.
- La **ocupación** usa una jornada configurable, no el horario real de cada profesional.
- **GraphiQL** depende de un CDN externo.
