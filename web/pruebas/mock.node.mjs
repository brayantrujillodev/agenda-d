/*
 * Prueba del backend simulado (web/mock.js) SIN dependencias: solo Node.
 *
 *     node web/pruebas/mock.node.mjs
 *
 * No es la suite de evaluación (esa es Testcontainers en el backend, Fase 3).
 * Es una red de seguridad para que la PWA no dependa de abrir el navegador
 * para saber si el mock sigue respetando el contrato.
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { webcrypto } from 'node:crypto';
import vm from 'node:vm';

const mockPath = fileURLToPath(new URL('../mock.js', import.meta.url));

function backend() {
  const store = new Map();
  const sandbox = {
    console, setTimeout, URL, URLSearchParams, crypto: webcrypto,
    localStorage: {
      getItem: (k) => (store.has(k) ? store.get(k) : null),
      setItem: (k, v) => store.set(k, String(v)),
      removeItem: (k) => store.delete(k),
    },
  };
  sandbox.window = sandbox;
  sandbox.CONFIG = { apiBase: 'http://localhost:8081', slug: 'barberia-el-corte', useMock: true };
  vm.createContext(sandbox);
  vm.runInContext(readFileSync(mockPath, 'utf8'), sandbox, { filename: 'mock.js' });
  return sandbox.MockBackend;
}

let fallos = 0;
const chk = (n, cond, extra) => {
  console.log((cond ? 'OK   ' : 'FALLA ') + n + (extra !== undefined ? '  ' + JSON.stringify(extra) : ''));
  if (!cond) fallos++;
};

const SERV = '22222222-2222-2222-2222-222222222222';
const uuid = () => webcrypto.randomUUID();
const POST = '/v1/publico/barberia-el-corte/citas';

// fecha futura que caiga en el día indicado (0=dom) según UTC, como el mock
function fechaEnDia(dia, desde = 30) {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + desde);
  while (new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate())).getUTCDay() !== dia) {
    d.setUTCDate(d.getUTCDate() + 1);
  }
  return d.toISOString().slice(0, 10);
}

const MB = backend();
const lunes = fechaEnDia(1);
const domingo = fechaEnDia(0);

let r = await MB.handle('GET', '/v1/publico/barberia-el-corte/servicios');
chk('servicios: 200 + forma de contrato', r.status === 200 && r.body.length === 2 && r.body[0].duracionMin > 0);

r = await MB.handle('GET', `/v1/publico/barberia-el-corte/disponibilidad?servicioId=${SERV}&fecha=${lunes}`);
chk('disponibilidad: 200 + zona + cupos', r.status === 200 && r.body.zonaHoraria === 'America/Bogota' && r.body.cupos.length > 0);
const cupo = r.body.cupos[0];
chk('cupo: inicio UTC y horaLocal = UTC-5',
  cupo.inicio.endsWith('Z') &&
  Number(cupo.horaLocal.slice(0, 2)) === (new Date(cupo.inicio).getUTCHours() + 19) % 24);

// algún cupo mostrado puede ser "fantasma" (409); reservamos hasta dar con uno bueno
let creada = null;
let reserva = null;
for (const c of r.body.cupos) {
  const body = { servicioId: SERV, profesionalId: c.profesionalId, inicio: c.inicio, clienteNombre: 'Juan Perez', clienteCelular: '3001234567' };
  const res = await MB.handle('POST', POST, { headers: { 'Idempotency-Key': uuid() }, body });
  if (res.status === 201) { creada = res; reserva = body; break; }
}
chk('reservar: 201 + CitaCreada + enlaceGestion',
  !!creada && creada.body.estado === 'CONFIRMADA' && creada.body.enlaceGestion.includes('/v1/gestion/'));

r = await MB.handle('POST', POST, { headers: { 'Idempotency-Key': uuid() }, body: { ...reserva, clienteNombre: 'Otra' } });
chk('mismo cupo: 409 CUPO_OCUPADO + alternativas[]',
  r.status === 409 && r.body.codigo === 'CUPO_OCUPADO' && Array.isArray(r.body.alternativas) && r.body.alternativas.length > 0);

const key = uuid();
const a = await MB.handle('POST', POST, { headers: { 'Idempotency-Key': key }, body: { ...reserva, inicio: r.body.alternativas[0].inicio, profesionalId: r.body.alternativas[0].profesionalId } });
const b = await MB.handle('POST', POST, { headers: { 'Idempotency-Key': key }, body: { ...reserva, inicio: r.body.alternativas[0].inicio, profesionalId: r.body.alternativas[0].profesionalId } });
chk('idempotencia: misma clave => misma cita', a.status === 201 && b.status === 201 && a.body.id === b.body.id);

r = await MB.handle('POST', POST, { headers: { 'Idempotency-Key': uuid() }, body: { ...reserva, clienteCelular: '123' } });
chk('celular inválido => 400 DATOS_INVALIDOS', r.status === 400 && r.body.codigo === 'DATOS_INVALIDOS');
r = await MB.handle('POST', POST, { headers: {}, body: reserva });
chk('sin Idempotency-Key => 400', r.status === 400);

const token = creada.body.enlaceGestion.split('/').pop();
r = await MB.handle('GET', `/v1/gestion/${token}`);
chk('gestión GET: 200 + celular enmascarado', r.status === 200 && r.body.clienteCelular === '300****567');
r = await MB.handle('DELETE', `/v1/gestion/${token}`);
chk('cancelar sin confirmar => 400', r.status === 400);
r = await MB.handle('DELETE', `/v1/gestion/${token}?confirmar=true`);
chk('cancelar con confirmar => 204', r.status === 204);
r = await MB.handle('GET', `/v1/gestion/${token}`);
chk('tras cancelar => CANCELADA y cupo liberado', r.status === 200 && r.body.estado === 'CANCELADA');
r = await MB.handle('GET', '/v1/gestion/no-existe');
chk('token inexistente => 404 NO_ENCONTRADO', r.status === 404 && r.body.codigo === 'NO_ENCONTRADO');

r = await MB.handle('GET', `/v1/publico/barberia-el-corte/disponibilidad?servicioId=${SERV}&fecha=${domingo}`);
chk('domingo cerrado => sin cupos', r.status === 200 && r.body.cupos.length === 0);

// cupo "fantasma": se muestra libre en disponibilidad pero rebota al confirmar
const MB2 = backend();
const dia = fechaEnDia(3, 60);
const libres = (await MB2.handle('GET', `/v1/publico/barberia-el-corte/disponibilidad?servicioId=${SERV}&fecha=${dia}`)).body.cupos;
let fantasma = null;
for (const c of libres) {
  const res = await MB2.handle('POST', POST, { headers: { 'Idempotency-Key': uuid() }, body: { servicioId: SERV, profesionalId: c.profesionalId, inicio: c.inicio, clienteNombre: 'Test Fantasma', clienteCelular: '3001112233' } });
  if (res.status === 409) { fantasma = { res, cupo: c }; break; }
}
chk('un cupo mostrado libre => 409 CUPO_OCUPADO con alternativas',
  !!fantasma && fantasma.res.body.codigo === 'CUPO_OCUPADO' && fantasma.res.body.alternativas.length > 0);
if (fantasma) {
  const post = await MB2.handle('GET', `/v1/publico/barberia-el-corte/disponibilidad?servicioId=${SERV}&fecha=${dia}`);
  chk('tras rebotar, ese cupo desaparece de la lista',
    !post.body.cupos.some((c) => c.inicio === fantasma.cupo.inicio && c.profesionalId === fantasma.cupo.profesionalId));
}

console.log(fallos === 0 ? '\n✅ TODO OK' : `\n❌ ${fallos} FALLAS`);
process.exit(fallos ? 1 : 0);
