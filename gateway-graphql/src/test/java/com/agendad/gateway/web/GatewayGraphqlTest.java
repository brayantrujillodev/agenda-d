package com.agendad.gateway.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agendad.gateway.client.AgendaClient;
import com.agendad.gateway.client.AnaliticaClient;
import com.agendad.gateway.client.dto.BloqueoDto;
import com.agendad.gateway.client.dto.CitaDto;
import com.agendad.gateway.client.dto.HorarioDto;
import com.agendad.gateway.client.dto.MetricasDto;
import com.agendad.gateway.client.dto.MetricasDto.ConteoServicioDto;
import com.agendad.gateway.client.dto.NegocioConfigDto;
import com.agendad.gateway.client.dto.ProfesionalDto;
import com.agendad.gateway.client.dto.ServicioDto;
import com.agendad.gateway.error.ErrorGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.test.tester.WebGraphQlTester;

/**
 * El gateway completo (esquema, resolvers, interceptor de cabeceras,
 * manejador de errores, límites de consulta; el rate limit HTTP se prueba
 * aparte en LimitePeticionesFilterTest) con agenda-service y analitica-service
 * simulados. Lo que se prueba aquí es la composición y el manejo de
 * errores, no el dominio: ese lo prueban los servicios.
 */
@SpringBootTest(properties = "agendad.limite.peticiones-por-minuto=10000")
class GatewayGraphqlTest {

    private static final UUID NEGOCIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CORTE = "22222222-2222-2222-2222-222222222222";
    private static final String BARBA = "22222222-2222-2222-2222-222222222223";
    private static final String LAURA = "33333333-3333-3333-3333-333333333333";
    private static final String ANDRES = "33333333-3333-3333-3333-333333333334";
    private static final LocalDate FECHA = LocalDate.parse("2026-11-03");

    @Autowired
    private WebGraphQlHandler handler;

    private WebGraphQlTester sinCabecera;

    @MockBean
    private AgendaClient agenda;

    @MockBean
    private AnaliticaClient analitica;

    private WebGraphQlTester graphQl;

    @BeforeEach
    void preparar() {
        sinCabecera = WebGraphQlTester.builder(handler).build();
        graphQl = sinCabecera.mutate().header("X-Negocio-Id", NEGOCIO.toString()).build();

        when(agenda.negocio(eq(NEGOCIO), anyString())).thenReturn(new NegocioConfigDto(
                NEGOCIO.toString(), "Barbería El Corte", "barberia-el-corte", "America/Bogota",
                List.of(new ServicioDto(BARBA, "Barba", 30, new BigDecimal("15000.00"), true),
                        new ServicioDto(CORTE, "Corte de cabello", 60, new BigDecimal("25000.00"), true)),
                List.of(new ProfesionalDto(ANDRES, "Andrés", true, List.of(CORTE), List.of()),
                        new ProfesionalDto(LAURA, "Laura", true, List.of(CORTE, BARBA),
                                List.of(new HorarioDto(1, "08:00", "18:00"))))));
    }

    private static CitaDto cita(String id, String profesional, String nombreProfesional, String hora, Instant inicio) {
        return new CitaDto(id, inicio, inicio.plusSeconds(3600), hora, CORTE, "Corte de cabello",
                profesional, nombreProfesional, "Juan Pérez", "3001234567", "CONFIRMADA");
    }

    private static MetricasDto metricas(List<ConteoServicioDto> top) {
        return new MetricasDto(FECHA.withDayOfMonth(1), FECHA, 10, 6, 2, 2, 50.0, 25.0, top);
    }

    // --- Composición ---------------------------------------------------

