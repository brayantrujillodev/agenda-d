package com.agendad.analitica.comun.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones a la respuesta del contrato. Nunca se filtra un
 * stacktrace ni un mensaje técnico: todo en español y accionable.
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(PeticionInvalida.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespuestaError peticionInvalida(PeticionInvalida e) {
        return new RespuestaError("DATOS_INVALIDOS", e.getMessage());
    }

    /** Ruta administrativa: falta la cabecera X-Negocio-Id que identifica el negocio. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespuestaError faltaCabecera(MissingRequestHeaderException e) {
        return new RespuestaError("DATOS_INVALIDOS", "Falta la cabecera " + e.getHeaderName() + ".");
    }
}
