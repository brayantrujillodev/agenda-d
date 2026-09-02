package com.agendad.agenda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de agenda-service.
 *
 * <p>{@code @EnableScheduling} activa el relay de outbox
 * ({@code com.agendad.agenda.outbox.OutboxRelay}), que publica en Kafka lo
 * que la reserva deja pendiente en {@code agenda.outbox}.
 */
@SpringBootApplication
@EnableScheduling
public class AgendaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgendaServiceApplication.class, args);
    }
}
