/*
 * AGENDA-D · Backend simulado para la PWA
 * -----------------------------------------------------------------------------
 * Responde los mismos endpoints y las mismas formas que
 * docs/openapi/agenda-service.yaml, con los datos de ejemplo del contrato.
 *
 * Existe para que la PWA se pueda construir y sustentar SIN agenda-service.
 * Cuando el backend real exista: CONFIG.useMock = false y este archivo se
 * puede borrar sin tocar app.js.
 *
 * Estado (citas creadas, idempotencia) se guarda en localStorage para que
 * la pantalla de gestión y la demo del 409 sobrevivan a un F5.
 * En consola:  mockReset()  borra ese estado.
 */
(function () {
  'use strict';

  const CLAVE_ESTADO = 'agenda-d.mock.v1';
  const ZONA = 'America/Bogota';
  const ZONA_OFFSET_MIN = -300;          // Bogotá = UTC-5, sin horario de verano
  const ATIENDE_DESDE = 8;               // 08:00 hora local
  const ATIENDE_HASTA = 18;              // 18:00 hora local
  const FANTASMAS_POR_DIA = 2;           // cupos que "se acaban de ocupar" -> 409

  // --- Catálogo fijo (del ejemplo del contrato) ---------------------------
  const SERVICIOS = [
    { id: '22222222-2222-2222-2222-222222222222', nombre: 'Corte de cabello', duracionMin: 60, precio: 25000 },
    { id: '22222222-2222-2222-2222-222222222223', nombre: 'Barba', duracionMin: 30, precio: 15000 },
  ];
  const PROFESIONALES = [
    { id: '33333333-3333-3333-3333-333333333333', nombre: 'Laura' },
    { id: '33333333-3333-3333-3333-333333333334', nombre: 'Carlos' },
  ];

  // --- Estado persistido -------------------------------------------------
  function cargarEstado() {
    try {
      return JSON.parse(localStorage.getItem(CLAVE_ESTADO)) || nuevoEstado();
    } catch (_) {
      return nuevoEstado();
    }
  }
  function nuevoEstado() {
    return { citas: {}, idempotencia: {} };   // citas: token -> cita ; idempotencia: key -> token
  }
  function guardarEstado(e) {
    try { localStorage.setItem(CLAVE_ESTADO, JSON.stringify(e)); } catch (_) {}
  }

  window.mockReset = function () {
    try { localStorage.removeItem(CLAVE_ESTADO); } catch (_) {}
    console.info('[mock] estado borrado. Recarga la página.');
  };

  // --- Utilidades de tiempo -------------------------------------------
  function fechaValida(f) { return /^\d{4}-\d{2}-\d{2}$/.test(f); }

  function localAUtc(fecha, horas, minutos) {
    const [y, m, d] = fecha.split('-').map(Number);
    return new Date(Date.UTC(y, m - 1, d, horas, minutos) - ZONA_OFFSET_MIN * 60000);
  }
  function horaLocalDe(iso) {
    const t = new Date(new Date(iso).getTime() + ZONA_OFFSET_MIN * 60000);
    return String(t.getUTCHours()).padStart(2, '0') + ':' + String(t.getUTCMinutes()).padStart(2, '0');
  }
  function diaSemanaLocal(fecha) {
    const [y, m, d] = fecha.split('-').map(Number);
    return new Date(Date.UTC(y, m - 1, d)).getUTCDay();   // 0 = domingo
  }

  // PRNG determinista: mismos "fantasmas" para (profesional, fecha) en cada carga
  function semilla(str) {
    let h = 1779033703 ^ str.length;
    for (let i = 0; i < str.length; i++) {
      h = Math.imul(h ^ str.charCodeAt(i), 3432918353);
      h = (h << 13) | (h >>> 19);
    }
    return function () {
      h = Math.imul(h ^ (h >>> 16), 2246822507);
      h = Math.imul(h ^ (h >>> 13), 3266489909);
      return ((h ^= h >>> 16) >>> 0) / 4294967296;
    };
  }

  function idxFantasma(profesionalId, fecha, totalRanuras) {
    const r = semilla(profesionalId + '|' + fecha);
    const set = new Set();
    let intentos = 0;
    while (set.size < Math.min(FANTASMAS_POR_DIA, totalRanuras) && intentos < 50) {
      set.add(Math.floor(r() * totalRanuras));
      intentos++;
    }
    return set;
  }

  // --- Generación de cupos ------------------------------------------
  function rejilla(fecha, duracionMin) {
    // devuelve [{hLocal, mLocal}] posibles inicios locales de la jornada
    const out = [];
    for (let min = ATIENDE_DESDE * 60; min + duracionMin <= ATIENDE_HASTA * 60; min += duracionMin) {
      out.push({ h: Math.floor(min / 60), m: min % 60 });
    }
    return out;
  }

  function citasActivas(estado) {
    return Object.values(estado.citas).filter(c => c.estado === 'CONFIRMADA');
  }

  function chocaConCita(estado, profesionalId, iniMs, finMs) {
    return citasActivas(estado).some(c =>
      c.profesionalId === profesionalId &&
      iniMs < new Date(c.fin).getTime() &&
      finMs > new Date(c.inicio).getTime());
  }

  function cuposDelDia(estado, servicio, fecha, filtroProfesional, incluirFantasmas) {
    if (diaSemanaLocal(fecha) === 0) return [];          // domingo cerrado
    const ranuras = rejilla(fecha, servicio.duracionMin);
    const ahora = Date.now();
    const cupos = [];

    for (const prof of PROFESIONALES) {
      if (filtroProfesional && prof.id !== filtroProfesional) continue;
      const fantasmas = idxFantasma(prof.id, fecha, ranuras.length);

      ranuras.forEach((r, i) => {
        const ini = localAUtc(fecha, r.h, r.m);
        const fin = new Date(ini.getTime() + servicio.duracionMin * 60000);
        if (ini.getTime() <= ahora) return;                       // ya pasó
        if (chocaConCita(estado, prof.id, ini.getTime(), fin.getTime())) return;
        const esFantasma = fantasmas.has(i);
        if (esFantasma && !incluirFantasmas) return;              // no lo mostramos, pero al reservar da 409
        cupos.push({
          inicio: ini.toISOString(),
          fin: fin.toISOString(),
          horaLocal: horaLocalDe(ini.toISOString()),
          profesionalId: prof.id,
          profesionalNombre: prof.nombre,
          _fantasma: esFantasma,
        });
      });
    }
    cupos.sort((a, b) => a.inicio.localeCompare(b.inicio) || a.profesionalNombre.localeCompare(b.profesionalNombre));
    return cupos;
  }

  function limpiar(cupo) {
    const { _fantasma, ...visible } = cupo;
    return visible;
  }

  // --- Helpers de identidad ---------------------------------------
  function uuid() {
    return (crypto.randomUUID && crypto.randomUUID()) ||
      '44444444-4444-4444-4444-' + Date.now().toString(16).padStart(12, '0');
  }
  function token() {
    const b = new Uint8Array(8);
    crypto.getRandomValues(b);
    return Array.from(b, x => x.toString(16).padStart(2, '0')).join('');
  }
  function enmascararCelular(cel) {
    return cel.length === 10 ? cel.slice(0, 3) + '****' + cel.slice(-3) : cel;
  }

  // --- Respuestas ------------------------------------------------
  const ok = (status, body) => ({ status, body });
  const err = (status, codigo, mensaje) => ({ status, body: { codigo, mensaje } });

  // --- Enrutador ------------------------------------------------
  function resolver(method, ruta, opciones) {
    const estado = cargarEstado();
    const url = new URL(ruta, 'http://mock.local');
    const p = url.pathname;
    const q = url.searchParams;

    let m;

    // GET /v1/publico/{slug}/servicios
    if (method === 'GET' && (m = p.match(/^\/v1\/publico\/([^/]+)\/servicios$/))) {
      return ok(200, SERVICIOS.map(s => ({ ...s })));
    }

    // GET /v1/publico/{slug}/disponibilidad
    if (method === 'GET' && (m = p.match(/^\/v1\/publico\/([^/]+)\/disponibilidad$/))) {
      const servicioId = q.get('servicioId');
      const fecha = q.get('fecha');
      const profesionalId = q.get('profesionalId') || null;
      if (!servicioId || !fecha) return err(400, 'DATOS_INVALIDOS', 'Falta el servicio o la fecha.');
      if (!fechaValida(fecha)) return err(400, 'DATOS_INVALIDOS', 'La fecha no tiene un formato válido.');
      const servicio = SERVICIOS.find(s => s.id === servicioId);
      if (!servicio) return err(404, 'NO_ENCONTRADO', 'No encontramos ese servicio.');
      const cupos = cuposDelDia(estado, servicio, fecha, profesionalId, false).map(limpiar);
      return ok(200, { fecha, zonaHoraria: ZONA, cupos });
    }

    // POST /v1/publico/{slug}/citas
    if (method === 'POST' && (m = p.match(/^\/v1\/publico\/([^/]+)\/citas$/))) {
      const key = (opciones.headers && (opciones.headers['Idempotency-Key'] || opciones.headers['idempotency-key'])) || null;
      if (!key) return err(400, 'DATOS_INVALIDOS', 'Falta la clave de idempotencia.');

      // Reenvío de la misma clave -> misma cita
      if (estado.idempotencia[key]) {
        const original = estado.citas[estado.idempotencia[key]];
        if (original) return ok(201, aCitaCreada(original));
      }

      const b = opciones.body || {};
      if (!b.clienteNombre || b.clienteNombre.trim().length < 2 || b.clienteNombre.length > 120) {
        return err(400, 'DATOS_INVALIDOS', 'Escribe tu nombre completo (mínimo 2 letras).');
      }
      if (!/^[0-9]{10}$/.test(b.clienteCelular || '')) {
        return err(400, 'DATOS_INVALIDOS', 'El número de celular debe tener 10 dígitos.');
      }
      const servicio = SERVICIOS.find(s => s.id === b.servicioId);
      const prof = PROFESIONALES.find(x => x.id === b.profesionalId);
      if (!servicio || !prof || !b.inicio) {
        return err(400, 'DATOS_INVALIDOS', 'Faltan datos de la cita o no son válidos.');
      }

      const ini = new Date(b.inicio);
      if (isNaN(ini.getTime())) return err(400, 'DATOS_INVALIDOS', 'La fecha de inicio no es válida.');
      const fin = new Date(ini.getTime() + servicio.duracionMin * 60000);
      const fecha = new Date(ini.getTime() + ZONA_OFFSET_MIN * 60000).toISOString().slice(0, 10);

      // ¿El cupo sigue libre? (choque real o "fantasma" = se acaba de ocupar)
      const todos = cuposDelDia(estado, servicio, fecha, null, true);
      const elegido = todos.find(c => c.inicio === ini.toISOString() && c.profesionalId === prof.id);
      const chocado = !elegido ||
        elegido._fantasma ||
        chocaConCita(estado, prof.id, ini.getTime(), fin.getTime());

      if (chocado) {
        const libres = todos
          .filter(c => !c._fantasma && c.inicio !== ini.toISOString())
          .sort((a, x) => Math.abs(new Date(a.inicio) - ini) - Math.abs(new Date(x.inicio) - ini))
          .slice(0, 3)
          .map(limpiar);
        return {
          status: 409,
          body: {
            codigo: 'CUPO_OCUPADO',
            mensaje: 'Ese cupo se acaba de ocupar. Estos son los más cercanos.',
            alternativas: libres,
          },
        };
      }

      const tk = token();
      const cita = {
        id: uuid(),
        token: tk,
        inicio: ini.toISOString(),
        fin: fin.toISOString(),
        horaLocal: horaLocalDe(ini.toISOString()),
        servicioId: servicio.id,
        servicioNombre: servicio.nombre,
        profesionalId: prof.id,
        profesionalNombre: prof.nombre,
        clienteNombre: b.clienteNombre.trim(),
        clienteCelular: b.clienteCelular,
        estado: 'CONFIRMADA',
      };
      estado.citas[tk] = cita;
      estado.idempotencia[key] = tk;
      guardarEstado(estado);
      return ok(201, aCitaCreada(cita));
    }

    // GET /v1/gestion/{token}
    if (method === 'GET' && (m = p.match(/^\/v1\/gestion\/([^/]+)$/))) {
      const cita = estado.citas[m[1]];
      if (!cita) return err(404, 'NO_ENCONTRADO', 'No encontramos esa cita. Es posible que el enlace haya vencido.');
      return ok(200, aCitaDetalle(cita));
    }

    // DELETE /v1/gestion/{token}
    if (method === 'DELETE' && (m = p.match(/^\/v1\/gestion\/([^/]+)$/))) {
      if (q.get('confirmar') !== 'true') return err(400, 'DATOS_INVALIDOS', 'Debes confirmar la cancelación.');
      const cita = estado.citas[m[1]];
      if (!cita) return err(404, 'NO_ENCONTRADO', 'No encontramos esa cita. Es posible que el enlace haya vencido.');
      cita.estado = 'CANCELADA';
      guardarEstado(estado);
      return ok(204, null);
    }

    return err(404, 'NO_ENCONTRADO', 'Ruta no contemplada en el mock: ' + method + ' ' + p);
  }

  function aCitaCreada(c) {
    return {
      id: c.id,
      inicio: c.inicio,
      fin: c.fin,
      horaLocal: c.horaLocal,
      servicioNombre: c.servicioNombre,
      profesionalNombre: c.profesionalNombre,
      estado: c.estado,
      enlaceGestion: (window.CONFIG?.apiBase || '') + '/v1/gestion/' + c.token,
    };
  }
  function aCitaDetalle(c) {
    return {
      id: c.id,
      inicio: c.inicio,
      fin: c.fin,
      horaLocal: c.horaLocal,
      servicioNombre: c.servicioNombre,
      profesionalNombre: c.profesionalNombre,
      clienteNombre: c.clienteNombre,
      clienteCelular: enmascararCelular(c.clienteCelular),
      estado: c.estado,
    };
  }

  // --- API pública del mock -------------------------------------
  window.MockBackend = {
    zona: ZONA,
    async handle(method, ruta, opciones = {}) {
      await new Promise(r => setTimeout(r, 120 + Math.random() * 220));  // latencia simulada
      return resolver(method, ruta, opciones);
    },
  };
})();
