package pe.cobranza.api.promesa;

import jakarta.persistence.*;
import pe.cobranza.api.deuda.Deuda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "promesa_pago")
public class PromesaPago {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "deuda_id")
    private Deuda deuda;
    private int cuotas;
    private int porcentajeDescuento;
    @Column(precision = 12, scale = 2)
    private BigDecimal montoTotal;
    @Column(precision = 12, scale = 2)
    private BigDecimal montoCuota;
    private LocalDate fechaPrimerPago;
    private String callId;
    private LocalDateTime creadaEn = LocalDateTime.now();

    protected PromesaPago() {}

    public PromesaPago(Deuda deuda, int cuotas, int porcentajeDescuento, BigDecimal montoTotal,
                       BigDecimal montoCuota, LocalDate fechaPrimerPago, String callId) {
        this.deuda = deuda;
        this.cuotas = cuotas;
        this.porcentajeDescuento = porcentajeDescuento;
        this.montoTotal = montoTotal;
        this.montoCuota = montoCuota;
        this.fechaPrimerPago = fechaPrimerPago;
        this.callId = callId;
    }

    public Long getId() { return id; }
}
