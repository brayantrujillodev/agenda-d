package com.agendad.agenda.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agendad.agenda.dominio.Outbox;
import com.agendad.agenda.repositorio.OutboxRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

@SuppressWarnings("unchecked")
class OutboxRelayTest {

    private final OutboxRepository outboxRepo = mock(OutboxRepository.class);
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(outboxRepo, kafka);

    private Outbox pendiente() {
        return new Outbox("citas.reservadas", UUID.randomUUID().toString(), UUID.randomUUID(), "{\"v\":1}");
    }

    @Test
    void publicaLosPendientesYLosMarcaComoEnviados() {
        Outbox a = pendiente();
        Outbox b = pendiente();
        when(outboxRepo.findTop100ByEnviadoEnIsNullOrderByCreadoEnAsc()).thenReturn(List.of(a, b));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.publicarPendientes();

        assertThat(a.getEnviadoEn()).isNotNull();
        assertThat(b.getEnviadoEn()).isNotNull();
        verify(kafka).send("citas.reservadas", a.getClaveParticion(), a.getPayload());
        verify(kafka).send("citas.reservadas", b.getClaveParticion(), b.getPayload());
        verify(outboxRepo).saveAll(anyList());
    }

    @Test
    void siKafkaFalla_sumaIntentoYDejaElEventoPendiente() {
        Outbox a = pendiente();
        when(outboxRepo.findTop100ByEnviadoEnIsNullOrderByCreadoEnAsc()).thenReturn(List.of(a));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka caído")));

        relay.publicarPendientes();

        assertThat(a.getEnviadoEn()).isNull();
        assertThat(a.getIntentos()).isEqualTo(1);
        verify(outboxRepo).saveAll(anyList());
    }

    @Test
    void sinPendientes_noTocaKafka() {
        when(outboxRepo.findTop100ByEnviadoEnIsNullOrderByCreadoEnAsc()).thenReturn(List.of());

        relay.publicarPendientes();

        verifyNoInteractions(kafka);
        verify(outboxRepo, never()).saveAll(anyList());
    }
}
