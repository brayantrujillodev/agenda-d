package com.agendad.agenda.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agendad.agenda.comun.error.RecursoNoEncontrado;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * docs/TAREAS.md #21: "Prueba negativa que intenta leer una cita de otro
 * negocio por su UUID directo y debe obtener respuesta vacía."
 *
 * <p>Contra Postgres real (Testcontainers): el negocio de la semilla de
 * Flyway (barbería-el-corte) más un SEGUNDO negocio insertado a mano en
 * esta prueba. El aislamiento no es opcional ni de "buena fe": lo
 * comprueban {@code findByIdAndNegocioId} en los repositorios, y esta
 * prueba demuestra que de verdad se aplica, no solo que compila.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeparacionEntreNegociosTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agendad")
            .withUsername("agendad")
            .withPassword("agendad");

    // Semilla de db/V1__esquema_inicial.sql: barbería-el-corte y su profesional Laura.
    private static final UUID NEGOCIO_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROFESIONAL_LAURA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SERVICIO_CORTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private UUID negocioB;
    private UUID citaDeLaura;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AgendaService agendaService;

    /**
     * Un segundo negocio no tiene endpoint de alta (no existe en el
     * alcance del proyecto): se inserta directo por SQL, igual que hace
     * la semilla de Flyway con el primero.
     */
    @BeforeEach
    void insertaUnSegundoNegocioYUnaCitaDeLaura() {
        negocioB = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO agenda.negocio (id, nombre, slug_publico, zona_horaria)
                VALUES (?, 'Veterinaria Los Andes', ?, 'America/Bogota')
                """, negocioB, "veterinaria-" + negocioB);

        citaDeLaura = UUID.randomUUID();
        var inicio = java.time.Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        jdbc.update("""
                INSERT INTO agenda.cita
                    (id, negocio_id, servicio_id, profesional_id, inicio, fin, cliente_nombre, cliente_celular)
                VALUES (?, ?, ?, ?, ?, ?, 'Cliente de negocio A', '3001112233')
                """, citaDeLaura, NEGOCIO_A, SERVICIO_CORTE, PROFESIONAL_LAURA,
                java.sql.Timestamp.from(inicio), java.sql.Timestamp.from(inicio.plusSeconds(3600)));
    }

    @Test
    void laAgendaDeUnProfesionalDeOtroNegocioNoSeVeAunqueElIdSeaCorrecto() {
        // Laura es de NEGOCIO_A. Pedirla con el X-Negocio-Id de negocioB
        // (real, existente, solo que es el negocio equivocado) no debe
        // devolver su agenda: debe comportarse como si no existiera.
        assertThatThrownBy(() -> agendaService.agendaDelDia(negocioB, PROFESIONAL_LAURA, LocalDate.now()))
                .isInstanceOf(RecursoNoEncontrado.class);
    }

    @Test
    void registrarEstadoSobreUnaCitaDeOtroNegocioNoEncuentraNada() {
        // citaDeLaura es de NEGOCIO_A. Intentar registrar su estado con el
        // negocioId de negocioB (UUID real, cita real, negocio equivocado)
        // debe fallar como "no encontrado", nunca modificar la cita ajena.
        assertThatThrownBy(() -> agendaService.registrarEstado(negocioB, citaDeLaura, "ATENDIDA", null))
                .isInstanceOf(RecursoNoEncontrado.class);
    }

    @Test
    void laMismaConsultaConElNegocioCorrectoSiFunciona() {
        // Control: no es que agendaDelDia esté rota para todo el mundo.
        // Con el negocio correcto, si encuentra al profesional (la lista
        // de citas puede estar vacía si ninguna cae justo hoy).
        assertThat(agendaService.agendaDelDia(NEGOCIO_A, PROFESIONAL_LAURA, LocalDate.now())).isNotNull();
    }
}
