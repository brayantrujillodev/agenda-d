#!/usr/bin/env node
/*
 * AGENDA-D · Evidencia P3 – Entrega 1 (gateway GraphQL)
 *
 * Recorre el flujo completo contra el stack real levantado con
 *   docker compose --profile full up -d --build
 * y escribe cada petición con su respuesta en docs/evidencias/P3-ENTREGA-1.md.
 * Nada está simulado: si un paso falla, el archivo lo muestra tal cual.
 *
 * Uso:   node docs/evidencias/flujo-p3.mjs
 * Requiere Node 18+ (fetch nativo) y Docker (para el paso de analitica caída).
 */
import { execSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const AGENDA = 'http://localhost:8081';
const GATEWAY = 'http://localhost:8080/graphql';
const ANALITICA = 'http://localhost:8083';
const NOTIFICACIONES = 'http://localhost:8082';
const NEGOCIO = '11111111-1111-1111-1111-111111111111';
const SLUG = 'barberia-el-corte';
const CORTE = '22222222-2222-2222-2222-222222222222';
const LAURA = '33333333-3333-3333-3333-333333333333';
const ANDRES = '33333333-3333-3333-3333-333333333334';

const salida = [];
const resumen = [];
const md = (t = '') => salida.push(t);
const json = (v) => '```json\n' + JSON.stringify(v, null, 2) + '\n```';
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

function check(nombre, ok, detalle = '') {
  resumen.push({ nombre, ok, detalle });
  md(`- ${ok ? '✅' : '❌'} **${nombre}**${detalle ? ' — ' + detalle : ''}`);
  md();
}

async function rest(metodo, url, { cuerpo, cabeceras = {} } = {}) {
  const res = await fetch(url, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', ...cabeceras },
    body: cuerpo ? JSON.stringify(cuerpo) : undefined,
  });
  const texto = await res.text();
  let datos = null;
  try { datos = texto ? JSON.parse(texto) : null; } catch { datos = texto; }
  return { estado: res.status, datos, cabeceras: res.headers };
}

async function gql(query, variables = {}, cabeceras = { 'X-Negocio-Id': NEGOCIO }) {
  return rest('POST', GATEWAY, { cuerpo: { query, variables }, cabeceras });
}

function seccion(titulo, explicacion) {
  md(`## ${titulo}`);
  md();
  if (explicacion) { md(explicacion); md(); }
}

function mostrarGql(query, variables, r, cabeceras = { 'X-Negocio-Id': NEGOCIO }) {
  md('**Petición** `POST /graphql`' + (Object.keys(cabeceras).length
    ? ' · cabeceras: ' + Object.entries(cabeceras).map(([k, v]) => `\`${k}: ${v}\``).join(', ')
    : ' · **sin cabeceras**'));
  md();
  md('```graphql\n' + query.trim() + '\n```');
  md();
  if (variables && Object.keys(variables).length) { md('Variables: `' + JSON.stringify(variables) + '`'); md(); }
  md(`**Respuesta** HTTP ${r.estado}`);
  md();
  md(json(r.datos));
  md();
}

function mostrarRest(metodo, url, cuerpo, r) {
  md(`**Petición** \`${metodo} ${url.replace(/^http:\/\/localhost:\d+/, '')}\``);
  md();
  if (cuerpo) { md(json(cuerpo)); md(); }
  md(`**Respuesta** HTTP ${r.estado}`);
  md();
  md(json(r.datos));
  md();
}

/** Próximo día hábil (lunes a sábado) al menos 2 días adelante, en Bogotá. */
function fechaDePrueba() {
  const ahora = new Date(Date.now() - 5 * 3600 * 1000); // UTC-5, sin DST en Colombia
  const d = new Date(Date.UTC(ahora.getUTCFullYear(), ahora.getUTCMonth(), ahora.getUTCDate() + 2));
  while (d.getUTCDay() === 0) d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}

const errorCodigo = (r) => r.datos?.errors?.map((e) => e.extensions?.codigo).join(',');

// =====================================================================

