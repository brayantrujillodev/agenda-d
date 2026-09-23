package com.agendad.agenda.comun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita CORS para las rutas {@code /v1/**}. La PWA (carpeta {@code web/})
 * corre en un origen distinto al de esta API (servidor de desarrollo, o el
 * dominio donde se publique) y la llama con {@code fetch()}; sin esto el
 * navegador bloquea la respuesta aunque curl/Postman la reciban sin problema
 * (CORS lo aplica el navegador, no el servidor).
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
                .allowedMethods("GET", "POST", "DELETE", "PATCH")
                .allowedHeaders("*");
    }
}
