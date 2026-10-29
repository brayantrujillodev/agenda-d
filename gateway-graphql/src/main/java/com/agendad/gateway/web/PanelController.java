package com.agendad.gateway.web;

import com.agendad.gateway.client.AgendaClient;
import com.agendad.gateway.client.AnaliticaClient;
import com.agendad.gateway.client.dto.MetricasDto;
import com.agendad.gateway.contexto.ContextoNegocio;
import com.agendad.gateway.error.ErrorGateway;
import com.agendad.gateway.model.Bloqueo;
import com.agendad.gateway.model.Cita;
import com.agendad.gateway.model.ConteoServicio;
import com.agendad.gateway.model.Metricas;
import com.agendad.gateway.model.Negocio;
import com.agendad.gateway.model.PanelRecepcion;
import com.agendad.gateway.model.Profesional;
import graphql.GraphQLContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * Consultas del panel. El gateway compone; no tiene base de datos ni reglas
 * de negocio (ver el encabezado de schema.graphqls). Cada campo que viene
 * de otro servicio se resuelve por separado, así un fallo queda en su campo.
 */
@Controller
public class PanelController {

    /** Una consulta de métricas no puede abarcar más de un año: es lo que el panel necesita. */
    private static final long MAX_DIAS_METRICAS = 366;

    private final AgendaClient agenda;
    private final AnaliticaClient analitica;
    private final CatalogoNegocio catalogo;

    public PanelController(AgendaClient agenda, AnaliticaClient analitica, CatalogoNegocio catalogo) {
        this.agenda = agenda;
        this.analitica = analitica;
        this.catalogo = catalogo;
    }

    // --- panelRecepcion ----------------------------------------------

    @QueryMapping
    public PanelRecepcion panelRecepcion(@Argument LocalDate fecha, GraphQLContext contexto) {
        return new PanelRecepcion(fecha != null ? fecha : catalogo.hoy(contexto));
    }

    @SchemaMapping(typeName = "PanelRecepcion", field = "negocio")
    public Negocio negocioDelPanel(PanelRecepcion panel, GraphQLContext contexto) {
        return catalogo.negocio(contexto);
    }

    /** Las citas del día de TODOS los profesionales del negocio, ordenadas por hora. */
    @SchemaMapping(typeName = "PanelRecepcion", field = "citasDelDia")
    public List<Cita> citasDelDia(PanelRecepcion panel, GraphQLContext contexto) {
        return catalogo.negocio(contexto).profesionales().stream()
                .flatMap(p -> citasDe(contexto, UUID.fromString(p.id()), panel.fecha()).stream())
                .sorted(Comparator.comparing(Cita::inicio).thenComparing(c -> c.profesional().nombre()))
                .toList();
    }

    /** Del día 1 del mes a la fecha del panel. Admite null: si analitica cae, el resto sale. */
    @SchemaMapping(typeName = "PanelRecepcion", field = "metricasDelMes")
    public Metricas metricasDelMes(PanelRecepcion panel, GraphQLContext contexto) {
        return metricasEntre(contexto, panel.fecha().withDayOfMonth(1), panel.fecha());
    }

    // --- Consultas sueltas -------------------------------------------

    @QueryMapping
    public Negocio negocio(GraphQLContext contexto) {
        return catalogo.negocio(contexto);
    }

    @QueryMapping
    public List<Cita> agenda(@Argument String profesionalId, @Argument LocalDate fecha, GraphQLContext contexto) {
        return citasDe(contexto, ContextoNegocio.uuid(profesionalId, "el profesional"), fecha);
    }

    @QueryMapping
    public Metricas metricas(@Argument LocalDate desde, @Argument LocalDate hasta, GraphQLContext contexto) {
        if (ChronoUnit.DAYS.between(desde, hasta) > MAX_DIAS_METRICAS) {
            throw ErrorGateway.datosInvalidos("El periodo de métricas no puede pasar de un año.");
        }
        return metricasEntre(contexto, desde, hasta);
    }

    @QueryMapping
    public List<Bloqueo> bloqueos(@Argument String profesionalId, GraphQLContext contexto) {
        return agenda.bloqueos(ContextoNegocio.negocioId(contexto), ContextoNegocio.correlationId(contexto),
                        ContextoNegocio.uuid(profesionalId, "el profesional")).stream()
                .map(b -> new Bloqueo(b.id(), b.inicio(), b.fin(), b.motivo()))
                .toList();
    }

    // --- Campos anidados ---------------------------------------------

    /** {@code Profesional.agenda(fecha)}: sin fecha, hoy en la zona del negocio. */
    @SchemaMapping(typeName = "Profesional", field = "agenda")
    public List<Cita> agendaDelProfesional(Profesional profesional, @Argument LocalDate fecha, GraphQLContext contexto) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now(ZoneId.of(profesional.zonaHoraria()));
        return citasDe(contexto, UUID.fromString(profesional.id()), dia);
    }

    @SchemaMapping(typeName = "Metricas", field = "topServicios")
    public List<ConteoServicio> topServicios(Metricas metricas, GraphQLContext contexto) {
        if (metricas.conteosPorServicio().isEmpty()) {
            return List.of();
        }
        return metricas.conteosPorServicio().stream()
                .flatMap(c -> catalogo.servicio(contexto, c.servicioId()).stream()
                        .map(s -> new ConteoServicio(s, Math.toIntExact(c.cantidad()))))
                .toList();
    }

    // -----------------------------------------------------------------

    private List<Cita> citasDe(GraphQLContext contexto, UUID profesionalId, LocalDate fecha) {
        return agenda.agenda(ContextoNegocio.negocioId(contexto), ContextoNegocio.correlationId(contexto),
                        profesionalId, fecha).stream()
                .map(dto -> catalogo.cita(contexto, dto))
                .toList();
    }

    private Metricas metricasEntre(GraphQLContext contexto, LocalDate desde, LocalDate hasta) {
        MetricasDto m = analitica.metricas(ContextoNegocio.negocioId(contexto), ContextoNegocio.correlationId(contexto),
                desde, hasta);
        return new Metricas(m.desde(), m.hasta(),
                Math.toIntExact(m.totalCitas()), Math.toIntExact(m.atendidas()),
                Math.toIntExact(m.noAsistio()), Math.toIntExact(m.canceladas()),
                m.ocupacion(), m.tasaInasistencia(),
                m.topServicios() == null ? List.of() : m.topServicios());
    }
}
