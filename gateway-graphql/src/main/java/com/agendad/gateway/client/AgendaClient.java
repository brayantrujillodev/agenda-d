package com.agendad.gateway.client;

import static com.agendad.gateway.contexto.ContextoNegocio.CABECERA_CORRELACION;
import static com.agendad.gateway.contexto.ContextoNegocio.CABECERA_NEGOCIO;

import com.agendad.gateway.client.dto.BloqueoDto;
import com.agendad.gateway.client.dto.CitaDto;
import com.agendad.gateway.client.dto.HorarioDto;
import com.agendad.gateway.client.dto.NegocioConfigDto;
import com.agendad.gateway.client.dto.ProfesionalDto;
import com.agendad.gateway.client.dto.ServicioDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP de agenda-service (contrato: docs/openapi/agenda-service.yaml).
 * Toda llamada lleva el {@code X-Negocio-Id} de la petición GraphQL: el
 * gateway nunca decide de qué negocio son los datos.
 *
 * <p>Los errores del servicio se convierten en {@code ErrorGateway} con su
 * mismo código y mensaje ({@link TraductorErroresHttp}); si el servicio no
 * responde, en {@code SERVICIO_NO_DISPONIBLE} ({@link LlamadaRemota}).
 */
@Service
public class AgendaClient {

    static final String NOMBRE = "el servicio de agenda";

    private final RestClient http;

    public AgendaClient(@Qualifier("agendaRestClient") RestClient http) {
        this.http = http;
    }

    public NegocioConfigDto negocio(UUID negocioId, String correlationId) {
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.get()
                .uri("/v1/negocio")
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .retrieve()
                .body(NegocioConfigDto.class));
    }

    public List<CitaDto> agenda(UUID negocioId, String correlationId, UUID profesionalId, LocalDate fecha) {
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.get()
                .uri("/v1/agenda/{profesionalId}?fecha={fecha}", profesionalId, fecha)
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .retrieve()
                .body(new ParameterizedTypeReference<List<CitaDto>>() {}));
    }

    public CitaDto cita(UUID negocioId, String correlationId, UUID citaId) {
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.get()
                .uri("/v1/citas/{id}", citaId)
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .retrieve()
                .body(CitaDto.class));
    }

    public List<BloqueoDto> bloqueos(UUID negocioId, String correlationId, UUID profesionalId) {
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.get()
                .uri("/v1/profesionales/{id}/bloqueos", profesionalId)
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .retrieve()
                .body(new ParameterizedTypeReference<List<BloqueoDto>>() {}));
    }

    // --- Escrituras ---------------------------------------------------

    public ServicioDto crearServicio(UUID negocioId, String correlationId,
                                     String nombre, int duracionMin, BigDecimal precio) {
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.post()
                .uri("/v1/servicios")
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nombre", nombre, "duracionMin", duracionMin, "precio", precio))
                .retrieve()
                .body(ServicioDto.class));
    }

    public ServicioDto actualizarServicio(UUID negocioId, String correlationId, UUID servicioId,
                                          String nombre, int duracionMin, BigDecimal precio) {
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.put()
                .uri("/v1/servicios/{id}", servicioId)
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nombre", nombre, "duracionMin", duracionMin, "precio", precio))
                .retrieve()
                .body(ServicioDto.class));
    }

    public void desactivarServicio(UUID negocioId, String correlationId, UUID servicioId) {
        LlamadaRemota.ejecutar(NOMBRE, () -> http.delete()
                .uri("/v1/servicios/{id}", servicioId)
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .retrieve()
                .toBodilessEntity());
    }

    public ProfesionalDto crearProfesional(UUID negocioId, String correlationId,
                                           String nombre, List<UUID> serviciosIds) {
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.post()
                .uri("/v1/profesionales")
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nombre", nombre, "serviciosIds", serviciosIds))
                .retrieve()
                .body(ProfesionalDto.class));
    }

    public ProfesionalDto definirHorario(UUID negocioId, String correlationId, UUID profesionalId,
                                         List<HorarioDto> horarios) {
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.put()
                .uri("/v1/profesionales/{id}/horarios", profesionalId)
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(horarios)
                .retrieve()
                .body(ProfesionalDto.class));
    }

    public BloqueoDto crearBloqueo(UUID negocioId, String correlationId, UUID profesionalId,
                                   Instant inicio, Instant fin, String motivo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("profesionalId", profesionalId);
        cuerpo.put("inicio", inicio);
        cuerpo.put("fin", fin);
        if (motivo != null) {
            cuerpo.put("motivo", motivo);
        }
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.post()
                .uri("/v1/bloqueos")
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(BloqueoDto.class));
    }

    public void registrarAsistencia(UUID negocioId, String correlationId, UUID citaId, String estado) {
        LlamadaRemota.ejecutar(NOMBRE, () -> http.patch()
                .uri("/v1/citas/{id}/estado", citaId)
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("estado", estado))
                .retrieve()
                .toBodilessEntity());
    }

    public CitaDto cancelarCita(UUID negocioId, String correlationId, UUID citaId, String motivo) {
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.post()
                .uri("/v1/citas/{id}/cancelacion", citaId)
                .headers(h -> cabeceras(h, negocioId, correlationId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(motivo == null ? Map.of() : Map.of("motivo", motivo))
                .retrieve()
                .body(CitaDto.class));
    }

    private static void cabeceras(HttpHeaders h, UUID negocioId, String correlationId) {
        h.set(CABECERA_NEGOCIO, negocioId.toString());
        if (correlationId != null) {
            h.set(CABECERA_CORRELACION, correlationId);
        }
    }
}
