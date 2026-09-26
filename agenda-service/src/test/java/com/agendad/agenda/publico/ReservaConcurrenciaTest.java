package com.agendad.agenda.publico;

import static org.assertj.core.api.Assertions.assertThat;

import com.agendad.agenda.comun.error.CupoOcupado;
import com.agendad.agenda.publico.dto.ReservaRequest;
import com.agendad.agenda.repositorio.CitaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CLAUDE.md: "La prueba de concurrencia (100 hilos sobre el mismo cupo, 1
 * éxito y 99 conflictos) es obligatoria y no se borra."
 *
 * <p>Contra Postgres real (Testcontainers, no H2 ni mocks): 100 hilos
 * intentan reservar exactamente el mismo profesional + instante al mismo
 * tiempo, cada uno con su propia {@code Idempotency-Key} (para que la rama
 * de idempotencia no enmascare el resultado real). No hay ningún
 * {@code synchronized} ni bloqueo en el código de producción — el árbitro
 * es la restricción {@code cita_sin_solape} (EXCLUDE + btree_gist) que
 * aplica {@code db/V1__esquema_inicial.sql} vía Flyway al arrancar el
 * contenedor.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReservaConcurrenciaTest {

    private static final int HILOS = 100;

    private static final String SLUG = "barberia-el-corte";
    private static final UUID SERVICIO_CORTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROFESIONAL_LAURA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agendad")
            .withUsername("agendad")
            .withPassword("agendad");

    @DynamicPropertySource
    static void propiedadesDeAislamiento(DynamicPropertyRegistry registry) {
        // Sin broker real en este contenedor: que el relay de outbox falle
        // rápido y en silencio (ya lo maneja así en producción) en vez de
        // colgarse intentando conectar, y que casi no se dispare durante
        // los pocos segundos que dura la prueba.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:1");
        registry.add("agendad.outbox.intervalo-ms", () -> "600000");
    }

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private CitaRepository citaRepository;

    @Test
    void cienHilosReservandoElMismoCupoSoloUnoGanaYLosDemasChocanConElExclude() throws InterruptedException {
        Instant inicio = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        CountDownLatch listos = new CountDownLatch(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();

        try {
            List<Future<?>> resultados = new ArrayList<>(HILOS);
            for (int i = 0; i < HILOS; i++) {
                String idempotencyKey = "concurrencia-" + i + "-" + UUID.randomUUID();
                ReservaRequest peticion = new ReservaRequest(
                        SERVICIO_CORTE, PROFESIONAL_LAURA, inicio, "Cliente concurrente", "3000000000");

                resultados.add(pool.submit(() -> {
                    listos.countDown();
                    salida.await(); // todos los hilos disparan el INSERT en el mismo instante
                    try {
                        reservaService.reservar(SLUG, idempotencyKey, null, peticion);
                        exitos.incrementAndGet();
                    } catch (CupoOcupado e) {
                        conflictos.incrementAndGet();
                    }
                    return null;
                }));
            }

            listos.await(10, TimeUnit.SECONDS); // los 100 hilos ya están bloqueados en salida.await()
            salida.countDown(); // ahora sí: los 100 intentan el INSERT a la vez

            for (Future<?> f : resultados) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new AssertionError("Un hilo terminó con una excepción no esperada", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(exitos.get()).as("citas realmente creadas").isEqualTo(1);
        assertThat(conflictos.get()).as("intentos rechazados por cita_sin_solape").isEqualTo(HILOS - 1);
        assertThat(citaRepository.count()).as("filas en agenda.cita: la BD, no el contador, es la prueba final")
                .isEqualTo(1);
    }
}
