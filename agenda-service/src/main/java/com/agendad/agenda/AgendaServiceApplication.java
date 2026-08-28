package com.agendad.agenda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de agenda-service.
 *
 * Por ahora solo arranca, conecta a PostgreSQL y deja que Flyway resuelva
 * el estado del esquema. Las entidades y los controladores llegan en las
 * ramas siguientes (feature/2-... y feature/3-...).
 */
@SpringBootApplication
public class AgendaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgendaServiceApplication.class, args);
    }
}
