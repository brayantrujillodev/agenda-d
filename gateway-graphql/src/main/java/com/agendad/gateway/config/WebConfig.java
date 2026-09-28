package com.agendad.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita CORS para {@code /graphql}. El panel de recepción
 * ({@code web/panel.html}, docs/TAREAS.md #20) corre en un origen distinto
 * y lo llama con {@code fetch()}; sin esto el navegador bloquea la
 * respuesta aunque curl la reciba sin problema (CORS lo aplica el
 * navegador, no el servidor) — mismo patrón que
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
        registry.addMapping("/graphql")
                .allowedOrigins(origenesPermitidos)
                .allowedMethods("POST")
                .allowedHeaders("*");
    }
}
