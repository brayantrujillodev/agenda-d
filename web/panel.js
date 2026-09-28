/*
 * AGENDA-D · Panel de recepción (docs/TAREAS.md #20)
 * HTML/CSS/JS nativo, sin framework ni build — mismo criterio que app.js.
 *
 * Una sola consulta GraphQL trae agenda del día + configuración + métricas
 * del mes (gateway-graphql, panelRecepcion). Es la razón de existir del
 * gateway: sin esto serían 3-4 peticiones REST desde un celular con mala
 * señal. Además consulta notificaciones-service para la alerta de
 * mensajes fallidos (docs/TAREAS.md #16).
 */
(function () {
  'use strict';

  const CFG = window.CONFIG;
  const panel = document.getElementById('panel');

  const QUERY = `
    query PanelRecepcion($fecha: Date) {
      panelRecepcion(fecha: $fecha) {
        fecha
        negocio { nombre zonaHoraria }
        citasDelDia {
          horaLocal
          clienteNombre
          clienteCelular
          servicio { nombre }
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

  async function consultarPanel(fecha) {
    const res = await fetch(CFG.gatewayUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: QUERY, variables: { fecha } }),
    });
    const cuerpo = await res.json();
    if (cuerpo.errors && cuerpo.errors.length) {
      throw new Error(cuerpo.errors.map((e) => e.message).join('; '));
    }
    return cuerpo.data.panelRecepcion;
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

  const ESTADO_ETIQUETA = {
    CONFIRMADA: 'Confirmada',
    CANCELADA: 'Cancelada',
    ATENDIDA: 'Atendida',
    NO_ASISTIO: 'No asistió',
  };

  function escapar(texto) {
    const d = document.createElement('div');
    d.textContent = texto == null ? '' : String(texto);
    return d.innerHTML;
  }

  function pintarAlerta(cantidad) {
    if (!cantidad) return '';
    return `
      <div class="banner banner--offline" style="position: static; margin-bottom: 16px;">
        ⚠ ${cantidad} mensaje${cantidad === 1 ? '' : 's'} de Kafka no se pudo${cantidad === 1 ? '' : 'ieron'} procesar
        y quedó${cantidad === 1 ? '' : 'aron'} en <code>citas.dlq</code>.
        Ver <code>GET /v1/mensajes-fallidos</code> en notificaciones-service.
      </div>`;
  }

  function pintarCitas(citas) {
    if (!citas.length) {
      return '<p class="cupos__vacio">No hay citas para este día.</p>';
    }
    const filas = citas.map((c) => `
      <tr>
        <td>${escapar(c.horaLocal)}</td>
        <td>${escapar(c.clienteNombre)}</td>
        <td>${escapar(c.servicio.nombre)}</td>
        <td><span class="panel-chip panel-chip--${c.estado.toLowerCase()}">${escapar(ESTADO_ETIQUETA[c.estado] || c.estado)}</span></td>
      </tr>`).join('');
    return `
      <table class="panel-tabla">
        <thead><tr><th>Hora</th><th>Cliente</th><th>Servicio</th><th>Estado</th></tr></thead>
        <tbody>${filas}</tbody>
      </table>`;
  }

  function pintarMetricas(m) {
    const tile = (valor, etiqueta) => `
      <div class="panel-metrica">
        <span class="panel-metrica__valor">${escapar(valor)}</span>
        <span class="panel-metrica__etiqueta">${escapar(etiqueta)}</span>
      </div>`;
    return `
      <div class="panel-metricas">
        ${tile(m.totalCitas, 'Citas del mes')}
        ${tile(m.atendidas, 'Atendidas')}
        ${tile(m.canceladas, 'Canceladas')}
        ${tile(m.noAsistio, 'No asistió')}
        ${tile(m.ocupacion.toFixed(1) + '%', 'Ocupación')}
        ${tile(m.tasaInasistencia.toFixed(1) + '%', 'Tasa de inasistencia')}
      </div>
      <p class="vista__ayuda">Periodo: ${escapar(m.desde)} — ${escapar(m.hasta)}</p>`;
  }

  function pintarPanel(datos, mensajesFallidos) {
    document.getElementById('negocio-nombre').textContent = datos.negocio.nombre;
    document.getElementById('pie-nombre').textContent = datos.negocio.nombre;

    panel.innerHTML = `
      <div class="vista">
        ${pintarAlerta(mensajesFallidos)}
        <h1 class="vista__titulo">Agenda del día</h1>
        <p class="vista__ayuda">
          <input type="date" id="selector-fecha" class="campo__control" style="width: auto; display: inline-block;"
                 value="${escapar(datos.fecha)}">
        </p>
        ${pintarCitas(datos.citasDelDia)}

        <h2 class="vista__titulo" style="margin-top: 32px;">Indicadores del mes</h2>
        ${pintarMetricas(datos.metricasDelMes)}
      </div>
    `;

    document.getElementById('selector-fecha').addEventListener('change', (ev) => {
      cargar(ev.target.value);
    });
  }

  function pintarError(err) {
    panel.innerHTML = `
      <div class="vista">
        <h1 class="vista__titulo">No pudimos cargar el panel</h1>
        <p class="cupos__error">${escapar(err.message || 'Revisa que gateway-graphql esté corriendo.')}</p>
        <button type="button" class="boton boton--secundario" onclick="location.reload()">Reintentar</button>
      </div>`;
  }

  async function cargar(fecha) {
    panel.innerHTML = '<p class="cargando">Cargando…</p>';
    try {
      const [datos, mensajesFallidos] = await Promise.all([
        consultarPanel(fecha || null),
        contarMensajesFallidos(),
      ]);
      pintarPanel(datos, mensajesFallidos);
    } catch (err) {
      pintarError(err);
    }
  }

  cargar(null);
})();