    @Test
    void panelRecepcion_componeAgendaDeTodosLosProfesionalesYMetricasEnUnaConsulta() {
        when(agenda.agenda(eq(NEGOCIO), anyString(), eq(UUID.fromString(LAURA)), eq(FECHA)))
                .thenReturn(List.of(cita("c-2", LAURA, "Laura", "10:00", Instant.parse("2026-11-03T15:00:00Z"))));
        when(agenda.agenda(eq(NEGOCIO), anyString(), eq(UUID.fromString(ANDRES)), eq(FECHA)))
                .thenReturn(List.of(cita("c-1", ANDRES, "Andrés", "08:00", Instant.parse("2026-11-03T13:00:00Z"))));
        when(analitica.metricas(eq(NEGOCIO), anyString(), eq(FECHA.withDayOfMonth(1)), eq(FECHA)))
                .thenReturn(metricas(List.of(new ConteoServicioDto(CORTE, 7))));

        var respuesta = graphQl.document("""
                query($fecha: Date) {
                  panelRecepcion(fecha: $fecha) {
                    fecha
                    negocio { nombre zonaHoraria }
                    citasDelDia { id horaLocal clienteCelular servicio { nombre } profesional { nombre } }
                    metricasDelMes { totalCitas tasaInasistencia topServicios { cantidad servicio { nombre } } }
                  }
                }""").variable("fecha", "2026-11-03").execute();

        respuesta.errors().verify();
        respuesta.path("panelRecepcion.negocio.nombre").entity(String.class).isEqualTo("Barbería El Corte");
        // Ordenadas por hora aunque vengan de profesionales distintos.
        respuesta.path("panelRecepcion.citasDelDia[*].id").entityList(String.class).containsExactly("c-1", "c-2");
        // El contrato GraphQL entrega el celular enmascarado.
        respuesta.path("panelRecepcion.citasDelDia[0].clienteCelular").entity(String.class).isEqualTo("300****567");
        respuesta.path("panelRecepcion.metricasDelMes.tasaInasistencia").entity(Double.class).isEqualTo(25.0);
        // analitica solo da el id; el nombre lo pone el gateway con el catálogo de agenda.
        respuesta.path("panelRecepcion.metricasDelMes.topServicios[0].servicio.nombre")
                .entity(String.class).isEqualTo("Corte de cabello");

        // Un solo GET /v1/negocio por petición, aunque tres campos lo usen.
        verify(agenda, times(1)).negocio(eq(NEGOCIO), anyString());
    }

    @Test
    void panelRecepcion_sinFecha_usaHoyEnLaZonaDelNegocio() {
        LocalDate hoyEnBogota = LocalDate.now(java.time.ZoneId.of("America/Bogota"));
        when(agenda.agenda(eq(NEGOCIO), anyString(), any(), eq(hoyEnBogota))).thenReturn(List.of());

        graphQl.document("{ panelRecepcion { fecha citasDelDia { id } } }").execute()
                .path("panelRecepcion.fecha").entity(String.class).isEqualTo(hoyEnBogota.toString());
    }

    @Test
    void elXNegocioIdDeLaPeticionEsElQueLlegaALosServicios() {
        UUID otro = UUID.randomUUID();
        when(agenda.negocio(eq(otro), anyString())).thenThrow(
                new ErrorGateway("NO_ENCONTRADO", "No encontramos ese negocio.", ErrorType.NOT_FOUND));

        sinCabecera.mutate().header("X-Negocio-Id", otro.toString()).build()
                .document("{ negocio { nombre } }").execute()
                .errors().expect(e -> "NO_ENCONTRADO".equals(e.getExtensions().get("codigo"))).verify();

        verify(agenda).negocio(eq(otro), anyString());
        verify(agenda, never()).negocio(eq(NEGOCIO), anyString());
    }

    @Test
    void profesionalAgenda_resuelveElCampoAnidadoSoloSiSePide() {
        when(agenda.agenda(eq(NEGOCIO), anyString(), eq(UUID.fromString(LAURA)), eq(FECHA)))
                .thenReturn(List.of(cita("c-9", LAURA, "Laura", "10:00", Instant.parse("2026-11-03T15:00:00Z"))));

        graphQl.document("{ negocio { profesionales { nombre servicios { nombre } horarios { diaSemana } } } }")
                .execute()
                .path("negocio.profesionales[1].servicios[*].nombre").entityList(String.class)
                .containsExactly("Corte de cabello", "Barba");
        verify(agenda, never()).agenda(any(), any(), any(), any());

        graphQl.document("""
                { negocio { profesionales { nombre agenda(fecha: "2026-11-03") { id } } } }""")
                .execute()
                .path("negocio.profesionales[1].agenda[0].id").entity(String.class).isEqualTo("c-9");
    }

    @Test
    void bloqueos_devuelveLosVigentesDelProfesional() {
        when(agenda.bloqueos(eq(NEGOCIO), anyString(), eq(UUID.fromString(LAURA)))).thenReturn(List.of(
                new BloqueoDto("b-1", LAURA, Instant.parse("2026-12-24T13:00:00Z"),
                        Instant.parse("2026-12-26T23:00:00Z"), "Vacaciones")));

        graphQl.document("query($p: ID!) { bloqueos(profesionalId: $p) { id motivo inicio } }")
                .variable("p", LAURA).execute()
                .path("bloqueos[0].motivo").entity(String.class).isEqualTo("Vacaciones");
    }

    // --- Errores ------------------------------------------------------

