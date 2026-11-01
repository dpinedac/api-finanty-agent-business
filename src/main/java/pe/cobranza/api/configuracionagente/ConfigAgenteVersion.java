package pe.cobranza.api.configuracionagente;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Versión publicada e inmutable de la configuración de un ámbito (global o un perfil del agente).
 * Sus archivos (prompt.md, guion.json, config.yaml, global.yaml) viven en config_agente_version_archivo.
 * La versión vigente del ámbito es la de mayor número.
 */
@Entity
@Table(name = "config_agente_version",
        uniqueConstraints = @UniqueConstraint(name = "uq_config_agente_version", columnNames = {"ambito", "numero"}))
public class ConfigAgenteVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 32)
    private String ambito;
    @Column(nullable = false)
    private int numero;
    @Column(nullable = false, length = 100)
    private String autor;
    @Column(length = 500)
    private String comentario;
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
    /** nombreArchivo -> contenido (texto UTF-8). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "config_agente_version_archivo", joinColumns = @JoinColumn(name = "version_id"))
    @MapKeyColumn(name = "nombre", length = 32, nullable = false)
    @Lob
    @Nationalized // nvarchar(max) en SQL Server, como en specs/sql/config_agente.sql
    @Column(name = "contenido", nullable = false)
    private Map<String, String> archivos = new LinkedHashMap<>();

    protected ConfigAgenteVersion() {}

    public ConfigAgenteVersion(String ambito, int numero, String autor, String comentario, Map<String, String> archivos) {
        this.ambito = ambito;
        this.numero = numero;
        this.autor = autor;
        this.comentario = comentario;
        this.archivos = new LinkedHashMap<>(archivos);
    }

    public Long getId() { return id; }
    public String getAmbito() { return ambito; }
    public int getNumero() { return numero; }
    public String getAutor() { return autor; }
    public String getComentario() { return comentario; }
    public LocalDateTime getFecha() { return fecha; }
    public Map<String, String> getArchivos() { return archivos; }
}
