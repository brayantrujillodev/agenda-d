/*
 * AGENDA-D · Panel de recepción (docs/TAREAS.md #20)
 * HTML/CSS/JS nativo, sin framework ni build — mismo criterio que app.js.
 *
 * Una sola consulta GraphQL trae agenda del día + configuración + métricas
 * del mes (gateway-graphql, panelRecepcion). Es la razón de existir del
 * gateway: sin esto serían 3-4 peticiones REST desde un celular con mala
 * señal. Además consulta notificaciones-service para la alerta de
 * mensajes fallidos (docs/TAREAS.md #16).
 *
 * Desde aquí la recepción registra el resultado de cada cita (atendió / no
 * asistió) y puede cancelarla: mutaciones registrarAsistencia y cancelarCita.
 */
(function () {
  'use strict';

  const CFG = window.CONFIG;
  const REFRESCO_MS = 60 * 1000;

  const QUERY = `
    query PanelRecepcion($fecha: Date) {
      panelRecepcion(fecha: $fecha) {
        fecha
        negocio { nombre zonaHoraria profesionales { id nombre } }
        citasDelDia {
          id
          horaLocal
          clienteNombre
          clienteCelular
          servicio { nombre }
          profesional { id nombre }
          estado
        }
        metricasDelMes {
          desde
          hasta
          totalCitas
          atendidas
          noAsistio
          canceladas
          ocupacion
          tasaInasistencia
        }
      }
    }
  `;

  const MUTACION_ASISTENCIA = `
    mutation Asistencia($id: ID!, $estado: EstadoCita!) {
      registrarAsistencia(citaId: $id, estado: $estado) { id estado }
    }
  `;

  const MUTACION_CANCELAR = `
    mutation Cancelar($id: ID!) {
      cancelarCita(citaId: $id) { id estado }
    }
  `;

  // ==========================================================================
  //  Red
  // ==========================================================================

  async function graphql(query, variables) {
    let res;
    try {
      res = await fetch(CFG.gatewayUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query, variables }),
      });
    } catch (_) {
      throw new Error('Sin conexión con el servidor. Revisa tu internet e intenta de nuevo.');
    }
    const cuerpo = await res.json().catch(() => ({}));
    if (cuerpo.errors && cuerpo.errors.length) {
      throw new Error(cuerpo.errors.map((e) => e.message).join(' '));
    }
    if (!cuerpo.data) throw new Error('El servidor respondió algo inesperado. Intenta de nuevo.');
    return cuerpo.data;
  }

  /** No es crítico: si notificaciones-service no responde, el panel igual funciona. */
  async function contarMensajesFallidos() {
    try {
      const res = await fetch(CFG.notificacionesUrl + '/v1/mensajes-fallidos');
      if (!res.ok) return null;
      const lista = await res.json();
      return Array.isArray(lista) ? lista.length : null;
    } catch (_) {
      return null;
    }
  }

  // ==========================================================================
  //  Estado
  // ==========================================================================

  const estado = {
    fecha: null,           // 'YYYY-MM-DD' que se está mirando
    datos: null,           // última respuesta de panelRecepcion
    profesional: 'todos',  // filtro activo
    ocupado: false,        // hay una mutación en curso: no refrescar encima
  };

  const ESTADO_ETIQUETA = {
    CONFIRMADA: 'Confirmada',
    CANCELADA: 'Cancelada',
    ATENDIDA: 'Atendió',
    NO_ASISTIO: 'No asistió',
  };

  // ==========================================================================
  //  Utilidades
  // ==========================================================================

  const $ = (id) => document.getElementById(id);

  function escapar(texto) {
    const d = document.createElement('div');
    d.textContent = texto == null ? '' : String(texto);
    return d.innerHTML;
  }

  function hoyLocal() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  function sumarDias(fecha, n) {
    const d = new Date(fecha + 'T12:00:00');
    d.setDate(d.getDate() + n);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  let temporizadorAviso = null;
  function avisar(texto, tipo) {
    const el = $('aviso');
    el.textContent = texto;
    el.className = 'panel-aviso' + (tipo === 'error' ? ' panel-aviso--error' : '');
    el.hidden = false;
    clearTimeout(temporizadorAviso);
    temporizadorAviso = setTimeout(() => { el.hidden = true; }, tipo === 'error' ? 6000 : 3000);
  }

  // ==========================================================================
  //  Pintado
  // ==========================================================================

  function pintarAlerta(cantidad) {
    const el = $('alerta-fallidos');
    if (!cantidad) { el.hidden = true; return; }
    const plural = cantidad !== 1;
    el.innerHTML = `
      <strong>${cantidad} aviso${plural ? 's' : ''} a clientes no se ${plural ? 'pudieron' : 'pudo'} enviar.</strong>
      Esos clientes no recibieron la confirmación, el recordatorio o la cancelación de su cita:
      llámalos para confirmar y avisa a soporte técnico para revisar el envío.`;
    el.hidden = false;
  }

  function citasVisibles() {
    const citas = estado.datos ? estado.datos.citasDelDia : [];
    return estado.profesional === 'todos'
      ? citas
      : citas.filter((c) => c.profesional.id === estado.profesional);
  }

  function pintarFiltro() {
    const cont = $('filtro-profesional');
    const { profesionales } = estado.datos.negocio;
    const citas = estado.datos.citasDelDia;
    if (profesionales.length < 2) { cont.innerHTML = ''; return; }
    if (estado.profesional !== 'todos' && !profesionales.some((p) => p.id === estado.profesional)) {
      estado.profesional = 'todos';
    }
    const chip = (id, nombre, n) => `
      <button type="button" class="panel-filtro__chip${estado.profesional === id ? ' panel-filtro__chip--sel' : ''}"
              data-profesional="${escapar(id)}" aria-pressed="${estado.profesional === id}">
        ${escapar(nombre)} <span class="panel-filtro__n">${n}</span>
      </button>`;
    cont.innerHTML = chip('todos', 'Todos', citas.length) + profesionales
      .map((p) => chip(p.id, p.nombre, citas.filter((c) => c.profesional.id === p.id).length))
      .join('');
  }

  function pintarCitas() {
    const cont = $('citas');
    const citas = citasVisibles();
    if (!citas.length) {
      cont.innerHTML = '<p class="cupos__vacio">No hay citas para este día.</p>';
      return;
    }
    const filas = citas.map((c) => {
      const acciones = c.estado === 'CONFIRMADA'
        ? `<div class="panel-acciones">
             <button type="button" class="panel-btn panel-btn--ok" data-accion="ATENDIDA" data-id="${escapar(c.id)}">Atendió</button>
             <button type="button" class="panel-btn" data-accion="NO_ASISTIO" data-id="${escapar(c.id)}">No asistió</button>
             <button type="button" class="panel-btn panel-btn--peligro" data-accion="CANCELAR" data-id="${escapar(c.id)}">Cancelar</button>
           </div>`
        : '';
      return `
        <tr>
          <td data-titulo="Hora" class="panel-tabla__hora">${escapar(c.horaLocal)}</td>
          <td data-titulo="Cliente">
            <div>
              <span class="panel-cliente">${escapar(c.clienteNombre)}</span>
              <a class="panel-tel" href="tel:${escapar(c.clienteCelular)}">${escapar(c.clienteCelular)}</a>
            </div>
          </td>
          <td data-titulo="Servicio">${escapar(c.servicio.nombre)}</td>
          <td data-titulo="Profesional">${escapar(c.profesional.nombre)}</td>
          <td data-titulo="Estado"><span class="panel-chip panel-chip--${c.estado.toLowerCase()}">${escapar(ESTADO_ETIQUETA[c.estado] || c.estado)}</span></td>
          <td data-titulo="" class="panel-tabla__acciones">${acciones}</td>
        </tr>`;
    }).join('');
    cont.innerHTML = `
      <table class="panel-tabla">
        <thead><tr><th>Hora</th><th>Cliente</th><th>Servicio</th><th>Profesional</th><th>Estado</th><th><span class="sr-solo">Acciones</span></th></tr></thead>
        <tbody>${filas}</tbody>
      </table>`;
  }

  function pintarMetricas(m) {
    const tile = (valor, etiqueta) => `
      <div class="panel-metrica">
        <span class="panel-metrica__valor">${escapar(valor)}</span>
        <span class="panel-metrica__etiqueta">${escapar(etiqueta)}</span>
      </div>`;
    $('metricas').innerHTML = `
      <div class="panel-metricas">
        ${tile(m.totalCitas, 'Citas del mes')}
        ${tile(m.atendidas, 'Atendidas')}
        ${tile(m.canceladas, 'Canceladas')}
        ${tile(m.noAsistio, 'No asistió')}
        ${tile(m.ocupacion.toFixed(1) + '%', 'Ocupación')}
        ${tile(m.tasaInasistencia.toFixed(1) + '%', 'Tasa de inasistencia')}
      </div>
      <p class="vista__ayuda panel-periodo">Periodo: ${escapar(m.desde)} — ${escapar(m.hasta)}</p>`;
  }

  function pintarTodo() {
    const d = estado.datos;
    $('negocio-nombre').textContent = d.negocio.nombre;
    $('pie-nombre').textContent = d.negocio.nombre;
    pintarFiltro();
    pintarCitas();
    pintarMetricas(d.metricasDelMes);
    const hora = new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
    $('actualizado').textContent = `Actualizado ${hora} · se refresca solo cada minuto`;
  }

  function pintarError(err) {
    $('citas').innerHTML = `
      <p class="cupos__error">${escapar(err.message || 'No pudimos cargar el panel. Revisa que el servidor esté corriendo.')}</p>
      <button type="button" class="boton boton--secundario" id="reintentar">Reintentar</button>`;
    $('reintentar').addEventListener('click', () => cargar());
  }

  // ==========================================================================
  //  Carga
  // ==========================================================================

  let peticion = 0;   // descarta respuestas de un día que ya se cambió

  /** {silencioso}: refresco de fondo, sin "Cargando…" ni borrar la tabla. */
  async function cargar({ silencioso = false } = {}) {
    const mia = ++peticion;
    if (!silencioso) $('citas').innerHTML = '<p class="cargando">Cargando…</p>';
    try {
      const [datos, fallidos] = await Promise.all([
        graphql(QUERY, { fecha: estado.fecha }),
        contarMensajesFallidos(),
      ]);
      if (mia !== peticion) return;
      estado.datos = datos.panelRecepcion;
      if (!estado.fecha) {
        estado.fecha = estado.datos.fecha;
        $('selector-fecha').value = estado.fecha;
      }
      pintarAlerta(fallidos);
      pintarTodo();
    } catch (err) {
      if (mia !== peticion) return;
      if (silencioso && estado.datos) {
        $('actualizado').textContent = 'No se pudo actualizar; se reintentará en un minuto.';
        return;
      }
      pintarError(err);
    }
  }

  function irA(fecha) {
    if (!fecha) return;
    estado.fecha = fecha;
    $('selector-fecha').value = fecha;
    cargar();
  }

  // ==========================================================================
  //  Acciones sobre una cita
  // ==========================================================================

  function confirmarCancelacion(cita) {
    const dlg = $('dialogo-cancelar');
    dlg.querySelector('[data-detalle]').textContent =
      `${cita.horaLocal} · ${cita.clienteNombre} · ${cita.servicio.nombre} con ${cita.profesional.nombre}`;
    return new Promise((resolver) => {
      dlg.addEventListener('close', () => resolver(dlg.returnValue === 'si'), { once: true });
      dlg.returnValue = 'no';
      dlg.showModal();
    });
  }

  async function ejecutar(accion, id, boton) {
    const cita = estado.datos.citasDelDia.find((c) => c.id === id);
    if (!cita) return;
    if (accion === 'CANCELAR' && !(await confirmarCancelacion(cita))) return;

    estado.ocupado = true;
    const fila = boton.closest('tr');
    fila.querySelectorAll('button').forEach((b) => (b.disabled = true));
    try {
      const r = accion === 'CANCELAR'
        ? (await graphql(MUTACION_CANCELAR, { id })).cancelarCita
        : (await graphql(MUTACION_ASISTENCIA, { id, estado: accion })).registrarAsistencia;
      cita.estado = r.estado;
      pintarCitas();
      avisar({
        ATENDIDA: `Listo: ${cita.clienteNombre} quedó como atendido.`,
        NO_ASISTIO: `Listo: ${cita.clienteNombre} quedó como no asistió.`,
        CANCELAR: `Cita de ${cita.clienteNombre} cancelada. Se le enviará el aviso.`,
      }[accion]);
      cargar({ silencioso: true });   // las métricas del mes cambian
    } catch (err) {
      fila.querySelectorAll('button').forEach((b) => (b.disabled = false));
      avisar(err.message, 'error');
    } finally {
      estado.ocupado = false;
    }
  }

  // ==========================================================================
  //  Arranque
  // ==========================================================================

  $('selector-fecha').addEventListener('change', (ev) => irA(ev.target.value));
  document.querySelectorAll('[data-dia]').forEach((b) => b.addEventListener('click', () => {
    irA(sumarDias(estado.fecha || hoyLocal(), Number(b.dataset.dia)));
  }));
  $('ir-hoy').addEventListener('click', () => irA(hoyLocal()));

  $('filtro-profesional').addEventListener('click', (ev) => {
    const b = ev.target.closest('[data-profesional]');
    if (!b) return;
    estado.profesional = b.dataset.profesional;
    pintarFiltro();
    pintarCitas();
  });

  $('citas').addEventListener('click', (ev) => {
    const b = ev.target.closest('[data-accion]');
    if (b) ejecutar(b.dataset.accion, b.dataset.id, b);
  });

  // Refresco automático: así una reserva hecha desde la PWA aparece sola.
  // No se refresca encima de un diálogo abierto o una acción a medio camino.
  function refrescarSiSePuede() {
    if (document.hidden || estado.ocupado || $('dialogo-cancelar').open) return;
    cargar({ silencioso: true });
  }
  setInterval(refrescarSiSePuede, REFRESCO_MS);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refrescarSiSePuede(); });

  cargar();
})();
