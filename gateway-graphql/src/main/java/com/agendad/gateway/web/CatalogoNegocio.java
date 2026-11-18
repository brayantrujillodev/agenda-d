package com.agendad.gateway.web;

import com.agendad.gateway.client.AgendaClient;
import com.agendad.gateway.client.dto.CitaDto;
import com.agendad.gateway.client.dto.NegocioConfigDto;
import com.agendad.gateway.client.dto.ProfesionalDto;
import com.agendad.gateway.client.dto.ServicioDto;
import com.agendad.gateway.contexto.ContextoNegocio;
import com.agendad.gateway.model.Cita;
import com.agendad.gateway.model.HorarioAtencion;
import com.agendad.gateway.model.Negocio;
import com.agendad.gateway.model.Profesional;
import com.agendad.gateway.model.Servicio;
import graphql.GraphQLContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Configuración del negocio de la petición (servicios, profesionales,
 * horarios), leída de agenda-service UNA vez por petición GraphQL y
 * guardada en su contexto: varios campos la necesitan (nombres de los
 * servicios en las citas, en topServicios, la zona horaria para "hoy") y
 * sin esto cada uno haría su propia llamada.
 *
 * <p>Las mutaciones la invalidan: la siguiente lectura en la misma petición
 * ya ve el cambio.
 */
@Component
public class CatalogoNegocio {

    private static final String CLAVE = "agendad.catalogo";

    private final AgendaClient agenda;

    public CatalogoNegocio(AgendaClient agenda) {
        this.agenda = agenda;
    }

    public Negocio negocio(GraphQLContext contexto) {
        return contexto.computeIfAbsent(CLAVE, clave -> armar(agenda.negocio(
                ContextoNegocio.negocioId(contexto), ContextoNegocio.correlationId(contexto))));
    }

    public void invalidar(GraphQLContext contexto) {
        contexto.delete(CLAVE);
    }

    /** "Hoy" en la zona del negocio, no del servidor (CLAUDE.md, regla 3). */
    public LocalDate hoy(GraphQLContext contexto) {
        return LocalDate.now(ZoneId.of(negocio(contexto).zonaHoraria()));
    }

    public Optional<Servicio> servicio(GraphQLContext contexto, String servicioId) {
        return negocio(contexto).servicios().stream().filter(s -> s.id().equals(servicioId)).findFirst();
    }

    public Profesional profesional(GraphQLContext contexto, ProfesionalDto dto) {
        Negocio negocio = negocio(contexto);
        return aProfesional(dto, porId(negocio.servicios()), negocio.zonaHoraria());
    }

    /**
     * La cita tal como la ve el panel. El celular va enmascarado (contrato
     * GraphQL: {@code clienteCelular}): agenda-service lo entrega completo
     * porque lo pide el negocio, pero el panel solo necesita reconocerlo.
     */
    public Cita cita(GraphQLContext contexto, CitaDto dto) {
        Negocio negocio = negocio(contexto);
        Servicio servicio = negocio.servicios().stream()
                .filter(s -> s.id().equals(dto.servicioId()))
                .findFirst()
                .orElseGet(() -> new Servicio(dto.servicioId(), dto.servicioNombre(), 0, BigDecimal.ZERO, false));
        Profesional profesional = negocio.profesionales().stream()
                .filter(p -> p.id().equals(dto.profesionalId()))
                .findFirst()
                .orElseGet(() -> new Profesional(dto.profesionalId(), dto.profesionalNombre(), false,
                        List.of(), List.of(), negocio.zonaHoraria()));
        return new Cita(dto.id(), dto.inicio(), dto.fin(), dto.horaLocal(), servicio, profesional,
                dto.clienteNombre(), enmascarar(dto.clienteCelular()), dto.estado());
    }

    public static Servicio aServicio(ServicioDto dto) {
        return new Servicio(dto.id(), dto.nombre(), dto.duracionMin(), dto.precio(), dto.activo());
    }

    /** {@code 3001234567 -> 300****567}, igual que la ruta de gestión de agenda-service. */
    static String enmascarar(String celular) {
        if (celular == null || celular.length() < 7) {
            return "***";
        }
        return celular.substring(0, 3) + "****" + celular.substring(celular.length() - 3);
    }

    private static Negocio armar(NegocioConfigDto config) {
        List<Servicio> servicios = config.servicios().stream().map(CatalogoNegocio::aServicio).toList();
        Map<String, Servicio> serviciosPorId = porId(servicios);
        List<Profesional> profesionales = config.profesionales().stream()
                .map(p -> aProfesional(p, serviciosPorId, config.zonaHoraria()))
                .toList();
        return new Negocio(config.id(), config.nombre(), config.slugPublico(), config.zonaHoraria(),
                servicios, profesionales);
    }

    private static Profesional aProfesional(ProfesionalDto dto, Map<String, Servicio> serviciosPorId, String zona) {
        List<Servicio> servicios = dto.serviciosIds().stream()
                .map(serviciosPorId::get)
                .filter(s -> s != null)
                .toList();
        List<HorarioAtencion> horarios = dto.horarios().stream()
                .map(h -> new HorarioAtencion(h.diaSemana(), h.horaInicio(), h.horaFin()))
                .toList();
        return new Profesional(dto.id(), dto.nombre(), dto.activo(), servicios, horarios, zona);
    }

    private static Map<String, Servicio> porId(List<Servicio> servicios) {
        return servicios.stream().collect(Collectors.toMap(Servicio::id, Function.identity()));
    }
}
