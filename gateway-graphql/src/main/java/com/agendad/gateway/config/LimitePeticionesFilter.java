package com.agendad.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting básico sobre {@code /graphql}: a lo sumo N peticiones por
 * minuto por dirección de origen (ventana fija). Al pasarse responde 429
 * con {@code Retry-After} y un cuerpo con el formato de error de GraphQL,
 * para que el panel lo muestre como cualquier otro error.
 *
 * <p>En memoria y por instancia: suficiente para un solo gateway. Con
 * varias réplicas haría falta un almacén compartido (p. ej. Redis), que
 * está fuera del alcance del proyecto.
 */
@Component
public class LimitePeticionesFilter extends OncePerRequestFilter {

    private static final long MINUTO_MS = 60_000;
    private static final int LIMPIAR_DESDE = 10_000;

    private final int limitePorMinuto;
    private final Clock reloj;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    @Autowired
    public LimitePeticionesFilter(@Value("${agendad.limite.peticiones-por-minuto}") int limitePorMinuto) {
        this(limitePorMinuto, Clock.systemUTC());
    }

    LimitePeticionesFilter(int limitePorMinuto, Clock reloj) {
        this.limitePorMinuto = limitePorMinuto;
        this.reloj = reloj;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // El preflight de CORS no ejecuta nada: no gasta cupo.
        return !request.getRequestURI().startsWith("/graphql")
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long ahora = reloj.millis();
        long minuto = ahora / MINUTO_MS;
        if (ventanas.size() > LIMPIAR_DESDE) {
            ventanas.values().removeIf(v -> v.minuto < minuto);
        }

        Ventana ventana = ventanas.compute(origen(request),
                (clave, actual) -> actual == null || actual.minuto != minuto ? new Ventana(minuto) : actual);
        if (ventana.peticiones.incrementAndGet() <= limitePorMinuto) {
            chain.doFilter(request, response);
            return;
        }

        long segundosParaReintentar = Math.max(1, ((minuto + 1) * MINUTO_MS - ahora + 999) / 1000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(segundosParaReintentar));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"errors":[{"message":"Hiciste demasiadas consultas seguidas. Espera %d segundos e intenta de nuevo.",\
                "extensions":{"codigo":"DEMASIADAS_PETICIONES","classification":"BAD_REQUEST"}}]}"""
                .formatted(segundosParaReintentar));
    }

    /**
     * La IP de quien llama. Se ignora {@code X-Forwarded-For} a propósito:
     * sin un proxy de confianza delante, cualquiera lo falsifica y se salta
     * el límite.
     */
    private static String origen(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private static final class Ventana {
        private final long minuto;
        private final AtomicInteger peticiones = new AtomicInteger();

        private Ventana(long minuto) {
            this.minuto = minuto;
        }
    }
}
