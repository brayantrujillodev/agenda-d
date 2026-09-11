package com.agendad.agenda.publico;

import com.agendad.agenda.publico.dto.CitaCreadaResponse;
import com.agendad.agenda.publico.dto.DisponibilidadResponse;
import com.agendad.agenda.publico.dto.ReservaRequest;
import com.agendad.agenda.publico.dto.ServicioResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas públicas: lo que usa el cliente final sin cuenta. El negocio se
 * resuelve por el {@code slug} de la URL; no hay cabecera de autenticación.
 */
@RestController
@RequestMapping("/v1/publico/{slug}")
public class PublicoController {

    private final DisponibilidadService disponibilidadService;
    private final ReservaService reservaService;

    public PublicoController(DisponibilidadService disponibilidadService, ReservaService reservaService) {
        this.disponibilidadService = disponibilidadService;
        this.reservaService = reservaService;
    }

    @GetMapping("/servicios")
    public List<ServicioResponse> servicios(@PathVariable String slug) {
        return disponibilidadService.listarServicios(slug);
    }

    @GetMapping("/disponibilidad")
    public DisponibilidadResponse disponibilidad(
            @PathVariable String slug,
            @RequestParam UUID servicioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) UUID profesionalId) {
        return disponibilidadService.calcular(slug, servicioId, fecha, profesionalId);
    }

    /**
     * Reservar una cita. La ausencia de solapamiento la garantiza la
     * restricción {@code cita_sin_solape} de PostgreSQL: si el motor rechaza
     * la escritura se responde 409 con los cupos más cercanos. Reenviar la
     * misma {@code Idempotency-Key} devuelve la cita original.
     */
    @PostMapping("/citas")
    @ResponseStatus(HttpStatus.CREATED)
    public CitaCreadaResponse reservar(
            @PathVariable String slug,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody ReservaRequest peticion) {
        return reservaService.reservar(slug, idempotencyKey, correlationId, peticion);
    }
}
