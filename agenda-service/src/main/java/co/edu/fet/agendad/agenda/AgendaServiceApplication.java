package co.edu.fet.agendad.agenda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgendaServiceApplication {
  public static void main(String[] args) { SpringApplication.run(AgendaServiceApplication.class, args); }
  @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
}
