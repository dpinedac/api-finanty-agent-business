package pe.cobranza.api.rellamada;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rellamada")
public class Rellamada {
    /** PENDIENTE hasta que el discador la tome; PROGRAMADA se reserva para un endpoint aditivo futuro. */
    public enum Estado { PENDIENTE, PROGRAMADA }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String documento;
    private LocalDateTime fechaHora;
    private String motivo;
    private String callId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.PENDIENTE;
    private LocalDateTime creadaEn = LocalDateTime.now();

    protected Rellamada() {}

    public Rellamada(String documento, LocalDateTime fechaHora, String motivo, String callId) {
        this.documento = documento;
        this.fechaHora = fechaHora;
        this.motivo = motivo;
        this.callId = callId;
    }

    public Long getId() { return id; }
    public String getDocumento() { return documento; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public String getMotivo() { return motivo; }
    public String getCallId() { return callId; }
    public Estado getEstado() { return estado; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
}
