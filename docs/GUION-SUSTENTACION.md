# Guion de sustentación · AGENDA-D

El profesor elige a **uno solo** y la nota es grupal. Esto lo leen los cuatro.

No memorices frases: entiende las cinco ideas de abajo. Con eso respondes casi
todo, venga la pregunta por donde venga.

---

# PARTE 1 · Las cinco ideas

Si entiendes esto, lo demás sale solo.

### 1. Por qué el sistema está partido en pedazos

Reservar es lo único que el cliente está esperando en pantalla. Confirmarle,
avisarle al profesional, recordarle mañana y recalcular las métricas **le pasan
después** y a él no le importan en ese instante.

Si todo eso va dentro de la misma petición, la lentitud del servicio de
mensajería termina impidiendo agendar. Por eso: lo que el usuario necesita para
continuar va en la petición; lo demás va en un evento.

> Frase para decir: *«Si el usuario no necesita el resultado para continuar,
> no va en la petición, va en un evento.»*

### 2. Dos personas no pueden quedar a la misma hora

Y esto **no lo decide el código**, lo decide la base de datos.

Si en Java hacemos «consulto si está libre, y si está libre inserto», entre esas
dos líneas cabe otra petición. Dos clientes pueden pasar la verificación a la vez.

Un `UNIQUE(profesional_id, inicio)` tampoco sirve: una cita de 10:00 a 11:00 y
otra que empieza a las 10:30 tienen **horas de inicio distintas** y aun así se
pisan. Por eso comparamos rangos, no instantes.

```sql
EXCLUDE USING gist (
    profesional_id WITH =,
    tstzrange(inicio, fin, '[)') WITH &&
) WHERE (estado <> 'CANCELADA')
```

El primero que llega gana. Al segundo la base de datos le rechaza la escritura y
el servicio lo traduce a un `409` con los cupos más cercanos, no a un error feo.

> El `WHERE` es para que un cupo cancelado vuelva a estar libre.

### 3. Ninguna cita se pierde en silencio (outbox)

Guardar la cita y publicar el evento son dos cosas que pueden fallar por
separado. Si publicáramos directo a Kafka y Kafka estuviera caído, el cliente
quedaría con una cita confirmada de la que el profesional nunca se entera.

Por eso el `INSERT` en `cita` y el `INSERT` en `outbox` van **en la misma
transacción**: o las dos, o ninguna. Después una tarea periódica lee lo
pendiente, lo publica y lo marca como enviado.

> Esto permite apagar Kafka, seguir reservando, y que al prenderlo se publique
> todo. **Es la demostración más fuerte que tenemos.**

### 4. Cómo sabemos de qué negocio es cada petición, si no hay login

El profesor mismo nos dijo que quitáramos la autenticación. Pero el sistema
igual necesita saber a qué negocio pertenece cada cosa:

- **Ruta pública:** el negocio sale del propio enlace →
  `/v1/publico/barberia-el-corte/disponibilidad`
- **Ruta administrativa:** viaja en la cabecera `X-Negocio-Id`

Cuando se agregue login, lo único que cambia es **de dónde sale esa cabecera**.
Ni el modelo de datos, ni las consultas, ni los contratos se tocan.

### 5. El gateway no hace nada del negocio

Solo junta. El panel necesita en una sola pantalla la agenda del día, la
configuración y las métricas del mes, y eso vive en **dos servicios distintos**.
Con REST puro serían 3 o 4 peticiones desde un celular con mala señal.

El gateway no tiene base de datos, no valida reglas, no publica eventos. Si un
resolver necesitara consultar una tabla, la responsabilidad estaría mal puesta.

---

# PARTE 2 · Números que hay que tener en la cabeza

| Cosa | Dato |
|---|---|
| Servicios | 3 (agenda, notificaciones, analitica) + gateway |
| Puertos | 8080 gateway · 8081 agenda · 8082 notificaciones · 8083 analitica |
| Contenedores | 6, más la consola de Kafka solo en desarrollo |
| Tópicos | 3: `citas.reservadas`, `citas.canceladas`, `citas.estado` + DLQ |
| Clave de partición | `profesionalId` |
| Endpoints REST | 8 |
| GraphQL | 15 tipos, 5 consultas, 8 mutaciones |
| Requisitos | 11 funcionales, 11 no funcionales |
| Desempeño | p95 < 300 ms reservar · < 200 ms consultar cupos |
| Prueba de concurrencia | 100 hilos: 1 con `201`, 99 con `409` |
| Base de datos | PostgreSQL 16, un esquema por servicio |
| Zona horaria | Todo en UTC; `America/Bogota` para mostrar |

