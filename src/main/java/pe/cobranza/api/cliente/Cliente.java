package pe.cobranza.api.cliente;

import jakarta.persistence.*;

@Entity
@Table(name = "cliente")
public class Cliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 15)
    private String documento;
    private String nombres;
    private String apellidos;
    private String telefono;
    /** Dato de control para validar identidad por teléfono. */
    private Integer anioNacimiento;

    public Long getId() { return id; }
    public String getDocumento() { return documento; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public String getTelefono() { return telefono; }
    public Integer getAnioNacimiento() { return anioNacimiento; }
}
