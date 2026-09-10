package com.agendad.agenda.publico;

import com.agendad.agenda.publico.dto.DisponibilidadResponse;
import com.agendad.agenda.publico.dto.ServicioResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas públicas: lo que usa el cliente final sin cuenta. El negocio se
 * resuelve por el {@code slug} de la URL; no hay cabecera de autenticación.
 *
 * La reserva (POST /citas) llega en feature/3.
 */
@RestController
@RequestMapping("/v1/publico/{slug}")
public class PublicoController {

    private final DisponibilidadService disponibilidadService;

    public PublicoController(DisponibilidadService disponibilidadService) {
        this.disponibilidadService = disponibilidadService;
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
}
