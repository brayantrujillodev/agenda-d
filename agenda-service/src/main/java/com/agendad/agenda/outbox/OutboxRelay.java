package com.agendad.agenda.outbox;

import com.agendad.agenda.dominio.Outbox;
import com.agendad.agenda.repositorio.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relay del patrón outbox: cada pocos segundos lee lo que la reserva dejó
 * pendiente en {@code agenda.outbox}, lo publica en Kafka y lo marca como
 * enviado. Clave de partición = {@code profesionalId}, para garantizar el
 * orden por profesional (CONTRATO-EVENTOS.md).
 *
 * <p>Sin reintentos con espera ni DLQ todavía: si una publicación falla, se
 * suma un intento y se reintenta en el siguiente ciclo. Eso es Fase 3
 * (issue #16). Apagar Kafka, reservar y volver a encenderlo debe terminar
 * con todos los eventos publicados.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long TIMEOUT_ENVIO_S = 10;

    private final OutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxRepository outboxRepo, KafkaTemplate<String, String> kafka) {
        this.outboxRepo = outboxRepo;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${agendad.outbox.intervalo-ms:2000}")
    @Transactional
    public void publicarPendientes() {
        List<Outbox> pendientes = outboxRepo.findTop100ByEnviadoEnIsNullOrderByCreadoEnAsc();
        if (pendientes.isEmpty()) {
            return;
        }

        int publicados = 0;
        for (Outbox evento : pendientes) {
            try {
                kafka.send(evento.getTipoEvento(), evento.getClaveParticion(), evento.getPayload())
                        .get(TIMEOUT_ENVIO_S, TimeUnit.SECONDS);
                evento.marcarEnviado(Instant.now());
                publicados++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                evento.registrarIntentoFallido();
                log.warn("Publicación de outbox {} interrumpida; se reintenta luego", evento.getId());
                break;
            } catch (Exception e) {
                evento.registrarIntentoFallido();
                log.warn("No se pudo publicar el evento de outbox {} (intento {}): {}",
                        evento.getId(), evento.getIntentos(), e.getMessage());
                // Sin DLQ todavía (Fase 3): queda pendiente para el próximo ciclo.
            }
        }

        outboxRepo.saveAll(pendientes);
        if (publicados > 0) {
            log.info("Outbox: {} de {} eventos publicados", publicados, pendientes.size());
        }
    }
}
