package co.edu.fet.agendad.notificaciones.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita CORS para {@code /v1/**}. El panel de recepción
 * ({@code web/panel.html}, docs/TAREAS.md #20) llama a
 * {@code GET /v1/mensajes-fallidos} para la alerta de mensajes caídos
 * (docs/TAREAS.md #16) desde un origen distinto — mismo patrón que
 * {@code agenda-service/.../comun/WebConfig.java}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] origenesPermitidos;

    public WebConfig(@Value("${agendad.cors.origenes-permitidos}") String origenesPermitidos) {
        this.origenesPermitidos = origenesPermitidos.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
                .allowedOrigins(origenesPermitidos)
                .allowedMethods("GET")
                .allowedHeaders("*");
    }
}
