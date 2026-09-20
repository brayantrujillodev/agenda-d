package co.edu.fet.agendad.agenda;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class ErroresHttp {
  @ExceptionHandler(AgendaController.NoEncontrado.class)
  ResponseEntity<ErrorRespuesta> noEncontrado(AgendaController.NoEncontrado e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorRespuesta("NO_ENCONTRADO", e.getMessage()));
  }
  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
  ResponseEntity<ErrorRespuesta> invalido(Exception e) {
    return ResponseEntity.badRequest().body(new ErrorRespuesta("DATOS_INVALIDOS", "Revisa los datos enviados e inténtalo de nuevo."));
  }
  record ErrorRespuesta(String codigo, String mensaje) {}
}