---

# PARTE 3 · Preguntas probables

Respuestas de dos o tres frases. No las recites: entiéndelas.

### Sobre la arquitectura

**¿Por qué no un monolito?**
Porque mezcla tres cargas distintas: la reserva, que es el camino crítico y
tiene concurrencia real; las notificaciones y métricas, que pueden ocurrir
después; y los recordatorios, que son trabajo programado. Si van juntos, la
falla de la más lenta afecta a la más crítica.

**¿Por qué solo tres servicios? ¿No es poco?**
Evaluamos una versión con siete y la descartamos. Somos cuatro estudiantes con
un semestre. Una arquitectura que no llega a entregarse no es superior.
Distribuimos lo que tiene una razón de negocio para estar separado: lo que el
usuario espera en pantalla frente a lo que puede ocurrir después.

**¿Por qué la configuración del negocio no es un servicio aparte?**
Porque el cálculo de disponibilidad la consulta en cada operación. Separarla
metería una llamada de red dentro del camino crítico. La decisión es reversible:
si algún día estorba, se saca sin cambiar los contratos.

**¿Qué responsabilidad NO debe asumir el gateway?**
Lógica de negocio, persistencia y publicación de eventos. Solo compone y
propaga el contexto.

**¿Por qué reservar no pasa por GraphQL?**
Es la operación más frecuente, la usan personas sin cuenta y no debe depender de
un componente pensado para el panel administrativo. Va directo a `agenda-service`
por REST.

### Sobre la concurrencia

**¿Cómo evitan que dos clientes tomen el mismo cupo?**
→ Idea 2. Restricción de exclusión en PostgreSQL sobre rangos temporales.

**¿Por qué no lo validan en el código?**
Entre el `SELECT` que pregunta si está libre y el `INSERT` que escribe, cabe otra
petición. La garantía tiene que estar donde la escritura es atómica.

**¿Y con `synchronized` o un bloqueo?**
`synchronized` solo funciona dentro de una instancia; con dos instancias del
servicio deja de servir. Un bloqueo pesimista funcionaría pero serializa el
acceso y castiga a todos los usuarios para proteger un caso raro.

**¿Cómo lo prueban?**
100 hilos reservando el mismo cupo, con Testcontainers sobre Postgres real.
Exactamente uno obtiene `201` y 99 obtienen `409`.

### Sobre Kafka

**¿Por qué esos tres tópicos?**
Corresponden a hechos del negocio, no a decisiones técnicas: una cita entró, una
cita se canceló, una cita se cerró. Se le pueden explicar al dueño de la barbería.

**¿Por qué particionan por `profesionalId`?**
Kafka garantiza el orden **dentro de una partición**. Al usar esa clave, todos
los eventos de un mismo profesional caen en la misma partición, y una
cancelación nunca se procesa antes que la reserva que la origina.

**¿Qué pasa si un evento llega dos veces?**
Kafka entrega «al menos una vez», así que pasa. Cada evento tiene un `eventoId`
y los consumidores lo verifican contra una tabla de procesados antes de actuar.

**¿Qué pasa si se cae Kafka?**
Se sigue reservando. Los eventos quedan en la tabla outbox y se publican cuando
vuelva. Lo podemos demostrar en vivo.

**¿Qué pasa si un consumidor falla siempre?**
Tres reintentos con espera creciente y después el mensaje va a la DLQ, con
alerta en el panel. Así no se pierde ni bloquea la partición.

**¿Por qué JSON y no Avro?**
Avro añade un contenedor y una curva de aprendizaje que no se justifican para
tres tipos de evento. Usamos JSON con un campo `version` para poder evolucionar.

### Sobre los datos

**¿Por qué PostgreSQL y no algo NoSQL?**
Porque necesitamos una garantía transaccional fuerte y el tipo `tstzrange` con
restricciones de exclusión, que es justamente lo que impide el solapamiento.

