package pe.cobranza.api.rellamada;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rellamada")
public class Rellamada {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String documento;
    private LocalDateTime fechaHora;
    private String motivo;
    private String callId;
    private LocalDateTime creadaEn = LocalDateTime.now();

    protected Rellamada() {}

    public Rellamada(String documento, LocalDateTime fechaHora, String motivo, String callId) {
        this.documento = documento;
        this.fechaHora = fechaHora;
        this.motivo = motivo;
        this.callId = callId;
    }

    public Long getId() { return id; }
}
