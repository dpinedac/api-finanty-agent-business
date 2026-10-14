package pe.cobranza.api.telefono;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Resultado de validar a quién pertenece el teléfono marcado. Lo registra el perfil validador. */
@Entity
@Table(name = "validacion_telefono", indexes = @Index(name = "ix_validacion_telefono_call_id", columnList = "call_id"))
public class ValidacionTelefono {
    /** Subconjunto del catálogo de tipificaciones propio de la validación. */
    public enum Resultado { CONTACTO_VALIDADO, TERCERO, NUMERO_EQUIVOCADO, SIN_VALIDAR, NO_EXISTE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 15)
    private String documento;
    @Column(nullable = false, length = 20)
    private String telefono;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Resultado resultado;
    @Column(nullable = false, length = 64)
    private String callId;
    private LocalDateTime creadaEn = LocalDateTime.now();

    protected ValidacionTelefono() {}

    public ValidacionTelefono(String documento, String telefono, Resultado resultado, String callId) {
        this.documento = documento;
        this.telefono = telefono;
        this.resultado = resultado;
        this.callId = callId;
    }

    public Long getId() { return id; }
    public String getDocumento() { return documento; }
    public String getTelefono() { return telefono; }
    public Resultado getResultado() { return resultado; }
    public String getCallId() { return callId; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
}
