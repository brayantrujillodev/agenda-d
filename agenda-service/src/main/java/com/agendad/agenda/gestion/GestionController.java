package com.agendad.agenda.gestion;

import com.agendad.agenda.gestion.dto.CitaDetalleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ruta de gestión: el cliente opera su propia cita con el token que recibió
 * al reservar. No hay cabecera de negocio ni autenticación; el token es la
 * credencial y da acceso a una sola cita.
 */
@RestController
@RequestMapping("/v1/gestion/{token}")
public class GestionController {

    private final GestionService gestionService;

    public GestionController(GestionService gestionService) {
        this.gestionService = gestionService;
    }

    @GetMapping
    public CitaDetalleResponse consultar(@PathVariable String token) {
        return gestionService.consultar(token);
    }

    /**
     * Cancela la cita, libera el cupo y publica {@code citas.canceladas} por
     * outbox. {@code confirmar=true} es obligatorio: evita cancelaciones por
     * un clic accidental.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(
            @PathVariable String token,
            @RequestParam boolean confirmar,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId) {
        gestionService.cancelar(token, confirmar, correlationId);
    }
}
