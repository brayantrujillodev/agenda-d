package com.agendad.agenda.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agendad.agenda.comun.error.PeticionInvalida;
import com.agendad.agenda.comun.error.RecursoNoEncontrado;
import com.agendad.agenda.dominio.Cita;
import com.agendad.agenda.dominio.Negocio;
import com.agendad.agenda.dominio.Outbox;
import com.agendad.agenda.dominio.Profesional;
import com.agendad.agenda.dominio.Servicio;
import com.agendad.agenda.dominio.TokenGestion;
import com.agendad.agenda.gestion.dto.CitaDetalleResponse;
import com.agendad.agenda.repositorio.CitaRepository;
import com.agendad.agenda.repositorio.NegocioRepository;
import com.agendad.agenda.repositorio.OutboxRepository;
import com.agendad.agenda.repositorio.ProfesionalRepository;
import com.agendad.agenda.repositorio.ServicioRepository;
import com.agendad.agenda.repositorio.TokenGestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GestionServiceTest {

    private static final String TOKEN = "tok-abc123";
    private static final Instant INICIO = Instant.parse("2026-09-01T15:00:00Z"); // 10:00 en Bogotá
    private static final Instant FIN = INICIO.plusSeconds(3600);
    private static final UUID NEGOCIO_ID = UUID.randomUUID();
    private static final UUID SERVICIO_ID = UUID.randomUUID();
    private static final UUID PROFESIONAL_ID = UUID.randomUUID();
    private static final UUID CITA_ID = UUID.randomUUID();

    private final TokenGestionRepository tokenRepo = mock(TokenGestionRepository.class);
    private final CitaRepository citaRepo = mock(CitaRepository.class);
    private final NegocioRepository negocioRepo = mock(NegocioRepository.class);
    private final ServicioRepository servicioRepo = mock(ServicioRepository.class);
    private final ProfesionalRepository profesionalRepo = mock(ProfesionalRepository.class);
    private final OutboxRepository outboxRepo = mock(OutboxRepository.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);

    private final GestionService servicio = new GestionService(
            tokenRepo, citaRepo, negocioRepo, servicioRepo, profesionalRepo, outboxRepo, objectMapper);

    @BeforeEach
    void stubTokenVigente() {
        TokenGestion tg = mock(TokenGestion.class);
        when(tg.getExpiraEn()).thenReturn(Instant.now().plusSeconds(3600));
        when(tg.getRevocadoEn()).thenReturn(null);
        when(tg.getCitaId()).thenReturn(CITA_ID);
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(tg));
    }

    private Cita citaConfirmada() {
        return new Cita(NEGOCIO_ID, SERVICIO_ID, PROFESIONAL_ID, INICIO, FIN,
                "Juan Perez", "3001234567", "idem-1");
    }

    @Test
    void consultar_tokenValido_devuelveDetalleConCelularEnmascarado() throws Exception {
        when(citaRepo.findById(CITA_ID)).thenReturn(Optional.of(citaConfirmada()));
        Negocio negocio = mock(Negocio.class);
        when(negocio.getZonaHoraria()).thenReturn("America/Bogota");
        when(negocioRepo.findById(NEGOCIO_ID)).thenReturn(Optional.of(negocio));
        Servicio servicioBarberia = mock(Servicio.class);
        when(servicioBarberia.getNombre()).thenReturn("Corte de cabello");
        when(servicioRepo.findById(SERVICIO_ID)).thenReturn(Optional.of(servicioBarberia));
        Profesional laura = mock(Profesional.class);
        when(laura.getNombre()).thenReturn("Laura");
        when(profesionalRepo.findById(PROFESIONAL_ID)).thenReturn(Optional.of(laura));

        CitaDetalleResponse resp = servicio.consultar(TOKEN);

        assertThat(resp.clienteCelular()).isEqualTo("300****567");
        assertThat(resp.clienteNombre()).isEqualTo("Juan Perez");
        assertThat(resp.horaLocal()).isEqualTo("10:00");
        assertThat(resp.servicioNombre()).isEqualTo("Corte de cabello");
        assertThat(resp.profesionalNombre()).isEqualTo("Laura");
        assertThat(resp.estado()).isEqualTo("CONFIRMADA");
    }

    @Test
    void consultar_tokenInexistente_devuelve404() {
        when(tokenRepo.findByToken("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar("no-existe"))
                .isInstanceOf(RecursoNoEncontrado.class);
    }

    @Test
    void consultar_tokenVencido_devuelve404() {
        TokenGestion vencido = mock(TokenGestion.class);
        when(vencido.getExpiraEn()).thenReturn(Instant.now().minusSeconds(60));
        when(vencido.getRevocadoEn()).thenReturn(null);
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(vencido));

        assertThatThrownBy(() -> servicio.consultar(TOKEN))
                .isInstanceOf(RecursoNoEncontrado.class)
                .hasMessageContaining("válido");
    }

    @Test
    void cancelar_sinConfirmar_devuelve400() {
        assertThatThrownBy(() -> servicio.cancelar(TOKEN, false, null))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("confirmar");
    }

    @Test
    void cancelar_ok_marcaCanceladaYEncolaEventoEnOutbox() throws JsonProcessingException {
        Cita cita = citaConfirmada();
        when(citaRepo.findById(CITA_ID)).thenReturn(Optional.of(cita));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"tipo\":\"citas.canceladas\"}");

        servicio.cancelar(TOKEN, true, "corr-1");

        assertThat(cita.estaCancelada()).isTrue();
        verify(outboxRepo).save(argThat((Outbox o) ->
                o.getTipoEvento().equals("citas.canceladas")
                        && o.getClaveParticion().equals(PROFESIONAL_ID.toString())
                        && o.getNegocioId().equals(NEGOCIO_ID)));
    }

    @Test
    void cancelar_citaYaCancelada_noEmiteSegundoEvento() {
        Cita cita = citaConfirmada();
        cita.cancelar();
        when(citaRepo.findById(CITA_ID)).thenReturn(Optional.of(cita));

        servicio.cancelar(TOKEN, true, null);

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void cancelar_citaYaCerrada_devuelve400() {
        Cita atendida = mock(Cita.class);
        when(atendida.estaCancelada()).thenReturn(false);
        when(atendida.estaConfirmada()).thenReturn(false);
        when(citaRepo.findById(CITA_ID)).thenReturn(Optional.of(atendida));

        assertThatThrownBy(() -> servicio.cancelar(TOKEN, true, null))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("no se puede cancelar");
        verify(outboxRepo, never()).save(any());
    }
}
