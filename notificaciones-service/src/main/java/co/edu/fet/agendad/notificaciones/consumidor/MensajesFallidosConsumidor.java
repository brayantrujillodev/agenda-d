package co.edu.fet.agendad.notificaciones.consumidor;

import co.edu.fet.agendad.notificaciones.dominio.MensajeFallido;
import co.edu.fet.agendad.notificaciones.dominio.MensajeFallidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consume {@code citas.dlq} y deja constancia en
 * {@code notificaciones.mensaje_fallido} — docs/TAREAS.md #16: "Endpoint
 * que lista lo caído". {@link co.edu.fet.agendad.notificaciones.web.MensajesFallidosController}
 * expone esa lista.
 */
@Component
public class MensajesFallidosConsumidor {

    private static final Logger log = LoggerFactory.getLogger(MensajesFallidosConsumidor.class);
    private static final int INTENTOS_ANTES_DE_LLEGAR_AQUI = 3;

    private final MensajeFallidoRepository mensajeFallidoRepository;

    public MensajesFallidosConsumidor(MensajeFallidoRepository mensajeFallidoRepository) {
        this.mensajeFallidoRepository = mensajeFallidoRepository;
    }

    @KafkaListener(topics = "citas.dlq", groupId = "notificaciones-service-dlq",
            containerFactory = "citasDlqListenerFactory")
    @Transactional
    public void alCaerUnMensaje(
            @Payload String payload,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String topicoOrigen,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String motivo) {

        String origen = topicoOrigen != null ? topicoOrigen : "desconocido";
        mensajeFallidoRepository.save(new MensajeFallido(
                UUID.randomUUID(), origen, payload, motivo, INTENTOS_ANTES_DE_LLEGAR_AQUI));

        log.warn("Mensaje de {} agotó los reintentos y quedó en citas.dlq: {}", origen, motivo);
    }
}
