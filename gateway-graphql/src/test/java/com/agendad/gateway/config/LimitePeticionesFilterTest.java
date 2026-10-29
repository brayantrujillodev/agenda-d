package com.agendad.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LimitePeticionesFilterTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-03T13:00:10Z"), ZoneOffset.UTC);

    private static MockHttpServletRequest peticion(String ip) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/graphql");
        peticion.setRemoteAddr(ip);
        return peticion;
    }

    private static MockHttpServletResponse ejecutar(LimitePeticionesFilter filtro, MockHttpServletRequest peticion)
            throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());
        return respuesta;
    }

    @Test
    void pasadoElLimiteResponde429ConRetryAfterYErrorEnFormatoGraphql() throws Exception {
        LimitePeticionesFilter filtro = new LimitePeticionesFilter(3, RELOJ);

        for (int i = 0; i < 3; i++) {
            assertThat(ejecutar(filtro, peticion("10.0.0.1")).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse rechazada = ejecutar(filtro, peticion("10.0.0.1"));

        assertThat(rechazada.getStatus()).isEqualTo(429);
        assertThat(rechazada.getHeader("Retry-After")).isEqualTo("50");
        assertThat(rechazada.getContentAsString())
                .contains("\"codigo\":\"DEMASIADAS_PETICIONES\"")
                .contains("Espera 50 segundos");
    }

    @Test
    void elLimiteEsPorOrigen() throws Exception {
        LimitePeticionesFilter filtro = new LimitePeticionesFilter(1, RELOJ);

        assertThat(ejecutar(filtro, peticion("10.0.0.1")).getStatus()).isEqualTo(200);
        assertThat(ejecutar(filtro, peticion("10.0.0.2")).getStatus()).isEqualTo(200);
        assertThat(ejecutar(filtro, peticion("10.0.0.1")).getStatus()).isEqualTo(429);
    }

    @Test
    void xForwardedForNoSirveParaSaltarseElLimite() throws Exception {
        LimitePeticionesFilter filtro = new LimitePeticionesFilter(1, RELOJ);
        ejecutar(filtro, peticion("10.0.0.1"));

        MockHttpServletRequest disfrazada = peticion("10.0.0.1");
        disfrazada.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(ejecutar(filtro, disfrazada).getStatus()).isEqualTo(429);
    }

    @Test
    void elPreflightDeCorsNoGastaCupo() throws Exception {
        LimitePeticionesFilter filtro = new LimitePeticionesFilter(1, RELOJ);
        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/graphql");
        preflight.setRemoteAddr("10.0.0.1");

        ejecutar(filtro, preflight);
        ejecutar(filtro, preflight);

        assertThat(ejecutar(filtro, peticion("10.0.0.1")).getStatus()).isEqualTo(200);
    }
}
