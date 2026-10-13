package pe.cobranza.api.llamada;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resultado_llamada")
public class ResultadoLlamada {
    public enum Tipificacion { PROMESA_PAGO, RELLAMADA, RECHAZO, TERCERO, NUMERO_EQUIVOCADO, TRANSFERIDO, SIN_VALIDAR, OTRO }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String callId;
    private String documento;
    @Enumerated(EnumType.STRING)
    private Tipificacion tipificacion;
    @Column(length = 2000)
    private String resumen;
    private LocalDateTime creadoEn = LocalDateTime.now();

    protected ResultadoLlamada() {}

    public ResultadoLlamada(String callId, String documento, Tipificacion tipificacion, String resumen) {
        this.callId = callId;
        this.documento = documento;
        this.tipificacion = tipificacion;
        this.resumen = resumen;
    }

    public Long getId() { return id; }
}
