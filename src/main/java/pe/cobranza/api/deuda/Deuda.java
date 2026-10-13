package pe.cobranza.api.deuda;

import jakarta.persistence.*;
import pe.cobranza.api.cliente.Cliente;

import java.math.BigDecimal;

@Entity
@Table(name = "deuda")
public class Deuda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;
    private String producto;
    private String entidadOrigen;
    @Column(precision = 12, scale = 2)
    private BigDecimal saldoCapital;
    @Column(precision = 12, scale = 2)
    private BigDecimal saldoTotal;
    private int diasMora;
    /** VIGENTE, CON_PROMESA, PAGADA */
    private String estado;

    public Long getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public String getProducto() { return producto; }
    public String getEntidadOrigen() { return entidadOrigen; }
    public BigDecimal getSaldoCapital() { return saldoCapital; }
    public BigDecimal getSaldoTotal() { return saldoTotal; }
    public int getDiasMora() { return diasMora; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