    @Test
    void sinCabeceraXNegocioId_esUnErrorClaroYNoSeLlamaANingunServicio() {
        sinCabecera.document("{ negocio { nombre } }").execute()
                .errors()
                .expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST
                        && "DATOS_INVALIDOS".equals(e.getExtensions().get("codigo"))
                        && e.getMessage().contains("X-Negocio-Id"))
                .verify();
        verify(agenda, never()).negocio(any(), any());
    }

    @Test
    void siAnaliticaNoResponde_elPanelSaleIgualConMetricasEnNullYElMotivoEnErrors() {
        when(agenda.agenda(eq(NEGOCIO), anyString(), any(), eq(FECHA)))
                .thenReturn(List.of(cita("c-1", LAURA, "Laura", "08:00", Instant.parse("2026-11-03T13:00:00Z"))));
        when(analitica.metricas(any(), any(), any(), any())).thenThrow(new ErrorGateway("SERVICIO_NO_DISPONIBLE",
                "No pudimos comunicarnos con el servicio de métricas. Intenta de nuevo en unos segundos.",
                ErrorType.INTERNAL_ERROR));

        var respuesta = graphQl.document("""
                { panelRecepcion(fecha: "2026-11-03") { citasDelDia { id } metricasDelMes { totalCitas } } }""")
                .execute();

        respuesta.errors()
                .expect(e -> "SERVICIO_NO_DISPONIBLE".equals(e.getExtensions().get("codigo"))
                        && e.getPath().equals("panelRecepcion.metricasDelMes"))
                .verify();
        respuesta.path("panelRecepcion.citasDelDia[*].id").entityList(String.class).hasSize(2); // una por profesional
        respuesta.path("panelRecepcion.metricasDelMes").valueIsNull();
    }

    @Test
    void siAgendaNoResponde_noSeInventanCitas() {
        when(agenda.negocio(any(), any())).thenThrow(new ErrorGateway("SERVICIO_NO_DISPONIBLE",
                "No pudimos comunicarnos con el servicio de agenda. Intenta de nuevo en unos segundos.",
                ErrorType.INTERNAL_ERROR));

        var respuesta = graphQl.document("{ panelRecepcion { citasDelDia { clienteNombre } } }").execute();

        respuesta.errors()
                .expect(e -> "SERVICIO_NO_DISPONIBLE".equals(e.getExtensions().get("codigo")))
                .verify();
        // panelRecepcion es no nulo en el esquema: el null sube hasta data. Nada inventado.
        respuesta.path("panelRecepcion").pathDoesNotExist();
    }

    @Test
    void unErrorInesperado_noFiltraElMensajeTecnico() {
        when(agenda.negocio(any(), any())).thenThrow(new IllegalStateException("NullPointer en la línea 42 de X"));

        graphQl.document("{ negocio { nombre } }").execute()
                .errors()
                .expect(e -> e.getErrorType() == ErrorType.INTERNAL_ERROR
                        && "ERROR_INTERNO".equals(e.getExtensions().get("codigo"))
                        && !e.getMessage().contains("línea 42"))
                .verify();
    }

    @Test
    void idMalFormado_esDatosInvalidosSinLlamarAAgenda() {
        graphQl.document("{ agenda(profesionalId: \"../../negocio\", fecha: \"2026-11-03\") { id } }").execute()
                .errors()
                .expect(e -> "DATOS_INVALIDOS".equals(e.getExtensions().get("codigo")))
                .verify();
        verify(agenda, never()).agenda(any(), any(), any(), any());
    }

    @Test
    void fechaMalFormada_laRechazaElEscalarDate() {
        graphQl.document("{ agenda(profesionalId: \"" + LAURA + "\", fecha: \"03/11/2026\") { id } }").execute()
                .errors()
                .expect(e -> e.getMessage().contains("Date"))
                .verify();
    }

    @Test
    void consultaDemasiadoProfunda_seCortaAntesDeEjecutar() {
        // negocio > profesionales > (agenda > profesional) x 8 > id = 19 niveles; el límite es 15.
        String anidado = "agenda { profesional { ".repeat(8);
        graphQl.document("{ negocio { profesionales { " + anidado + "id" + " }".repeat(19))
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains("demasiado profunda"))
                .verify();
        verify(agenda, never()).negocio(any(), any());
    }

    // --- Mutaciones ---------------------------------------------------

    @Test
    void crearServicio_conDatosValidos_llamaAAgendaYDevuelveElServicio() {
        when(agenda.crearServicio(eq(NEGOCIO), anyString(), eq("Corte y barba"), eq(90), eq(new BigDecimal("35000"))))
                .thenReturn(new ServicioDto("s-nuevo", "Corte y barba", 90, new BigDecimal("35000.00"), true));

        graphQl.document("""
                mutation { crearServicio(entrada: { nombre: "Corte y barba", duracionMin: 90, precio: 35000 }) {
                  id nombre precio activo } }""")
                .execute()
                .path("crearServicio.precio").entity(String.class).isEqualTo("35000.00");
    }

    @Test
    void crearServicio_conDuracionFueraDeRango_seRechazaEnElGatewaySinLlamarAAgenda() {
        graphQl.document("""
                mutation { crearServicio(entrada: { nombre: "Corte", duracionMin: 2, precio: 10 }) { id } }""")
                .execute()
                .errors()
                .expect(e -> e.getErrorType() == ErrorType.BAD_REQUEST
                        && "DATOS_INVALIDOS".equals(e.getExtensions().get("codigo"))
                        && e.getMessage().equals("La duración mínima es de 5 minutos."))
                .verify();
        verify(agenda, never()).crearServicio(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    void definirHorario_conHoraMalFormada_seRechazaEnElGateway() {
        graphQl.document("""
                mutation($p: ID!) { definirHorario(profesionalId: $p,
                  horarios: [{ diaSemana: 1, horaInicio: "8am", horaFin: "18:00" }]) { id } }""")
                .variable("p", LAURA)
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains("HH:mm"))
                .verify();
        verify(agenda, never()).definirHorario(any(), any(), any(), any());
    }

    @Test
    void crearBloqueo_conCitasEnElRango_exponeElCodigoDeAgendaTalCual() {
        when(agenda.crearBloqueo(any(), any(), any(), any(), any(), any())).thenThrow(new ErrorGateway(
                "CITAS_EN_RANGO", "Hay 2 citas confirmadas en ese rango. Cancélalas antes de bloquearlo.",
                ErrorType.BAD_REQUEST));

        graphQl.document("""
                mutation($p: ID!) { crearBloqueo(entrada: { profesionalId: $p,
                  inicio: "2026-11-03T13:00:00Z", fin: "2026-11-03T23:00:00Z" }) { id } }""")
                .variable("p", LAURA)
                .execute()
                .errors()
                .expect(e -> "CITAS_EN_RANGO".equals(e.getExtensions().get("codigo"))
                        && e.getMessage().startsWith("Hay 2 citas"))
                .verify();
    }

    @Test
    void registrarAsistencia_soloAdmiteAtendidaONoAsistio() {
        graphQl.document("""
                mutation { registrarAsistencia(citaId: "%s", estado: CANCELADA) { id } }""".formatted(UUID.randomUUID()))
                .execute()
                .errors()
                .expect(e -> e.getMessage().equals("Solo se puede registrar ATENDIDA o NO_ASISTIO."))
                .verify();
        verify(agenda, never()).registrarAsistencia(any(), any(), any(), any());
    }

    @Test
    void registrarAsistencia_registraYDevuelveLaCitaActualizada() {
        UUID citaId = UUID.randomUUID();
        CitaDto atendida = new CitaDto(citaId.toString(), Instant.parse("2026-11-03T13:00:00Z"),
                Instant.parse("2026-11-03T14:00:00Z"), "08:00", CORTE, "Corte de cabello", LAURA, "Laura",
                "Juan Pérez", "3001234567", "NO_ASISTIO");
        when(agenda.cita(eq(NEGOCIO), anyString(), eq(citaId))).thenReturn(atendida);

        graphQl.document("""
                mutation($c: ID!) { registrarAsistencia(citaId: $c, estado: NO_ASISTIO) { estado servicio { nombre } } }""")
                .variable("c", citaId.toString())
                .execute()
                .path("registrarAsistencia.estado").entity(String.class).isEqualTo("NO_ASISTIO");
        verify(agenda).registrarAsistencia(eq(NEGOCIO), anyString(), eq(citaId), eq("NO_ASISTIO"));
    }

    @Test
    void cancelarCita_devuelveLaCitaCancelada() {
        UUID citaId = UUID.randomUUID();
        when(agenda.cancelarCita(eq(NEGOCIO), anyString(), eq(citaId), eq("Incapacidad"))).thenReturn(
                new CitaDto(citaId.toString(), Instant.parse("2026-11-03T13:00:00Z"),
                        Instant.parse("2026-11-03T14:00:00Z"), "08:00", CORTE, "Corte de cabello", LAURA, "Laura",
                        "Juan Pérez", "3001234567", "CANCELADA"));

        graphQl.document("""
                mutation($c: ID!) { cancelarCita(citaId: $c, motivo: "Incapacidad") { estado profesional { nombre } } }""")
                .variable("c", citaId.toString())
                .execute()
                .path("cancelarCita.estado").entity(String.class).isEqualTo("CANCELADA");
    }

    @Test
    void elCorrelationIdRecibidoSeReenviaALosServicios() {
        sinCabecera.mutate()
                .header("X-Negocio-Id", NEGOCIO.toString())
                .header("X-Correlation-Id", "corr-123")
                .build()
                .document("{ negocio { nombre } }").execute()
                .errors().verify();
        verify(agenda).negocio(NEGOCIO, "corr-123");
    }
}
