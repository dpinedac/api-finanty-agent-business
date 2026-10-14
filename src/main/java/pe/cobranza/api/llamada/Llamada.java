package pe.cobranza.api.llamada;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Inicio y fin de una llamada atendida por el agente IA. Se correlaciona por callId con el discador. */
@Entity
@Table(name = "llamada")
public class Llamada {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String callId;
    @Column(nullable = false, length = 15)
    private String documento;
    @Column(nullable = false, length = 20)
    private String telefono;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;
    @Column(length = 100)
    private String campana;
    @Column(length = 64)
    private String guionHash;
    @Column(nullable = false)
    private LocalDateTime inicio = LocalDateTime.now();
    /** Se fija al registrar el resultado. */
    private LocalDateTime fin;

    protected Llamada() {}

    public Llamada(String callId, String documento, String telefono, Perfil perfil, String campana, String guionHash) {
        this.callId = callId;
        this.documento = documento;
        this.telefono = telefono;
        this.perfil = perfil;
        this.campana = campana;
        this.guionHash = guionHash;
    }

    public Long getId() { return id; }
    public String getCallId() { return callId; }
    public String getDocumento() { return documento; }
    public String getTelefono() { return telefono; }
    public Perfil getPerfil() { return perfil; }
    public String getCampana() { return campana; }
    public String getGuionHash() { return guionHash; }
    public LocalDateTime getInicio() { return inicio; }
    public LocalDateTime getFin() { return fin; }
    public void setFin(LocalDateTime fin) { this.fin = fin; }
}
