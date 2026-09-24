package com.agendad.analitica.web;

import com.agendad.analitica.comun.error.PeticionInvalida;
import com.agendad.analitica.repositorio.AgregadoMetricas;
import com.agendad.analitica.repositorio.MetricaDiariaRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricasControllerTest {

    private final MetricaDiariaRepository repositorio = mock(MetricaDiariaRepository.class);
    private final MetricasController controller = new MetricasController(repositorio, 600);

    @Test
    void calculaOcupacionYTasaDeInasistenciaSobreDatosReales() {
        UUID negocioId = UUID.randomUUID();
        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2026, 9, 30);

        // 2 profesionales-día con actividad -> 1200 min disponibles (600 c/u)
        // 600 min ocupados -> 50% de ocupación
        // 8 citas resueltas (6 atendidas + 2 no_asistio) -> 25% de inasistencia
        when(repositorio.agregarPeriodo(negocioId, desde, hasta))
                .thenReturn(new AgregadoMetricas(10, 2, 6, 2, 600, 2));

        var resultado = controller.metricas(negocioId, desde, hasta);

        assertThat(resultado.totalCitas()).isEqualTo(10);
        assertThat(resultado.canceladas()).isEqualTo(2);
        assertThat(resultado.atendidas()).isEqualTo(6);
        assertThat(resultado.noAsistio()).isEqualTo(2);
        assertThat(resultado.ocupacion()).isEqualTo(50.0);
        assertThat(resultado.tasaInasistencia()).isEqualTo(25.0);
    }

    @Test
    void sinActividadNoDivideEntreCeroYDevuelveCiframasEnCero() {
        UUID negocioId = UUID.randomUUID();
        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2026, 9, 30);
        when(repositorio.agregarPeriodo(negocioId, desde, hasta)).thenReturn(AgregadoMetricas.vacio());

        var resultado = controller.metricas(negocioId, desde, hasta);

        assertThat(resultado.ocupacion()).isZero();
        assertThat(resultado.tasaInasistencia()).isZero();
    }

    @Test
    void fechaDesdePosteriorAHastaEsUnaPeticionInvalida() {
        UUID negocioId = UUID.randomUUID();
        LocalDate desde = LocalDate.of(2026, 9, 30);
        LocalDate hasta = LocalDate.of(2026, 9, 1);

        assertThatThrownBy(() -> controller.metricas(negocioId, desde, hasta))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("desde");
    }
}
