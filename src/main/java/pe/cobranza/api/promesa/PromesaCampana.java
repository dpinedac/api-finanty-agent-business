package pe.cobranza.api.promesa;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Promesa de pago sobre la campaña de CobraTwo de una deuda. La deuda y la campaña viven en CNTSEC: aquí solo
 * quedan sus identificadores y los montos que el servidor calculó al registrar la promesa.
 */
@Entity
@Table(name = "promesa_campana")
public class PromesaCampana {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 15)
    private String documento;
    /** TM15NDEUID de CobraTwo. */
    @Column(nullable = false)
    private long deudaId;
    private Long operacion;
    /** TM16NCPNID de CobraTwo. */
    @Column(nullable = false)
    private int campanaId;
    @Column(nullable = false, length = 10)
    private String moneda;
    @Column(nullable = false)
    private int cuotas;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montoCuota;
    @Column(nullable = false)
    private LocalDate fechaPrimerPago;
    private String callId;
    private LocalDateTime creadaEn = LocalDateTime.now();

    protected PromesaCampana() {}

    public PromesaCampana(String documento, long deudaId, Long operacion, int campanaId, String moneda, int cuotas,
                          BigDecimal montoTotal, BigDecimal montoCuota, LocalDate fechaPrimerPago, String callId) {
        this.documento = documento;
        this.deudaId = deudaId;
        this.operacion = operacion;
        this.campanaId = campanaId;
        this.moneda = moneda;
        this.cuotas = cuotas;
        this.montoTotal = montoTotal;
        this.montoCuota = montoCuota;
        this.fechaPrimerPago = fechaPrimerPago;
        this.callId = callId;
    }

    public Long getId() { return id; }
}