// `up -d` vuelve cuando los contenedores arrancan, no cuando aceptan peticiones.
for (const base of [AGENDA, NOTIFICACIONES, ANALITICA, 'http://localhost:8080']) {
  for (let i = 0; ; i++) {
    try {
      if ((await fetch(base + '/actuator/health')).ok) break;
    } catch { /* todavía arrancando */ }
    if (i === 60) throw new Error(base + ' no respondió en 2 minutos');
    await esperar(2000);
  }
}

const fecha = fechaDePrueba();
md('# Evidencia · P3 – Entrega 1 · Gateway GraphQL');
md();
md(`Generado por \`docs/evidencias/flujo-p3.mjs\` el ${new Date().toISOString()} contra el stack real`);
md('(`docker compose --profile full up -d --build`). Cada bloque es una petición real y su respuesta');
md('sin editar. Fecha de prueba: `' + fecha + '` (hora local de Bogotá).');
md();
md('<!-- RESUMEN -->');
md();

// --- 0. Salud ----------------------------------------------------------
seccion('0. Los cuatro servicios están arriba', 'Un solo comando levanta todo; cada servicio expone `/actuator/health`.');
for (const [nombre, base] of [['agenda-service', AGENDA], ['notificaciones-service', NOTIFICACIONES],
  ['analitica-service', ANALITICA], ['gateway-graphql', 'http://localhost:8080']]) {
  const r = await rest('GET', base + '/actuator/health');
  md(`- \`${nombre}\` → HTTP ${r.estado} \`${JSON.stringify(r.datos)}\``);
  resumen.push({ nombre: `${nombre} saludable`, ok: r.estado === 200 && r.datos?.status === 'UP' });
}
md();

// --- 1. Reserva por REST público --------------------------------------
seccion('1. El cliente reserva por REST público (fuera del gateway)',
  'Reservar no pasa por GraphQL: es la ruta pública, sin cuenta (ver encabezado de `schema.graphqls`).');
const disp = await rest('GET', `${AGENDA}/v1/publico/${SLUG}/disponibilidad?servicioId=${CORTE}&fecha=${fecha}&profesionalId=${LAURA}`);
const cupo = disp.datos?.cupos?.[0];
md(`\`GET /v1/publico/${SLUG}/disponibilidad?...\` → HTTP ${disp.estado}, ${disp.datos?.cupos?.length ?? 0} cupos. Se toma el primero: \`${cupo?.inicio}\` (${cupo?.horaLocal}).`);
md();
const reservaCuerpo = { servicioId: CORTE, profesionalId: LAURA, inicio: cupo?.inicio, clienteNombre: 'Juan Pérez', clienteCelular: '3001234567' };
const reserva = await rest('POST', `${AGENDA}/v1/publico/${SLUG}/citas`, { cuerpo: reservaCuerpo, cabeceras: { 'Idempotency-Key': randomUUID() } });
mostrarRest('POST', `${AGENDA}/v1/publico/${SLUG}/citas`, reservaCuerpo, reserva);
check('Reserva creada', reserva.estado === 201, `cita ${reserva.datos?.id}`);
const citaId = reserva.datos?.id;

// Segunda cita, con Andrés, para ver la composición de varios profesionales.
const disp2 = await rest('GET', `${AGENDA}/v1/publico/${SLUG}/disponibilidad?servicioId=${CORTE}&fecha=${fecha}&profesionalId=${ANDRES}`);
const cupo2 = disp2.datos?.cupos?.[1];
const reserva2 = await rest('POST', `${AGENDA}/v1/publico/${SLUG}/citas`, {
  cuerpo: { servicioId: CORTE, profesionalId: ANDRES, inicio: cupo2?.inicio, clienteNombre: 'María Gómez', clienteCelular: '3109876543' },
  cabeceras: { 'Idempotency-Key': randomUUID() },
});
md(`Segunda reserva, con Andrés a las ${cupo2?.horaLocal}, para que el panel componga a dos profesionales.`);
md();

// --- 2. Outbox → Kafka → analitica ------------------------------------
seccion('2. El evento llega a analitica por outbox → Kafka',
  'Se espera a que el relay publique `citas.reservadas` y analitica-service lo proyecte.');
