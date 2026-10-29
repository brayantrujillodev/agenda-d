package com.agendad.agenda.configuracion;

import com.agendad.agenda.configuracion.dto.BloqueoRequest;
import com.agendad.agenda.configuracion.dto.BloqueoResponse;
import com.agendad.agenda.configuracion.dto.HorarioDto;
import com.agendad.agenda.configuracion.dto.NegocioConfigResponse;
import com.agendad.agenda.configuracion.dto.ProfesionalAdminResponse;
import com.agendad.agenda.configuracion.dto.ProfesionalRequest;
import com.agendad.agenda.configuracion.dto.ServicioAdminResponse;
import com.agendad.agenda.configuracion.dto.ServicioRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas de configuración del negocio. Las consume gateway-graphql para las
 * mutaciones del panel. Contexto de negocio por {@code X-Negocio-Id}
 * (CLAUDE.md, regla 4); no hay autenticación.
 */
@RestController
public class ConfiguracionController {

    private final ConfiguracionService configuracion;

    public ConfiguracionController(ConfiguracionService configuracion) {
        this.configuracion = configuracion;
    }

    @GetMapping("/v1/negocio")
    public NegocioConfigResponse negocio(@RequestHeader("X-Negocio-Id") UUID negocioId) {
        return configuracion.negocio(negocioId);
    }

    @PostMapping("/v1/servicios")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicioAdminResponse crearServicio(
            @RequestHeader("X-Negocio-Id") UUID negocioId,
            @Valid @RequestBody ServicioRequest peticion) {
        return configuracion.crearServicio(negocioId, peticion);
    }

    @PutMapping("/v1/servicios/{id}")
    public ServicioAdminResponse actualizarServicio(
            @RequestHeader("X-Negocio-Id") UUID negocioId,
            @PathVariable UUID id,
            @Valid @RequestBody ServicioRequest peticion) {
        return configuracion.actualizarServicio(negocioId, id, peticion);
    }

    @DeleteMapping("/v1/servicios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarServicio(
            @RequestHeader("X-Negocio-Id") UUID negocioId,
            @PathVariable UUID id) {
        configuracion.desactivarServicio(negocioId, id);
    }

    @PostMapping("/v1/profesionales")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfesionalAdminResponse crearProfesional(
            @RequestHeader("X-Negocio-Id") UUID negocioId,
            @Valid @RequestBody ProfesionalRequest peticion) {
        return configuracion.crearProfesional(negocioId, peticion);
    }

    @PutMapping("/v1/profesionales/{id}/horarios")
    public ProfesionalAdminResponse definirHorario(
            @RequestHeader("X-Negocio-Id") UUID negocioId,
            @PathVariable UUID id,
            @RequestBody List<HorarioDto> horarios) {
        return configuracion.definirHorario(negocioId, id, horarios);
    }

    @GetMapping("/v1/profesionales/{id}/bloqueos")
    public List<BloqueoResponse> bloqueos(
            @RequestHeader("X-Negocio-Id") UUID negocioId,
            @PathVariable UUID id) {
        return configuracion.bloqueosVigentes(negocioId, id);
    }

    @PostMapping("/v1/bloqueos")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueoResponse crearBloqueo(
            @RequestHeader("X-Negocio-Id") UUID negocioId,
            @Valid @RequestBody BloqueoRequest peticion) {
        return configuracion.crearBloqueo(negocioId, peticion);
    }
}
