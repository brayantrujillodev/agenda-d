package co.edu.fet.agendad.notificaciones.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapea {@code notificaciones.programacion}, creada en
 * db/V1__esquema_inicial.sql. El aviso nace en estado PENDIENTE (sin
 * canal todavía); el {@link co.edu.fet.agendad.notificaciones.canal.CanalNotificacion}
 * elegido es quien decide cómo se "envía" y deja el registro final.
 */
@Entity
@Table(name = "programacion", schema = "notificaciones")
public class Programacion {

    @Id
    private UUID id;

    @Column(name = "negocio_id", nullable = false)
    private UUID negocioId;

    @Column(name = "cita_id", nullable = false)
    private UUID citaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoAviso tipo;

    @Column(name = "enviar_en", nullable = false)
    private Instant enviarEn;

    @Column(name = "canal", nullable = false)
    private String canal;

    @Column(name = "destinatario", nullable = false)
    private String destinatario;

    @Column(name = "cuerpo", nullable = false, columnDefinition = "text")
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoAviso estado;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    protected Programacion() {
        // Requerido por JPA
    }

    /**
     * Crea un aviso pendiente. El canal (destinatario del envío) lo fija
     * después la implementación de {@code CanalNotificacion} al procesarlo.
     */
    public Programacion(UUID id, UUID negocioId, UUID citaId, TipoAviso tipo, Instant enviarEn,
                         String destinatario, String cuerpo) {
        this.id = id;
        this.negocioId = negocioId;
        this.citaId = citaId;
        this.tipo = tipo;
        this.enviarEn = enviarEn;
        this.destinatario = destinatario;
        this.cuerpo = cuerpo;
        this.estado = EstadoAviso.PENDIENTE;
        this.intentos = 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getNegocioId() {
        return negocioId;
    }

    public UUID getCitaId() {
        return citaId;
    }

    public TipoAviso getTipo() {
        return tipo;
    }

    public Instant getEnviarEn() {
        return enviarEn;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public EstadoAviso getEstado() {
        return estado;
    }

    public void setEstado(EstadoAviso estado) {
        this.estado = estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public void setEnviadoEn(Instant enviadoEn) {
        this.enviadoEn = enviadoEn;
    }
}