let metricas;
for (let i = 0; i < 20; i++) {
  metricas = await rest('GET', `${ANALITICA}/v1/metricas?desde=${fecha.slice(0, 8)}01&hasta=${fecha}`, { cabeceras: { 'X-Negocio-Id': NEGOCIO } });
  if ((metricas.datos?.totalCitas ?? 0) >= 2) break;
  await esperar(1500);
}
mostrarRest('GET', `${ANALITICA}/v1/metricas?desde=${fecha.slice(0, 8)}01&hasta=${fecha}`, null, metricas);
check('analitica-service proyectó las reservas', (metricas.datos?.totalCitas ?? 0) >= 2,
  'topServicios trae solo `servicioId`: el nombre lo pone el gateway');

// --- 3. panelRecepcion ------------------------------------------------
seccion('3. `panelRecepcion`: una consulta, dos servicios',
  'Agenda del día de **todos** los profesionales (agenda-service) + configuración del negocio (agenda-service) + métricas del mes (analitica-service), con el nombre de cada servicio de `topServicios` resuelto por el gateway.');
const QUERY_PANEL = `
query Panel($fecha: Date) {
  panelRecepcion(fecha: $fecha) {
    fecha
    negocio { nombre zonaHoraria }
    citasDelDia { id horaLocal clienteNombre clienteCelular estado servicio { nombre } profesional { nombre } }
    metricasDelMes { desde hasta totalCitas atendidas noAsistio canceladas ocupacion tasaInasistencia
                     topServicios { cantidad servicio { nombre } } }
  }
}`;
const panel = await gql(QUERY_PANEL, { fecha });
mostrarGql(QUERY_PANEL, { fecha }, panel);
const citas = panel.datos?.data?.panelRecepcion?.citasDelDia ?? [];
check('panelRecepcion compone agenda y métricas sin errores', panel.estado === 200 && !panel.datos?.errors && citas.length >= 2);
check('Celular enmascarado según el contrato GraphQL', citas.every((c) => /^\d{3}\*{4}\d{3}$/.test(c.clienteCelular)));
check('topServicios con nombre real (no un UUID)',
  panel.datos?.data?.panelRecepcion?.metricasDelMes?.topServicios?.[0]?.servicio?.nombre === 'Corte de cabello');

// --- 4. Queries sueltas -----------------------------------------------
seccion('4. Otras consultas');
const QUERY_NEGOCIO = `{ negocio { nombre slugPublico servicios { nombre duracionMin precio activo }
  profesionales { nombre servicios { nombre } horarios { diaSemana horaInicio horaFin } } } }`;
const neg = await gql(QUERY_NEGOCIO);
mostrarGql(QUERY_NEGOCIO, null, neg);
check('negocio sale de agenda-service, no de datos fijos en el código', neg.datos?.data?.negocio?.nombre === 'Barbería El Corte');

const QUERY_AGENDA = `query($p: ID!, $f: Date!) { agenda(profesionalId: $p, fecha: $f) { horaLocal clienteNombre estado } }`;
const ag = await gql(QUERY_AGENDA, { p: ANDRES, f: fecha });
mostrarGql(QUERY_AGENDA, { p: ANDRES, f: fecha }, ag);
check('agenda(profesionalId) funciona con cualquier profesional, no solo uno fijo', (ag.datos?.data?.agenda?.length ?? 0) >= 1);

// --- 5. Mutaciones ----------------------------------------------------
seccion('5. Mutaciones', 'Cada una es una llamada a agenda-service; los eventos los publica agenda-service por su outbox.');
const M_SERV = `mutation($e: ServicioInput!) { crearServicio(entrada: $e) { id nombre duracionMin precio activo } }`;
const vServ = { e: { nombre: 'Corte y barba', duracionMin: 90, precio: 35000 } };
const serv = await gql(M_SERV, vServ);
mostrarGql(M_SERV, vServ, serv);
const servicioNuevo = serv.datos?.data?.crearServicio?.id;
check('crearServicio', !!servicioNuevo);

const M_ACT = `mutation($id: ID!, $e: ServicioInput!) { actualizarServicio(id: $id, entrada: $e) { nombre precio } }`;
const vAct = { id: servicioNuevo, e: { nombre: 'Corte y barba premium', duracionMin: 90, precio: '40000' } };
const act = await gql(M_ACT, vAct);
mostrarGql(M_ACT, vAct, act);
check('actualizarServicio', act.datos?.data?.actualizarServicio?.precio === '40000.00');

