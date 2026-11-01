package pe.cobranza.api.configuracionagente;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trabajo en curso de la consola para un ámbito: a lo sumo un borrador por ámbito (la clave es el ámbito).
 * Al publicar se elimina. Sus archivos viven en config_agente_borrador_archivo.
 */
@Entity
@Table(name = "config_agente_borrador")
public class ConfigAgenteBorrador {
    @Id
    @Column(length = 32)
    private String ambito;
    @Column(nullable = false, length = 100)
    private String autor;
    @Column(length = 500)
    private String comentario;
    /** Último guardado. */
    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();
    /** nombreArchivo -> contenido (texto UTF-8). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "config_agente_borrador_archivo", joinColumns = @JoinColumn(name = "ambito"))
    @MapKeyColumn(name = "nombre", length = 32, nullable = false)
    @Lob
    @Nationalized // nvarchar(max) en SQL Server, como en specs/sql/config_agente.sql
    @Column(name = "contenido", nullable = false)
    private Map<String, String> archivos = new LinkedHashMap<>();

    protected ConfigAgenteBorrador() {}

    public ConfigAgenteBorrador(String ambito, String autor, String comentario, Map<String, String> archivos) {
        this.ambito = ambito;
        reemplazar(autor, comentario, archivos);
    }

    /** Reemplaza por completo autor, comentario y archivos, y actualiza la fecha de guardado. */
    public void reemplazar(String autor, String comentario, Map<String, String> archivos) {
        this.autor = autor;
        this.comentario = comentario;
        this.fecha = LocalDateTime.now();
        this.archivos.clear();
        this.archivos.putAll(archivos);
    }

    public String getAmbito() { return ambito; }
    public String getAutor() { return autor; }
    public String getComentario() { return comentario; }
    public LocalDateTime getFecha() { return fecha; }
    public Map<String, String> getArchivos() { return archivos; }
}
