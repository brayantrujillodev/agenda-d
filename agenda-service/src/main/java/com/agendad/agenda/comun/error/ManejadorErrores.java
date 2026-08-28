package com.agendad.agenda.comun.error;

import java.time.DateTimeException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce las excepciones a la respuesta del contrato. Nunca se filtra un
 * stacktrace ni un mensaje técnico: todo en español y accionable.
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(RecursoNoEncontrado.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public RespuestaError noEncontrado(RecursoNoEncontrado e) {
        return new RespuestaError("NO_ENCONTRADO", e.getMessage());
    }

    @ExceptionHandler(PeticionInvalida.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespuestaError peticionInvalida(PeticionInvalida e) {
        return new RespuestaError("DATOS_INVALIDOS", e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespuestaError parametros(Exception e) {
        return new RespuestaError("DATOS_INVALIDOS",
                "Revisa los parámetros de la consulta: falta alguno o tiene un formato inválido.");
    }

    @ExceptionHandler(DateTimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespuestaError zonaHoraria(DateTimeException e) {
        return new RespuestaError("DATOS_INVALIDOS", "La configuración de zona horaria del negocio no es válida.");
    }
}
