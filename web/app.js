/*
 * AGENDA-D · PWA pública de reserva
 * HTML/CSS/JS nativo, sin framework ni build.
 *
 * Contra qué habla:  docs/openapi/agenda-service.yaml
 * Con qué datos:      web/mock.js  mientras CONFIG.useMock sea true
 */
(function () {
  'use strict';

  const CFG = window.CONFIG;
  const app = document.getElementById('app');

  // ==========================================================================
  //  Capa de red
  // ==========================================================================

  class SinRed extends Error {}

  async function pedir(method, path, { headers, body } = {}) {
    if (CFG.useMock) {
      return window.MockBackend.handle(method, path, { headers, body });
    }
    const cabeceras = { ...(headers || {}) };
    if (body != null) cabeceras['Content-Type'] = 'application/json';
    let res;
    try {
      res = await fetch(CFG.apiBase + path, {
        method,
        headers: cabeceras,
        body: body != null ? JSON.stringify(body) : undefined,
      });
    } catch (_) {
      throw new SinRed();
    }
    const texto = await res.text();
    let cuerpo = null;
    if (texto) { try { cuerpo = JSON.parse(texto); } catch (_) { cuerpo = texto; } }
    return { status: res.status, body: cuerpo };
  }

  const slug = () => encodeURIComponent(CFG.slug);

  const api = {
    servicios: () => pedir('GET', `/v1/publico/${slug()}/servicios`),

    disponibilidad(servicioId, fecha, profesionalId) {
      const q = new URLSearchParams({ servicioId, fecha });
      if (profesionalId) q.set('profesionalId', profesionalId);
      return pedir('GET', `/v1/publico/${slug()}/disponibilidad?${q}`);
    },

    reservar(idempotencyKey, cuerpo) {
      return pedir('POST', `/v1/publico/${slug()}/citas`, {
        headers: { 'Idempotency-Key': idempotencyKey },
        body: cuerpo,
      });
    },

    verCita: (token) => pedir('GET', `/v1/gestion/${encodeURIComponent(token)}`),
    cancelar: (token) => pedir('DELETE', `/v1/gestion/${encodeURIComponent(token)}?confirmar=true`),
  };

  // ==========================================================================
  //  Estado de la reserva en curso  (en memoria: un F5 lo reinicia)
  // ==========================================================================

  const flujo = {
    servicio: null,   // { id, nombre, duracionMin, precio }
    fecha: null,      // 'YYYY-MM-DD'
    cupo: null,       // { inicio, fin, horaLocal, profesionalId, profesionalNombre }
    idemKey: null,    // UUID estable para reintentar el MISMO cupo sin duplicar
  };
  let ultimaCita = null;   // respuesta 201 + token, para no volver a pedirla al confirmar

  // ==========================================================================
  //  Utilidades
  // ==========================================================================

  function pintar(idTpl) {
    const frag = document.getElementById(idTpl).content.cloneNode(true);
    app.replaceChildren(frag);
    window.scrollTo(0, 0);
    return app;
  }

  function nuevaClave() {
    return (crypto.randomUUID && crypto.randomUUID()) ||
      ('k-' + Date.now() + '-' + Math.random().toString(16).slice(2));
  }

  const dinero = (n) => '$' + Number(n).toLocaleString('es-CO');

  function hoyLocal() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  const FORMATO_FECHA = { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' };

  // El contrato da el instante en UTC y la hora local por separado, pero no la
  // fecha local. La reconstruimos desde ambos para no depender de la zona del
  // dispositivo (que puede no ser la del negocio).
  function fechaLocalDeCita(iso, horaLocal) {
    const d = new Date(iso);
    const [hh, mm] = (horaLocal || '00:00').split(':').map(Number);
    let deltaMin = (hh * 60 + mm) - (d.getUTCHours() * 60 + d.getUTCMinutes());
    if (deltaMin > 720) deltaMin -= 1440;
    if (deltaMin < -720) deltaMin += 1440;
    return new Date(d.getTime() + deltaMin * 60000).toLocaleDateString('es-CO', FORMATO_FECHA);
  }

  function tokenDeEnlace(enlace) {
    try { return enlace.split('/').filter(Boolean).pop(); } catch (_) { return ''; }
  }

  function definicion(dl, termino, valor) {
    const dt = document.createElement('dt');
    dt.textContent = termino;
    const dd = document.createElement('dd');
    dd.textContent = valor;
    dl.append(dt, dd);
  }

  const estaOffline = () => !navigator.onLine;

  const ESTADO_ETIQUETA = {
    CONFIRMADA: 'Confirmada',
    CANCELADA: 'Cancelada',
    ATENDIDA: 'Atendida',
    NO_ASISTIO: 'No asistió',
    PASADA: 'Pasada',   // solo en "Mis citas": confirmada cuya hora ya pasó
  };

  function chipEstado(estado) {
    const span = document.createElement('span');
    span.className = `chip-estado chip-estado--${String(estado).toLowerCase()}`;
    span.textContent = ESTADO_ETIQUETA[estado] || estado;
    return span;
  }

  function definicionEstado(dl, estado) {
    const dt = document.createElement('dt');
    dt.textContent = 'Estado';
    const dd = document.createElement('dd');
    dd.appendChild(chipEstado(estado));
    dl.append(dt, dd);
  }

  // ==========================================================================
  //  Mis citas  (guardadas en este dispositivo)
  // ==========================================================================
  // El enlace de gestión es la ÚNICA forma de cancelar sin cuenta. Si solo
  // vive en memoria, un F5 en la pantalla de confirmación lo pierde. Se
  // guarda en localStorage; si el navegador no lo permite (modo privado),
  // la app sigue funcionando y solo se pierde esta comodidad.

  const CLAVE_MIS_CITAS = 'agenda-d.mis-citas';

  function leerMisCitas() {
    try {
      const lista = JSON.parse(localStorage.getItem(CLAVE_MIS_CITAS) || '[]');
      return Array.isArray(lista) ? lista : [];
    } catch (_) { return []; }
  }

  function escribirMisCitas(lista) {
    try { localStorage.setItem(CLAVE_MIS_CITAS, JSON.stringify(lista)); } catch (_) { /* sin storage */ }
  }

  /** Inserta o actualiza la cita del token con los campos que traiga `cita`. */
  function guardarCita(token, cita) {
    if (!token) return;
    const lista = leerMisCitas();
    const previa = lista.find((c) => c.token === token) || { token };
    const nueva = {
      ...previa,
      servicioNombre: cita.servicioNombre ?? previa.servicioNombre,
      profesionalNombre: cita.profesionalNombre ?? previa.profesionalNombre,
      inicio: cita.inicio ?? previa.inicio,
      fin: cita.fin ?? previa.fin,
      horaLocal: cita.horaLocal ?? previa.horaLocal,
      estado: cita.estado ?? previa.estado,
    };
    escribirMisCitas([nueva, ...lista.filter((c) => c.token !== token)]);
  }

  function olvidarCita(token) {
    escribirMisCitas(leerMisCitas().filter((c) => c.token !== token));
  }

  function pintarEnlaceMisCitas() {
    const n = leerMisCitas().filter((c) => c.estado === 'CONFIRMADA' && new Date(c.fin || c.inicio) > new Date()).length;
    const enlace = document.getElementById('enlace-mis-citas');
    enlace.hidden = leerMisCitas().length === 0;
    enlace.querySelector('[data-n]').textContent = n ? String(n) : '';
  }

  // ==========================================================================
  //  Después de reservar: calendario y compartir
  // ==========================================================================

  const enlaceGestionPwa = (token) => `${location.origin}${location.pathname}#/gestion/${token}`;

  function nombreNegocio() {
    return CFG.negocioNombre || 'el negocio';
  }

  /** Archivo .ics (RFC 5545) con la cita en UTC: el calendario la muestra en la zona del teléfono. */
  function descargarIcs(cita, token) {
    const utc = (iso) => new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
    const escaparIcs = (t) => String(t).replace(/([,;\\])/g, '\\$1').replace(/\n/g, '\\n');
    const lineas = [
      'BEGIN:VCALENDAR',
      'VERSION:2.0',
      'PRODID:-//AGENDA-D//PWA//ES',
      'BEGIN:VEVENT',
      `UID:${cita.id || token}@agenda-d`,
      `DTSTAMP:${utc(new Date().toISOString())}`,
      `DTSTART:${utc(cita.inicio)}`,
      `DTEND:${utc(cita.fin || cita.inicio)}`,
      `SUMMARY:${escaparIcs(`${cita.servicioNombre} · ${nombreNegocio()}`)}`,
      `DESCRIPTION:${escaparIcs(`Con ${cita.profesionalNombre}. Ver o cancelar: ${enlaceGestionPwa(token)}`)}`,
      'BEGIN:VALARM',
      'TRIGGER:-PT1H',
      'ACTION:DISPLAY',
      'DESCRIPTION:Tu cita es en una hora',
      'END:VALARM',
      'END:VEVENT',
      'END:VCALENDAR',
    ];
    const blob = new Blob([lineas.join('\r\n') + '\r\n'], { type: 'text/calendar;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'mi-cita.ics';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }

  function textoParaCompartir(cita, token) {
    return `Mi cita en ${nombreNegocio()}: ${cita.servicioNombre} con ${cita.profesionalNombre}, ` +
      `${fechaLocalDeCita(cita.inicio, cita.horaLocal)} a las ${cita.horaLocal}. ` +
      `Ver o cancelar: ${enlaceGestionPwa(token)}`;
  }

  /** Botones de calendario y WhatsApp debajo del detalle de una cita confirmada. */
  function montarAccionesCita(contenedor, cita, token) {
    contenedor.hidden = false;
    contenedor.querySelector('[data-ics]').addEventListener('click', () => descargarIcs(cita, token));
    contenedor.querySelector('[data-whatsapp]').href =
      `https://wa.me/?text=${encodeURIComponent(textoParaCompartir(cita, token))}`;
  }

  // ==========================================================================
  //  Diálogo de confirmación propio (en vez de confirm())
  // ==========================================================================

  function confirmar(dlg) {
    return new Promise((resolver) => {
      dlg.addEventListener('close', () => resolver(dlg.returnValue === 'si'), { once: true });
      dlg.returnValue = 'no';
      dlg.showModal();
    });
  }

  // --- iconos SVG (inline, sin assets) ---
  const SVG_RELOJ = '<svg class="ico" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><circle cx="8" cy="8" r="6.3"/><path d="M8 4.4V8l2.6 1.6"/></svg>';
  const SVG_CHECK = '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3.5 8.5l3 3 6-7"/></svg>';
  const SVG_TIJERAS = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="6" cy="7" r="2.4"/><circle cx="6" cy="17" r="2.4"/><path d="M8 8.4 19 15M8 15.6 19 9"/><circle cx="12.4" cy="12" r=".6" fill="currentColor"/></svg>';
  const SVG_PEINE = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="18" height="4.5" rx="1.4"/><path d="M6 11.5v6M10 11.5v6M14 11.5v5M18 11.5v6"/></svg>';

  function iconoServicio(nombre) {
    return /barba|afeit|bigote/i.test(nombre || '') ? SVG_PEINE : SVG_TIJERAS;
  }

  // Marca el stepper (1 servicio · 2 fecha · 3 confirmar) en la vista activa.
  function montarPasos(v, activo) {
    const hueco = v.querySelector('[data-pasos]');
    if (!hueco) return;
    const frag = document.getElementById('tpl-pasos').content.cloneNode(true);
    frag.querySelectorAll('.paso').forEach((li) => {
      const n = Number(li.dataset.paso);
      if (n < activo) {
        li.classList.add('paso--hecho');
        li.querySelector('.paso__bolita').innerHTML = SVG_CHECK;
      } else if (n === activo) {
        li.classList.add('paso--activo');
      } else {
        li.classList.add('paso--pendiente');
      }
    });
    hueco.replaceWith(frag);
  }

  // ==========================================================================
  //  Vista · lista de servicios   (#/)
  // ==========================================================================

  async function vistaServicios() {
    const v = pintar('tpl-servicios');
    montarPasos(v, 1);
    const lista = v.querySelector('[data-lista]');
    const barra = v.querySelector('[data-barra]');
    lista.innerHTML = '<li class="cupos__vacio">Cargando servicios…</li>';

    try {
      const r = await api.servicios();
      if (r.status !== 200 || !Array.isArray(r.body)) {
        lista.innerHTML = `<li class="cupos__error">${(r.body && r.body.mensaje) || 'No pudimos cargar los servicios.'}</li>`;
        return;
      }
      if (r.body.length === 0) {
        lista.innerHTML = '<li class="cupos__vacio">Este negocio todavía no publicó servicios.</li>';
        return;
      }

      let elegido = null;
      const botones = r.body.map((s) => {
        const li = document.createElement('li');
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'servicio';
        btn.innerHTML = `
          <span class="miniatura">${iconoServicio(s.nombre)}</span>
          <span class="servicio__info">
            <span class="servicio__nombre"></span>
            <span class="linea-meta">${SVG_RELOJ}<span data-dur></span></span>
          </span>
          <span class="servicio__precio"></span>
          <span class="servicio__check">${SVG_CHECK}</span>`;
        btn.querySelector('.servicio__nombre').textContent = s.nombre;
        btn.querySelector('[data-dur]').textContent = `${s.duracionMin} min`;
        btn.querySelector('.servicio__precio').textContent = dinero(s.precio);
        btn.addEventListener('click', () => {
          elegido = s;
          botones.forEach((x) => x.classList.toggle('servicio--sel', x === btn));
          barra.hidden = false;
        });
        li.appendChild(btn);
        return btn;
      });
      lista.replaceChildren(...botones.map((b) => b.parentNode));

      // preseleccionar si volvemos del paso 2
      if (flujo.servicio) {
        const i = r.body.findIndex((s) => s.id === flujo.servicio.id);
        if (i >= 0) { botones[i].click(); }
      }

      v.querySelector('[data-continuar]').addEventListener('click', () => {
        if (!elegido) return;
        if (!flujo.servicio || flujo.servicio.id !== elegido.id) flujo.cupo = null;
        flujo.servicio = elegido;
        location.hash = `#/reservar?servicio=${encodeURIComponent(elegido.id)}`;
      });
    } catch (e) {
      lista.innerHTML = `<li class="cupos__error">${
        e instanceof SinRed
          ? 'Sin conexión. Conéctate a internet para ver los servicios.'
          : 'No pudimos cargar los servicios.'}</li>`;
    }
  }

  // ==========================================================================
  //  Vista · disponibilidad   (#/reservar?servicio=…)
  // ==========================================================================

  async function vistaDisponibilidad(servicioId) {
    // Resolver el servicio si venimos de un enlace directo o de un F5.
    if (!flujo.servicio || flujo.servicio.id !== servicioId) {
      try {
        const r = await api.servicios();
        flujo.servicio = (r.body || []).find((s) => s.id === servicioId) || null;
      } catch (_) { flujo.servicio = null; }
    }
    if (!flujo.servicio) { location.hash = '#/'; return; }

    const s = flujo.servicio;
    const v = pintar('tpl-disponibilidad');
    montarPasos(v, 2);
    v.querySelector('[data-mini]').innerHTML = iconoServicio(s.nombre);
    v.querySelector('[data-servicio-nombre]').textContent = s.nombre;
    v.querySelector('[data-servicio-detalle]').innerHTML = `${SVG_RELOJ}${s.duracionMin} min · ${dinero(s.precio)}`;
    v.querySelector('[data-volver]').addEventListener('click', () => { location.hash = '#/'; });

    const input = v.querySelector('[data-fecha]');
    const cont = v.querySelector('[data-cupos]');
    const barra = v.querySelector('[data-barra]');
    input.min = hoyLocal();
    input.value = flujo.fecha && flujo.fecha >= hoyLocal() ? flujo.fecha : hoyLocal();
    input.addEventListener('change', () => cargar());

    let seleccion = null;
    v.querySelector('[data-continuar]').addEventListener('click', () => {
      if (!seleccion) return;
      flujo.cupo = seleccion;
      flujo.idemKey = nuevaClave();   // cupo nuevo => intento nuevo de reserva
      location.hash = '#/datos';
    });

    let peticion = 0;   // descarta respuestas de una fecha que el usuario ya cambió
    cargar();

    async function cargar() {
      const mia = ++peticion;
      seleccion = null;
      barra.hidden = true;
      flujo.fecha = input.value;
      if (!flujo.fecha) return;
      cont.innerHTML = '<p class="cupos__vacio">Buscando horarios…</p>';
      try {
        const r = await api.disponibilidad(s.id, flujo.fecha);
        if (mia !== peticion) return;   // llegó tarde: hay una búsqueda más nueva
        if (r.status !== 200) {
          cont.innerHTML = `<p class="cupos__error">${(r.body && r.body.mensaje) || 'No pudimos cargar los horarios.'}</p>`;
          return;
        }
        const cupos = r.body.cupos || [];
        if (cupos.length === 0) {
          cont.innerHTML = '<p class="cupos__vacio">No hay cupos libres ese día. Prueba con otra fecha.</p>';
          return;
        }
        const botones = cupos.map((c) => {
          const b = document.createElement('button');
          b.type = 'button';
          b.className = 'cupo';
          b.innerHTML = '<span class="cupo__hora"></span><span class="cupo__prof"></span>';
          b.querySelector('.cupo__hora').textContent = c.horaLocal;
          b.querySelector('.cupo__prof').textContent = c.profesionalNombre;
          b.addEventListener('click', () => {
            seleccion = c;
            botones.forEach((x) => x.classList.toggle('cupo--sel', x === b));
            barra.hidden = false;
          });
          return b;
        });
        cont.replaceChildren(...botones);
      } catch (e) {
        if (mia !== peticion) return;
        cont.innerHTML = e instanceof SinRed
          ? '<p class="cupos__error">Sin conexión. No pudimos cargar los horarios.</p>'
          : '<p class="cupos__error">No pudimos cargar los horarios.</p>';
      }
    }
  }

  // ==========================================================================
  //  Vista · datos del cliente   (#/datos)
  // ==========================================================================

  function vistaDatos() {
    if (!flujo.servicio || !flujo.cupo) {
      location.hash = flujo.servicio ? `#/reservar?servicio=${encodeURIComponent(flujo.servicio.id)}` : '#/';
      return;
    }

    const v = pintar('tpl-datos');
    montarPasos(v, 3);
    const form = v.querySelector('[data-form]');
    const avisoConflicto = v.querySelector('[data-conflicto]');
    const boton = v.querySelector('[data-enviar]');

    const pintarResumen = () => {
      const cuando = fechaLocalDeCita(flujo.cupo.inicio, flujo.cupo.horaLocal);
      v.querySelector('[data-resumen]').textContent =
        `${flujo.servicio.nombre} · ${cuando} · ${flujo.cupo.horaLocal} con ${flujo.cupo.profesionalNombre}`;
    };
    pintarResumen();

    v.querySelector('[data-volver]').addEventListener('click', () => {
      location.hash = `#/reservar?servicio=${encodeURIComponent(flujo.servicio.id)}`;
    });

    const erroresDe = (campo) => form.querySelector(`[data-error="${campo}"]`);
    const limpiarErrores = () => {
      form.querySelectorAll('.campo__error').forEach((e) => (e.textContent = ''));
      avisoConflicto.hidden = true;
      avisoConflicto.innerHTML = '';
    };

    form.addEventListener('submit', (ev) => {
      ev.preventDefault();
      enviar();
    });

    async function enviar() {
      limpiarErrores();
      const datos = new FormData(form);
      const clienteNombre = (datos.get('clienteNombre') || '').toString().trim();
      // "300 123 4567" o "300-123-4567" también valen: se quitan los separadores.
      const clienteCelular = (datos.get('clienteCelular') || '').toString().replace(/[\s.-]/g, '');

      let hayError = false;
      if (clienteNombre.length < 2 || clienteNombre.length > 120) {
        erroresDe('clienteNombre').textContent = 'Escribe tu nombre completo.';
        hayError = true;
      }
      if (!/^[0-9]{10}$/.test(clienteCelular)) {
        erroresDe('clienteCelular').textContent = 'El celular debe tener 10 dígitos. Ejemplo: 3001234567.';
        hayError = true;
      } else if (!clienteCelular.startsWith('3')) {
        erroresDe('clienteCelular').textContent = 'Los celulares en Colombia empiezan por 3. Revisa el número.';
        hayError = true;
      }
      if (hayError) return;

      if (estaOffline()) {
        mostrarAviso('Necesitas conexión a internet para confirmar la reserva. Tu selección se queda guardada.');
        return;
      }

      boton.disabled = true;
      boton.textContent = 'Confirmando…';
      try {
        const r = await api.reservar(flujo.idemKey, {
          servicioId: flujo.servicio.id,
          profesionalId: flujo.cupo.profesionalId,
          inicio: flujo.cupo.inicio,
          clienteNombre,
          clienteCelular,
        });

        if (r.status === 201) {
          const token = tokenDeEnlace(r.body.enlaceGestion);
          ultimaCita = { ...r.body, token };
          guardarCita(token, r.body);
          // El token va en la URL: un F5 en la confirmación ya no pierde el enlace.
          location.hash = `#/confirmada/${token}`;
          return;
        }
        if (r.status === 409) {
          mostrarConflicto(r.body);
          return;
        }
        if (r.status === 400 && r.body && r.body.mensaje) {
          // agenda-service dice qué campo falló; el mock (y versiones viejas) no.
          const casilla = r.body.campo ? erroresDe(r.body.campo) : null;
          if (casilla) casilla.textContent = r.body.mensaje;
          else mostrarAviso(r.body.mensaje);
          return;
        }
        mostrarAviso((r.body && r.body.mensaje) || 'No pudimos completar la reserva. Intenta de nuevo.');
      } catch (e) {
        mostrarAviso(e instanceof SinRed
          ? 'Se perdió la conexión antes de confirmar. Revisa tu internet e intenta otra vez.'
          : 'No pudimos completar la reserva. Intenta de nuevo.');
      } finally {
        boton.disabled = false;
        boton.textContent = 'Confirmar reserva';
      }
    }

    function mostrarAviso(texto) {
      avisoConflicto.hidden = false;
      avisoConflicto.textContent = texto;
    }

    function mostrarConflicto(cuerpo) {
      avisoConflicto.hidden = false;
      avisoConflicto.innerHTML = '';
      const p = document.createElement('span');
      p.textContent = (cuerpo && cuerpo.mensaje) || 'Ese cupo se acaba de ocupar.';
      avisoConflicto.appendChild(p);

      const alts = (cuerpo && cuerpo.alternativas) || [];
      if (alts.length === 0) return;
      const ul = document.createElement('ul');
      ul.className = 'alternativas';
      alts.forEach((c) => {
        const li = document.createElement('li');
        const b = document.createElement('button');
        b.type = 'button';
        b.className = 'boton boton--secundario';
        b.style.marginTop = '0';
        b.textContent = `${c.horaLocal} con ${c.profesionalNombre}`;
        b.addEventListener('click', () => {
          flujo.cupo = c;
          flujo.idemKey = nuevaClave();   // otro cupo => otra clave de idempotencia
          pintarResumen();
          enviar();
        });
        li.appendChild(b);
        ul.appendChild(li);
      });
      avisoConflicto.appendChild(ul);
    }
  }

  // ==========================================================================
  //  Vista · confirmación   (#/confirmada/{token})
  // ==========================================================================

  async function vistaConfirmada(token) {
    let c = ultimaCita && ultimaCita.token === token ? ultimaCita : null;
    if (!c) {
      // F5 o enlace abierto después: se reconstruye desde el token.
      try {
        const r = await api.verCita(token);
        if (r.status !== 200) return vistaGestion(token);
        c = r.body;
      } catch (_) {
        return vistaGestion(token);
      }
    }

    const v = pintar('tpl-confirmada');
    const dl = v.querySelector('[data-detalle]');
    definicion(dl, 'Servicio', c.servicioNombre);
    definicion(dl, 'Profesional', c.profesionalNombre);
    definicion(dl, 'Fecha', fechaLocalDeCita(c.inicio, c.horaLocal));
    definicion(dl, 'Hora', c.horaLocal);
    definicionEstado(dl, c.estado);

    montarAccionesCita(v.querySelector('[data-acciones-cita]'), c, token);

    const enlacePwa = enlaceGestionPwa(token);
    const inputEnlace = v.querySelector('[data-enlace]');
    inputEnlace.value = enlacePwa;

    const btnCopiar = v.querySelector('[data-copiar]');
    btnCopiar.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(enlacePwa);
        btnCopiar.textContent = 'Copiado ✓';
      } catch (_) {
        inputEnlace.select();
        btnCopiar.textContent = 'Selecciónalo y copia';
      }
      setTimeout(() => (btnCopiar.textContent = 'Copiar'), 2500);
    });

    v.querySelector('[data-abrir-gestion]').setAttribute('href', `#/gestion/${token}`);
    pintarEnlaceMisCitas();
  }

  // ==========================================================================
  //  Vista · gestión de la cita   (#/gestion/{token})
  // ==========================================================================

  async function vistaGestion(token) {
    const v = pintar('tpl-gestion');
    const dl = v.querySelector('[data-detalle]');
    const acciones = v.querySelector('[data-acciones]');
    const avisoCancelada = v.querySelector('[data-cancelada]');
    const avisoError = v.querySelector('[data-error-accion]');
    const btnCancelar = v.querySelector('[data-cancelar]');
    const dialogo = v.querySelector('[data-dialogo-cancelar]');

    dl.innerHTML = '<dt>Cargando…</dt><dd></dd>';

    let cita;
    try {
      const r = await api.verCita(token);
      if (r.status === 404) return vistaError('Enlace no válido', (r.body && r.body.mensaje) ||
        'No encontramos esa cita. Es posible que el enlace haya vencido.');
      if (r.status !== 200) return vistaError('Algo salió mal', (r.body && r.body.mensaje) ||
        'No pudimos consultar tu cita.');
      cita = r.body;
    } catch (e) {
      return vistaError('Sin conexión', e instanceof SinRed
        ? 'Necesitas conexión a internet para ver o cancelar tu cita.'
        : 'No pudimos consultar tu cita.');
    }

    guardarCita(token, cita);   // también la estrena en "Mis citas" si llegó por un enlace
    pintarEnlaceMisCitas();

    dl.innerHTML = '';
    definicion(dl, 'Servicio', cita.servicioNombre);
    definicion(dl, 'Profesional', cita.profesionalNombre);
    definicion(dl, 'Fecha', fechaLocalDeCita(cita.inicio, cita.horaLocal));
    definicion(dl, 'Hora', cita.horaLocal);
    definicion(dl, 'A nombre de', cita.clienteNombre);
    definicion(dl, 'Celular', cita.clienteCelular);
    definicionEstado(dl, cita.estado);

    if (cita.estado !== 'CONFIRMADA') {
      acciones.hidden = true;
      avisoCancelada.hidden = false;
      avisoCancelada.textContent = cita.estado === 'CANCELADA'
        ? 'Esta cita está cancelada. El cupo quedó libre para otra persona.'
        : `Esta cita ya está marcada como ${(ESTADO_ETIQUETA[cita.estado] || cita.estado).toLowerCase()}.`;
      return;
    }

    montarAccionesCita(v.querySelector('[data-acciones-cita]'), cita, token);
    dialogo.querySelector('[data-detalle-dialogo]').textContent =
      `${cita.servicioNombre} · ${fechaLocalDeCita(cita.inicio, cita.horaLocal)} · ${cita.horaLocal} con ${cita.profesionalNombre}`;

    const mostrarError = (texto) => {
      avisoError.hidden = false;
      avisoError.textContent = texto;
    };

    btnCancelar.addEventListener('click', async () => {
      avisoError.hidden = true;
      if (!(await confirmar(dialogo))) return;
      if (estaOffline()) {
        mostrarError('Necesitas conexión a internet para cancelar la cita.');
        return;
      }
      btnCancelar.disabled = true;
      btnCancelar.textContent = 'Cancelando…';
      try {
        const r = await api.cancelar(token);
        if (r.status === 204) return vistaGestion(token);   // recarga: mostrará el estado cancelado
        mostrarError((r.body && r.body.mensaje) || 'No pudimos cancelar la cita. Intenta de nuevo.');
      } catch (e) {
        mostrarError(e instanceof SinRed
          ? 'Se perdió la conexión. Intenta cancelar de nuevo.'
          : 'No pudimos cancelar la cita. Intenta de nuevo.');
      } finally {
        btnCancelar.disabled = false;
        btnCancelar.textContent = 'Cancelar esta cita';
      }
    });
  }

  // ==========================================================================
  //  Vista · mis citas   (#/mis-citas)
  // ==========================================================================

  function vistaMisCitas() {
    const v = pintar('tpl-mis-citas');
    const lista = v.querySelector('[data-lista]');
    const ahora = new Date();
    const citas = leerMisCitas();

    if (citas.length === 0) {
      lista.innerHTML = '<li class="cupos__vacio">Todavía no has reservado desde este dispositivo.</li>';
      return;
    }

    // Próximas primero (la más cercana arriba); después las demás, la más reciente arriba.
    const fin = (c) => new Date(c.fin || c.inicio);
    const esProxima = (c) => c.estado === 'CONFIRMADA' && fin(c) > ahora;
    const proximas = citas.filter(esProxima).sort((a, b) => new Date(a.inicio) - new Date(b.inicio));
    const resto = citas.filter((c) => !esProxima(c)).sort((a, b) => new Date(b.inicio) - new Date(a.inicio));

    lista.replaceChildren(...[...proximas, ...resto].map((c) => {
      const li = document.createElement('li');
      const a = document.createElement('a');
      a.className = 'mi-cita' + (esProxima(c) ? '' : ' mi-cita--pasada');
      a.href = `#/gestion/${c.token}`;
      a.innerHTML = `
        <span class="miniatura">${iconoServicio(c.servicioNombre)}</span>
        <span class="mi-cita__info">
          <span class="mi-cita__titulo"></span>
          <span class="linea-meta" data-cuando></span>
        </span>`;
      a.querySelector('.mi-cita__titulo').textContent = `${c.servicioNombre} con ${c.profesionalNombre}`;
      a.querySelector('[data-cuando]').textContent = `${fechaLocalDeCita(c.inicio, c.horaLocal)} · ${c.horaLocal}`;
      a.appendChild(chipEstado(c.estado === 'CONFIRMADA' && !esProxima(c) ? 'PASADA' : c.estado));
      li.appendChild(a);

      if (!esProxima(c)) {
        const quitar = document.createElement('button');
        quitar.type = 'button';
        quitar.className = 'mi-cita__quitar';
        quitar.textContent = 'Quitar de la lista';
        quitar.addEventListener('click', () => { olvidarCita(c.token); pintarEnlaceMisCitas(); vistaMisCitas(); });
        li.appendChild(quitar);
      }
      return li;
    }));
  }

  // ==========================================================================
  //  Vista · error genérico
  // ==========================================================================

  function vistaError(titulo, mensaje) {
    const v = pintar('tpl-error');
    v.querySelector('[data-titulo]').textContent = titulo;
    v.querySelector('[data-mensaje]').textContent = mensaje;
  }

  // ==========================================================================
  //  Enrutador
  // ==========================================================================

  function ruta() {
    const h = location.hash.replace(/^#/, '') || '/';
    const [path, query] = h.split('?');
    return { path, params: new URLSearchParams(query || '') };
  }

  function enrutar() {
    const { path, params } = ruta();

    if (path === '/' || path === '') return vistaServicios();
    if (path === '/reservar') return vistaDisponibilidad(params.get('servicio'));
    if (path === '/datos') return vistaDatos();
    if (path === '/mis-citas') return vistaMisCitas();
    if (path.startsWith('/confirmada/')) {
      return vistaConfirmada(decodeURIComponent(path.slice('/confirmada/'.length)));
    }
    if (path === '/confirmada') return location.replace('#/mis-citas');   // enlace viejo, sin token
    if (path.startsWith('/gestion/')) {
      const token = decodeURIComponent(path.slice('/gestion/'.length));
      return token ? vistaGestion(token) : vistaError('Enlace incompleto', 'Ese enlace no trae el código de tu cita.');
    }
    return vistaError('Página no encontrada', 'El enlace que seguiste no existe en esta app.');
  }

  // ==========================================================================
  //  Instalación de la PWA
  // ==========================================================================
  // Chrome no muestra su propio prompt de instalación a menos que se lo pidamos:
  // dispara beforeinstallprompt y espera a que llamemos a prompt(). Guardamos
  // ese evento para lanzarlo desde nuestro propio botón (mejor que depender de
  // que alguien encuentre "Instalar" en el menú del navegador).

  const CLAVE_INSTALAR_DESCARTADO = 'agenda-d.instalar-descartado-en';
  const DIAS_ANTES_DE_REOFRECER = 14;

  let promptDeInstalacion = null;

  const corriendoInstalada = () =>
    window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;

  function descartadoRecientemente() {
    try {
      const en = Number(localStorage.getItem(CLAVE_INSTALAR_DESCARTADO));
      return Boolean(en) && (Date.now() - en) < DIAS_ANTES_DE_REOFRECER * 86400000;
    } catch (_) { return false; }
  }

  function marcarInstalarDescartado() {
    try { localStorage.setItem(CLAVE_INSTALAR_DESCARTADO, String(Date.now())); } catch (_) {}
  }

  function ocultarBannerInstalar() {
    document.getElementById('banner-instalar').hidden = true;
  }

  window.addEventListener('beforeinstallprompt', (ev) => {
    ev.preventDefault();   // no dejamos que el navegador muestre su propio mini-banner
    promptDeInstalacion = ev;
    if (!corriendoInstalada() && !descartadoRecientemente()) {
      document.getElementById('banner-instalar').hidden = false;
    }
  });

  window.addEventListener('appinstalled', () => {
    promptDeInstalacion = null;
    ocultarBannerInstalar();
    marcarInstalarDescartado();   // ya instalada: no tiene sentido volver a ofrecerla
  });

  // ==========================================================================
  //  Arranque
  // ==========================================================================

  function pintarOffline() {
    document.getElementById('banner-offline').hidden = navigator.onLine;
  }

  window.addEventListener('online', pintarOffline);
  window.addEventListener('offline', pintarOffline);
  window.addEventListener('hashchange', enrutar);

  function arrancar() {
    if (CFG.negocioNombre) {
      document.getElementById('negocio-nombre').textContent = CFG.negocioNombre;
      document.getElementById('pie-nombre').textContent = CFG.negocioNombre;
    }
    pintarOffline();
    pintarEnlaceMisCitas();
    if (!location.hash) history.replaceState(null, '', '#/');
    enrutar();

    document.querySelector('[data-instalar]').addEventListener('click', async () => {
      ocultarBannerInstalar();
      if (!promptDeInstalacion) return;
      promptDeInstalacion.prompt();
      try { await promptDeInstalacion.userChoice; } catch (_) { /* el usuario cerró el diálogo nativo */ }
      promptDeInstalacion = null;
    });
    document.querySelector('[data-cerrar-instalar]').addEventListener('click', () => {
      ocultarBannerInstalar();
      marcarInstalarDescartado();
    });

    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('sw.js').catch(() => { /* sin SW la app igual funciona */ });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', arrancar);
  } else {
    arrancar();
  }
})();
