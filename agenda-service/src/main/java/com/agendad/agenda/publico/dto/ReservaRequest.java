package com.agendad.agenda.publico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.text.Normalizer;
import java.time.Instant;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /v1/publico/{slug}/citas}. El {@code fin} no se
 * envía: lo calcula el servidor con la duración del servicio.
 *
 * <p>Las reglas de formato son las de {@code ReservaRequest} en
 * docs/openapi/agenda-service.yaml y las repite web/app.js para avisar antes
 * de enviar. Que el {@code inicio} sea un cupo real lo comprueba
 * {@link com.agendad.agenda.publico.ValidadorCupo}.
 */
public record ReservaRequest(
        @NotNull(message = "Falta el servicio.")
        UUID servicioId,

        @NotNull(message = "Falta el profesional.")
        UUID profesionalId,

        @NotNull(message = "Falta la hora de inicio.")
        Instant inicio,

        @NotBlank(message = "Falta el nombre.")
        @Size(min = 5, max = 80, message = "El nombre debe tener entre 5 y 80 caracteres.")
        @Pattern(regexp = NOMBRE,
                message = "Escribe tu nombre y apellido solo con letras, sin números ni símbolos.")
        String clienteNombre,

        @NotBlank(message = "Falta el celular.")
        @Pattern(regexp = CELULAR,
                message = "El celular debe tener 10 dígitos y empezar por 3, sin espacios ni +57.")
        String clienteCelular) {

    /** Una palabra: letras (con tildes y ñ), con apóstrofo o guion solo entre letras. */
    private static final String PALABRA = "[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+(?:['-][A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)*";

    /** Nombre y apellido: de 2 a 6 palabras separadas por un solo espacio. */
    public static final String NOMBRE = "^" + PALABRA + "(?: " + PALABRA + "){1,5}$";

    /** Celular colombiano: 10 dígitos que empiezan por 3. */
    public static final String CELULAR = "^3[0-9]{9}$";

    /**
     * Normaliza antes de validar: Jackson construye el record y luego corre
     * {@code @Valid}, así que lo que se valida (y se guarda) es ya la forma
     * limpia. Solo se arreglan diferencias invisibles para la persona —
     * espacios de sobra y tildes compuestas — nunca se "corrige" contenido.
     */
    public ReservaRequest {
        if (clienteNombre != null) {
            clienteNombre = Normalizer.normalize(clienteNombre, Normalizer.Form.NFC)
                    .strip()
                    .replaceAll("\\s+", " ");
        }
        if (clienteCelular != null) {
            clienteCelular = clienteCelular.strip();
        }
    }
}