const M_PROF = `mutation($e: ProfesionalInput!) { crearProfesional(entrada: $e) { id nombre activo servicios { nombre } horarios { diaSemana } } }`;
const vProf = { e: { nombre: 'Camila', serviciosIds: [servicioNuevo, CORTE] } };
const prof = await gql(M_PROF, vProf);
mostrarGql(M_PROF, vProf, prof);
const camila = prof.datos?.data?.crearProfesional?.id;
check('crearProfesional', !!camila);

const M_HOR = `mutation($p: ID!, $h: [HorarioInput!]!) { definirHorario(profesionalId: $p, horarios: $h) { nombre horarios { diaSemana horaInicio horaFin } } }`;
const vHor = { p: camila, h: [{ diaSemana: 1, horaInicio: '08:00', horaFin: '12:00' }, { diaSemana: 1, horaInicio: '14:00', horaFin: '18:00' }, { diaSemana: 3, horaInicio: '09:00', horaFin: '17:00' }] };
const hor = await gql(M_HOR, vHor);
mostrarGql(M_HOR, vHor, hor);
check('definirHorario', hor.datos?.data?.definirHorario?.horarios?.length === 3);

const inicioCita = reserva.datos?.inicio;
const M_BLOQ = `mutation($e: BloqueoInput!) { crearBloqueo(entrada: $e) { id inicio fin motivo } }`;
const vBloqMal = { e: { profesionalId: LAURA, inicio: new Date(Date.parse(inicioCita) - 3600e3).toISOString(), fin: new Date(Date.parse(inicioCita) + 3600e3).toISOString(), motivo: 'Incapacidad' } };
const bloqMal = await gql(M_BLOQ, vBloqMal);
md('Bloquear un rango que contiene la cita de Juan Pérez: agenda-service lo rechaza y el gateway expone su código tal cual.');
md();
mostrarGql(M_BLOQ, vBloqMal, bloqMal);
check('crearBloqueo con citas en el rango → CITAS_EN_RANGO', errorCodigo(bloqMal) === 'CITAS_EN_RANGO');

const lejos = new Date(Date.now() + 40 * 86400e3);
const vBloq = { e: { profesionalId: LAURA, inicio: lejos.toISOString().slice(0, 10) + 'T13:00:00Z', fin: lejos.toISOString().slice(0, 10) + 'T23:00:00Z', motivo: 'Capacitación' } };
const bloq = await gql(M_BLOQ, vBloq);
mostrarGql(M_BLOQ, vBloq, bloq);
check('crearBloqueo en un rango libre', !!bloq.datos?.data?.crearBloqueo?.id);

const Q_BLOQ = `query($p: ID!) { bloqueos(profesionalId: $p) { inicio fin motivo } }`;
const bl = await gql(Q_BLOQ, { p: LAURA });
mostrarGql(Q_BLOQ, { p: LAURA }, bl);
check('bloqueos (antes no tenía resolver y fallaba)', (bl.datos?.data?.bloqueos?.length ?? 0) >= 1);

const M_CANC = `mutation($c: ID!) { cancelarCita(citaId: $c, motivo: "El profesional se incapacitó") { id estado horaLocal clienteCelular } }`;
const canc = await gql(M_CANC, { c: citaId });
mostrarGql(M_CANC, { c: citaId }, canc);
check('cancelarCita', canc.datos?.data?.cancelarCita?.estado === 'CANCELADA');

const M_ASIS = `mutation($c: ID!) { registrarAsistencia(citaId: $c, estado: ATENDIDA) { estado } }`;
const asis = await gql(M_ASIS, { c: citaId });
md('Registrar asistencia sobre la cita recién cancelada: la regla la aplica agenda-service y el gateway devuelve su mensaje.');
md();
mostrarGql(M_ASIS, { c: citaId }, asis);
check('registrarAsistencia sobre cita cancelada → error de dominio en español', errorCodigo(asis) === 'DATOS_INVALIDOS');

