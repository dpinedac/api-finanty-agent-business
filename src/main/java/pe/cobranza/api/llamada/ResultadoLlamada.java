package pe.cobranza.api.llamada;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resultado_llamada")
public class ResultadoLlamada {
    /**
     * Catálogo de tipificaciones del agente. Las ocho primeras son de la fase 1;
     * CONTACTO_VALIDADO, NO_EXISTE, INFORMADO y PROMESA_SIMPLE se agregaron para el validador y el seguimiento.
     * El mapeo a las tipificaciones del discador vive en back-discador.
     */
    public enum Tipificacion {
        PROMESA_PAGO, RELLAMADA, RECHAZO, TERCERO, NUMERO_EQUIVOCADO, TRANSFERIDO, SIN_VALIDAR, OTRO,
        CONTACTO_VALIDADO, NO_EXISTE, INFORMADO, PROMESA_SIMPLE
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String callId;
    private String documento;
    @Enumerated(EnumType.STRING)
    private Tipificacion tipificacion;
    @Column(length = 2000)
    private String resumen;
    /** Hash del guion de campaña que interpretó el agente (aditivo). */
    @Column(length = 64)
    private String guionHash;
    /** Transcripción completa de la conversación (aditivo). */
    @Lob
    private String transcripcion;
    /** Resumen estructurado en JSON, definido por cada perfil del agente (aditivo). */
    @Lob
    private String resumenEstructurado;
    private LocalDateTime creadoEn = LocalDateTime.now();

    protected ResultadoLlamada() {}

    public ResultadoLlamada(String callId, String documento, Tipificacion tipificacion, String resumen) {
        this.callId = callId;
        this.documento = documento;
        this.tipificacion = tipificacion;
        this.resumen = resumen;
    }

    public ResultadoLlamada(String callId, String documento, Tipificacion tipificacion, String resumen,
                            String guionHash, String transcripcion, String resumenEstructurado) {
        this(callId, documento, tipificacion, resumen);
        this.guionHash = guionHash;
        this.transcripcion = transcripcion;
        this.resumenEstructurado = resumenEstructurado;
    }

    public Long getId() { return id; }
    public String getCallId() { return callId; }
    public String getDocumento() { return documento; }
    public Tipificacion getTipificacion() { return tipificacion; }
    public String getResumen() { return resumen; }
    public String getGuionHash() { return guionHash; }
    public String getTranscripcion() { return transcripcion; }
    public String getResumenEstructurado() { return resumenEstructurado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