**¿Una base de datos por servicio?**
Una instancia con un esquema por servicio, cada uno con su usuario y sin llaves
foráneas cruzadas. Es por consumo de memoria en nuestros equipos. Separarlo
después no exige cambiar código.

**¿Por qué todo en UTC?**
El dominio entero son horarios. Si guardáramos hora local, un cambio de zona o
un horario de frontera desplazaría todas las citas. Guardamos en UTC y
mostramos en la zona del negocio.

**¿Qué es la cabecera `Idempotency-Key`?**
Si el celular pierde señal y el cliente reintenta, la petición puede llegar dos
veces. Con esa cabecera la segunda devuelve la cita original en lugar de crear
otra.

### Sobre el alcance

**¿Por qué no hay login?**
Por indicación suya: es mucho trabajo y no aporta al aprendizaje de
arquitecturas distribuidas. → Idea 4 para explicar cómo lo resolvimos.

**¿Cómo evitan que un negocio vea datos de otro sin login?**
Todas las entidades llevan `negocio_id` y toda consulta lo aplica desde el
contexto de la petición. Hay una prueba negativa que intenta leer una cita de
otro negocio por su identificador directo y debe devolver vacío.

**¿La PWA funciona sin internet?**
Se instala y la interfaz queda cacheada, pero **reservar exige conexión**.
Permitir reservas offline generaría conflictos irresolubles: dos personas
tomando el mismo cupo sin red, y al sincronizar uno pierde una cita que creía
confirmada.

**¿Cómo llega el recordatorio al cliente?**
El servicio no está acoplado a ningún proveedor: define una interfaz de canal
con adaptadores. Durante el semestre usamos el que registra en base de datos,
sin costo. WhatsApp es el canal objetivo pero exige cuenta verificada,
plantillas aprobadas y pago por conversación.

**¿Y si el servicio se reinicia antes de enviar el recordatorio?**
Las programaciones están en tabla, no en memoria. Al arrancar recupera las
pendientes y envía las vencidas.

### Sobre su retroalimentación

**Les señalé una inconsistencia con el UNIQUE. ¿Qué hicieron?**
La corregimos en el diagrama. Y aprovechamos para dejar explicado en el
documento por qué el `UNIQUE` no sirve, para que no se repita la confusión.

**Les dije que no se complicaran. ¿Cómo lo aplicaron?**
Reordenamos el plan. El gateway GraphQL se adelantó al segundo corte porque
usted lo incluyó dentro del flujo mínimo, y la DLQ, los Testcontainers y la
analítica se pasaron al tercero. La única excepción es la restricción de
exclusión, que dejamos desde el inicio porque ya está en la migración y cuesta
prácticamente nada.

---

# PARTE 4 · Las tres trampas

**«¿Y si les pido agregar X?»**
No respondas «sí, fácil». Responde qué se caería a cambio. El documento tiene
una sección de exclusiones justamente para eso.

**«¿Esto ya funciona?»**
Di la verdad y sé preciso sobre qué sí. Decir «va la Fase 1 y estamos cerrando
el flujo mínimo» vale más que un «sí» que se desmonta en la siguiente pregunta.

**«¿Por qué no usaron [tecnología]?»**
Casi siempre la respuesta es la misma: la evaluamos y no se justificaba para
nuestro alcance. Está documentado en decisiones pendientes.

---

# PARTE 5 · Si no sabes algo

Dilo. *«Eso no lo hemos definido todavía, está en decisiones pendientes»* o
*«no manejo ese detalle, lo trabajó [nombre], pero la idea general es…»*.

Un profesor orientado a producción castiga mucho más el invento que el vacío.
Y todo el documento está construido sobre decisiones explicables: si entiendes
las cinco ideas, casi nada te va a tomar por sorpresa.

---

# La demostración, si la piden

En este orden:

1. `docker compose --profile full up -d` — todo arriba
2. Abrir la PWA, reservar una cita
3. Mostrar el evento en la consola de Kafka
4. **Apagar Kafka**, reservar dos citas más, prenderlo → los eventos se publican
5. Correr la prueba de concurrencia: 1 éxito, 99 conflictos

El paso 4 y el 5 son los que impresionan. Ténganlos ensayados.
