package pe.cobranza.api.llamada;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Herramienta ejecutada por el agente durante la llamada, con sus argumentos y su respuesta en JSON. */
@Entity
@Table(name = "evento_llamada", indexes = @Index(name = "ix_evento_llamada_call_id", columnList = "call_id"))
public class EventoLlamada {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64)
    private String callId;
    @Column(nullable = false, length = 100)
    private String herramienta;
    /** JSON serializado tal como lo envió el agente. */
    @Lob
    private String argumentos;
    /** JSON serializado tal como lo envió el agente. */
    @Lob
    private String respuesta;
    /** Momento de la ejecución (columna propia para no usar la palabra reservada TIMESTAMP). */
    @Column(name = "ocurrido_en", nullable = false)
    private LocalDateTime ocurridoEn;

    protected EventoLlamada() {}

    public EventoLlamada(String callId, String herramienta, String argumentos, String respuesta, LocalDateTime ocurridoEn) {
        this.callId = callId;
        this.herramienta = herramienta;
        this.argumentos = argumentos;
        this.respuesta = respuesta;
        this.ocurridoEn = ocurridoEn;
    }

    public Long getId() { return id; }
    public String getCallId() { return callId; }
    public String getHerramienta() { return herramienta; }
    public String getArgumentos() { return argumentos; }
    public String getRespuesta() { return respuesta; }
    public LocalDateTime getOcurridoEn() { return ocurridoEn; }
}
