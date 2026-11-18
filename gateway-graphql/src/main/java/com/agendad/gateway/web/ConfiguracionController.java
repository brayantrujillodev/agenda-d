package com.agendad.gateway.web;

import com.agendad.gateway.client.AgendaClient;
import com.agendad.gateway.client.dto.BloqueoDto;
import com.agendad.gateway.client.dto.HorarioDto;
import com.agendad.gateway.contexto.ContextoNegocio;
import com.agendad.gateway.error.ErrorGateway;
import com.agendad.gateway.model.Bloqueo;
import com.agendad.gateway.model.BloqueoInput;
import com.agendad.gateway.model.Cita;
import com.agendad.gateway.model.HorarioInput;
import com.agendad.gateway.model.Profesional;
import com.agendad.gateway.model.ProfesionalInput;
import com.agendad.gateway.model.Servicio;
import com.agendad.gateway.model.ServicioInput;
import graphql.GraphQLContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/**
 * Mutaciones del panel: lo que hace el personal del negocio. Reservar no
 * está aquí (lo hace el cliente final por REST público). Cada mutación es
 * una llamada a agenda-service; los eventos los publica agenda-service por
 * su outbox, nunca el gateway.
 */
@Controller
public class ConfiguracionController {

    private static final Set<String> RESULTADOS = Set.of("ATENDIDA", "NO_ASISTIO");

    private final AgendaClient agenda;
    private final CatalogoNegocio catalogo;

    public ConfiguracionController(AgendaClient agenda, CatalogoNegocio catalogo) {
        this.agenda = agenda;
        this.catalogo = catalogo;
    }

    // --- Servicios ----------------------------------------------------

    @MutationMapping
    public Servicio crearServicio(@Argument @Valid ServicioInput entrada, GraphQLContext contexto) {
        Servicio creado = CatalogoNegocio.aServicio(agenda.crearServicio(negocio(contexto), correlacion(contexto),
                entrada.nombre(), entrada.duracionMin(), entrada.precio()));
        catalogo.invalidar(contexto);
        return creado;
    }

    @MutationMapping
    public Servicio actualizarServicio(@Argument String id, @Argument @Valid ServicioInput entrada,
                                       GraphQLContext contexto) {
        Servicio actualizado = CatalogoNegocio.aServicio(agenda.actualizarServicio(negocio(contexto),
                correlacion(contexto), ContextoNegocio.uuid(id, "el servicio"),
                entrada.nombre(), entrada.duracionMin(), entrada.precio()));
        catalogo.invalidar(contexto);
        return actualizado;
    }

    @MutationMapping
    public boolean desactivarServicio(@Argument String id, GraphQLContext contexto) {
        agenda.desactivarServicio(negocio(contexto), correlacion(contexto), ContextoNegocio.uuid(id, "el servicio"));
        catalogo.invalidar(contexto);
        return true;
    }

    // --- Profesionales ------------------------------------------------

    @MutationMapping
    public Profesional crearProfesional(@Argument @Valid ProfesionalInput entrada, GraphQLContext contexto) {
        List<UUID> serviciosIds = entrada.serviciosIds().stream()
                .map(id -> ContextoNegocio.uuid(id, "un servicio"))
                .toList();
        var creado = agenda.crearProfesional(negocio(contexto), correlacion(contexto),
                entrada.nombre(), serviciosIds);
        catalogo.invalidar(contexto);
        return catalogo.profesional(contexto, creado);
    }

    @MutationMapping
    public Profesional definirHorario(
            @Argument String profesionalId,
            @Argument @Size(max = 21, message = "A lo sumo 21 franjas por profesional (3 por día).")
            List<@Valid HorarioInput> horarios,
            GraphQLContext contexto) {
        List<HorarioDto> franjas = horarios.stream()
                .map(h -> new HorarioDto(h.diaSemana(), h.horaInicio(), h.horaFin()))
                .toList();
        var actualizado = agenda.definirHorario(negocio(contexto), correlacion(contexto),
                ContextoNegocio.uuid(profesionalId, "el profesional"), franjas);
        catalogo.invalidar(contexto);
        return catalogo.profesional(contexto, actualizado);
    }

    @MutationMapping
    public Bloqueo crearBloqueo(@Argument @Valid BloqueoInput entrada, GraphQLContext contexto) {
        BloqueoDto creado = agenda.crearBloqueo(negocio(contexto), correlacion(contexto),
                ContextoNegocio.uuid(entrada.profesionalId(), "el profesional"),
                entrada.inicio(), entrada.fin(), entrada.motivo());
        return new Bloqueo(creado.id(), creado.inicio(), creado.fin(), creado.motivo());
    }

    // --- Citas --------------------------------------------------------

    @MutationMapping
    public Cita registrarAsistencia(@Argument String citaId, @Argument String estado, GraphQLContext contexto) {
        if (!RESULTADOS.contains(estado)) {
            throw ErrorGateway.datosInvalidos("Solo se puede registrar ATENDIDA o NO_ASISTIO.");
        }
        UUID id = ContextoNegocio.uuid(citaId, "la cita");
        agenda.registrarAsistencia(negocio(contexto), correlacion(contexto), id, estado);
        return catalogo.cita(contexto, agenda.cita(negocio(contexto), correlacion(contexto), id));
    }

    @MutationMapping
    public Cita cancelarCita(
            @Argument String citaId,
            @Argument @Size(max = 200, message = "El motivo no puede pasar de 200 caracteres.") String motivo,
            GraphQLContext contexto) {
        var cancelada = agenda.cancelarCita(negocio(contexto), correlacion(contexto),
                ContextoNegocio.uuid(citaId, "la cita"), motivo);
        return catalogo.cita(contexto, cancelada);
    }

    private static UUID negocio(GraphQLContext contexto) {
        return ContextoNegocio.negocioId(contexto);
    }

    private static String correlacion(GraphQLContext contexto) {
        return ContextoNegocio.correlationId(contexto);
    }
}
