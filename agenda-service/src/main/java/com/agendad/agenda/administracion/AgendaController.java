package com.agendad.agenda.administracion;

import com.agendad.agenda.administracion.dto.RegistrarEstadoRequest;
import com.agendad.agenda.comun.dto.CitaDetalleResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas administrativas: el negocio consulta su agenda y registra el
 * resultado de una cita. El contexto de negocio llega por {@code X-Negocio-Id}
 * (CLAUDE.md, regla 4) — no hay autenticación.
 */
@RestController
public class AgendaController {

    private final AgendaService agendaService;

    public AgendaController(AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    @GetMapping("/v1/agenda/{profesionalId}")
    public List<CitaDetalleResponse> agendaDelDia(
            @RequestHeader("X-Negocio-Id") UUID negocioId,
            @PathVariable UUID profesionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return agendaService.agendaDelDia(negocioId, profesionalId, fecha);
    }

    @PatchMapping("/v1/citas/{id}/estado")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrarEstado(
            @RequestHeader("X-Negocio-Id") UUID negocioId,
            @PathVariable UUID id,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody RegistrarEstadoRequest peticion) {
        agendaService.registrarEstado(negocioId, id, peticion.estado(), correlationId);
    }
}