const otraCita = reserva2.datos?.id; // la de esta corrida: idempotente si se repite el script
const asis2 = await gql(`mutation($c: ID!) { registrarAsistencia(citaId: $c, estado: NO_ASISTIO) { estado clienteNombre } }`, { c: otraCita });
mostrarGql(`mutation($c: ID!) { registrarAsistencia(citaId: $c, estado: NO_ASISTIO) { estado clienteNombre } }`, { c: otraCita }, asis2);
check('registrarAsistencia NO_ASISTIO', asis2.datos?.data?.registrarAsistencia?.estado === 'NO_ASISTIO');

const M_DES = `mutation($id: ID!) { desactivarServicio(id: $id) }`;
const des = await gql(M_DES, { id: servicioNuevo });
mostrarGql(M_DES, { id: servicioNuevo }, des);
check('desactivarServicio', des.datos?.data?.desactivarServicio === true);

// Los eventos de cancelar y de asistencia también viajan por Kafka.
await esperar(6000);
const notif = execSync('docker logs agd-notificaciones 2>&1').toString().split('\n')
  .filter((l) => l.includes('citas.canceladas procesado') && l.includes(citaId)).slice(-3);
md('**Log de notificaciones-service** (la cancelación del panel llegó por `citas.canceladas`):');
md();
md('```text\n' + (notif.join('\n') || '(sin líneas)') + '\n```');
md();
check('notificaciones-service consumió la cancelación hecha desde el panel', notif.length > 0);

// --- 6. Errores -------------------------------------------------------
seccion('6. Manejo de errores', 'Formato estándar de GraphQL: `message` en español, `extensions.codigo` y `extensions.classification`. Nunca un stacktrace.');

const Q_SIMPLE = `{ negocio { nombre } }`;
const sinCab = await gql(Q_SIMPLE, {}, {});
md('### 6.1 Sin la cabecera `X-Negocio-Id`');
md();
mostrarGql(Q_SIMPLE, null, sinCab, {});
check('Sin X-Negocio-Id → DATOS_INVALIDOS', errorCodigo(sinCab) === 'DATOS_INVALIDOS');

const ajeno = '99999999-9999-9999-9999-999999999999';
const otro = await gql(Q_SIMPLE, {}, { 'X-Negocio-Id': ajeno });
md('### 6.2 Un negocio que no existe');
md();
mostrarGql(Q_SIMPLE, null, otro, { 'X-Negocio-Id': ajeno });
check('Negocio inexistente → NO_ENCONTRADO (el 404 de agenda-service, traducido)', errorCodigo(otro) === 'NO_ENCONTRADO');

const vInv = { e: { nombre: 'X', duracionMin: 2, precio: -5 } };
const inv = await gql(M_SERV, vInv);
md('### 6.3 Validación de entrada en el gateway');
md();
mostrarGql(M_SERV, vInv, inv);
check('Input inválido → DATOS_INVALIDOS sin llegar a agenda-service', errorCodigo(inv) === 'DATOS_INVALIDOS');

const Q_ID = `{ agenda(profesionalId: "../../v1/negocio", fecha: "${fecha}") { id } }`;
const idMal = await gql(Q_ID);
md('### 6.4 Un ID mal formado');
md();
mostrarGql(Q_ID, null, idMal);
check('ID mal formado → DATOS_INVALIDOS', errorCodigo(idMal) === 'DATOS_INVALIDOS');

md('### 6.5 analitica-service caído: respuesta parcial');
md();
md('Se detiene el contenedor `agd-analitica` y se pide el panel. La agenda sale; `metricasDelMes` llega en `null` y el motivo en `errors[]`. Antes, el gateway devolvía ceros que parecían reales.');
md();
execSync('docker stop agd-analitica', { stdio: 'ignore' });
const QUERY_PARCIAL = `query($fecha: Date) { panelRecepcion(fecha: $fecha) { citasDelDia { horaLocal clienteNombre estado } metricasDelMes { totalCitas } } }`;
const parcial = await gql(QUERY_PARCIAL, { fecha });
mostrarGql(QUERY_PARCIAL, { fecha }, parcial);
check('Con analitica caído, la agenda sale y metricasDelMes = null con SERVICIO_NO_DISPONIBLE',
  errorCodigo(parcial) === 'SERVICIO_NO_DISPONIBLE' && parcial.datos?.data?.panelRecepcion?.metricasDelMes === null
  && (parcial.datos?.data?.panelRecepcion?.citasDelDia?.length ?? 0) >= 1);
