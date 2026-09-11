package com.agendad.agenda.comun.error;

import java.time.DateTimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.agendad.agenda.publico.dto.ConflictoCupoResponse;

/**
 * Traduce las excepciones a la respuesta del contrato. Nunca se filtra un
 * stacktrace ni un mensaje técnico: todo en español y accionable.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

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

    /** El cupo lo rechazó {@code cita_sin_solape} entre el INSERT y el commit. */
    @ExceptionHandler(CupoOcupado.class)
    public ResponseEntity<ConflictoCupoResponse> cupoOcupado(CupoOcupado e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ConflictoCupoResponse("CUPO_OCUPADO", e.getMessage(), e.getAlternativas()));
    }

    /** Falla la validación del cuerpo (@Valid): se muestra el primer mensaje, ya redactado en español. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespuestaError cuerpoInvalido(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("Revisa los datos de la reserva.");
        return new RespuestaError("DATOS_INVALIDOS", detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RespuestaError cuerpoIlegible(HttpMessageNotReadableException e) {
        return new RespuestaError("DATOS_INVALIDOS", "El cuerpo de la petición no es un JSON válido.");
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

    /** Cualquier fallo de base de datos que no sea una violación de negocio ya traducida. */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RespuestaError baseDeDatos(DataAccessException e) {
        log.error("Fallo de acceso a datos no controlado", e);
        return new RespuestaError("ERROR_INTERNO",
                "Tuvimos un problema guardando la información. Intenta de nuevo en un momento.");
    }
}
