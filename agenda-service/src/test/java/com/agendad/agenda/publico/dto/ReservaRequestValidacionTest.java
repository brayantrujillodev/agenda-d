package com.agendad.agenda.publico.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Formato de los datos del cliente. Casos sacados de lo que se alcanzó a guardar sin estas reglas. */
class ReservaRequestValidacionTest {

    private static final Validator VALIDADOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static ReservaRequest con(String nombre, String celular) {
        return new ReservaRequest(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), nombre, celular);
    }

    private static boolean valido(String nombre, String celular) {
        return VALIDADOR.validate(con(nombre, celular)).isEmpty();
    }

    @Test
    void nombresValidos() {
        for (String n : List.of("Juan Pérez", "María José Núñez", "Brayan Trujillo Ramon",
                "Ana O'Neil", "Jean-Luc Picard", "Juan de la Cruz")) {
            assertThat(valido(n, "3001234567")).as(n).isTrue();
        }
    }

    @Test
    void nombresInvalidos() {
        for (String n : List.of(
                "<script>alert('XSS');</script>",
                "%/\"()/(·&%!\"·(/&!&\"(&(",
                "rgnbrfhn",                    // una sola palabra
                "Prueba MVP 30092026",         // números
                "Juan_Perez",
                "Juan -Perez",                 // guion suelto
                "Uno Dos Tres Cuatro Cinco Seis Siete",
                "Carlos Andrès",               // tilde grave, no se usa en español
                "Ab",
                "")) {
            assertThat(valido(n, "3001234567")).as(n).isFalse();
        }
    }

    @Test
    void espaciosDeSobraSeRecortanAntesDeValidar() {
        ReservaRequest r = con("  Juan    Pérez  ", " 3001234567 ");
        assertThat(r.clienteNombre()).isEqualTo("Juan Pérez");
        assertThat(r.clienteCelular()).isEqualTo("3001234567");
        assertThat(VALIDADOR.validate(r)).isEmpty();
    }

    @Test
    void celulares() {
        assertThat(valido("Juan Pérez", "3001234567")).isTrue();
        for (String c : List.of("0000000000", "1111111111", "300123456", "30012345678",
                "300 123 4567", "+573001234567", "300123456a")) {
            assertThat(valido("Juan Pérez", c)).as(c).isFalse();
        }
    }
}