execSync('docker start agd-analitica', { stdio: 'ignore' });

// --- 7. Seguridad básica ----------------------------------------------
seccion('7. Seguridad básica');
md('### 7.1 Límite de profundidad');
md();
const profunda = '{ negocio { profesionales { ' + 'agenda { profesional { '.repeat(8) + 'id' + ' }'.repeat(19);
const prof2 = await gql(profunda);
mostrarGql(profunda, null, prof2);
check('Consulta de 19 niveles se corta antes de ejecutarse', JSON.stringify(prof2.datos).includes('demasiado profunda'));

md('### 7.2 Introspección');
md();
const INTRO = `query IntrospectionQuery { __schema { queryType { name } mutationType { name }
  types { ...FullType } directives { name locations args { ...InputValue } } } }
fragment FullType on __Type { kind name fields(includeDeprecated: true) { name args { ...InputValue } type { ...TypeRef } }
  inputFields { ...InputValue } interfaces { ...TypeRef } enumValues(includeDeprecated: true) { name } possibleTypes { ...TypeRef } }
fragment InputValue on __InputValue { name type { ...TypeRef } defaultValue }
fragment TypeRef on __Type { kind name ofType { kind name ofType { kind name ofType { kind name ofType { kind name
  ofType { kind name ofType { kind name ofType { kind name } } } } } } } }`;
const intro = await gql(INTRO);
const tipos = intro.datos?.data?.__schema?.types?.length;
md(`La consulta de introspección completa (la que usa GraphiQL) en el perfil de desarrollo → HTTP ${intro.estado}, ${tipos ?? 0} tipos${intro.datos?.errors ? ', errores: ' + JSON.stringify(intro.datos.errors) : ''}.`);
md();
md('En el perfil `prod` (`docker-compose.prod.yml` → `SPRING_PROFILES_ACTIVE=prod`) la introspección y GraphiQL quedan apagados: ver `gateway-graphql/src/main/resources/application-prod.yml`.');
md();
check('GraphiQL puede introspectar en desarrollo (los límites no lo rompen)', (tipos ?? 0) > 10);

md('### 7.3 Rate limiting');
md();
let rechazadas = 0;
let ejemplo = null;
const lote = await Promise.all(Array.from({ length: 130 }, () => gql('{ __typename }')));
for (const r of lote) {
  if (r.estado === 429) { rechazadas++; ejemplo = ejemplo || r; }
}
md(`Se disparan 130 consultas seguidas desde la misma IP (límite: 120 por minuto, contando las de los pasos anteriores). Rechazadas con 429: **${rechazadas}**.`);
md();
if (ejemplo) {
  md(`Ejemplo de rechazo (cabecera \`Retry-After: ${ejemplo.cabeceras.get('retry-after')}\`):`);
  md();
  md(json(ejemplo.datos));
  md();
}
check('Rate limit responde 429 con formato de error GraphQL', rechazadas > 0 && ejemplo?.datos?.errors?.[0]?.extensions?.codigo === 'DEMASIADAS_PETICIONES');

// --- Resumen ----------------------------------------------------------
const ok = resumen.filter((r) => r.ok).length;
const tabla = ['## Resumen', '', `**${ok} de ${resumen.length} verificaciones pasaron.**`, '', '| Verificación | Resultado |', '|---|---|',
  ...resumen.map((r) => `| ${r.nombre} | ${r.ok ? '✅' : '❌'} |`)].join('\n');
const final = salida.join('\n').replace('<!-- RESUMEN -->', tabla);
const destino = join(dirname(fileURLToPath(import.meta.url)), 'P3-ENTREGA-1.md');
writeFileSync(destino, final);
console.log(`${ok}/${resumen.length} verificaciones OK → ${destino}`);
for (const r of resumen.filter((x) => !x.ok)) console.log('  FALLÓ:', r.nombre);
process.exit(ok === resumen.length ? 0 : 1);
