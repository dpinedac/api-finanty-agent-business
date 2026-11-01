package pe.cobranza.api.configuracionagente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.cobranza.api.config.ErrorHandler.NegocioException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static pe.cobranza.api.configuracionagente.ReglasConfiguracion.validarAmbito;
import static pe.cobranza.api.configuracionagente.ReglasConfiguracion.validarArchivos;

/**
 * Depósito versionado de la configuración del agente (prompts, guion, config.yaml, global.yaml) por ámbito.
 * Lo escribe la consola de agent-voice y lo lee agent-voice al cargar un perfil. business-api sigue siendo
 * el único escritor en BD; agent-voice no toca estas tablas.
 */
@RestController
@RequestMapping("/api/v1/configuracion-agente")
public class ConfiguracionAgenteController {

    private final ConfigAgenteVersionRepository versiones;
    private final ConfigAgenteBorradorRepository borradores;

    public ConfiguracionAgenteController(ConfigAgenteVersionRepository versiones,
                                         ConfigAgenteBorradorRepository borradores) {
        this.versiones = versiones;
        this.borradores = borradores;
    }

    // ---------- Ámbitos ----------

    public record AmbitoDto(String ambito, Integer versionVigente, boolean hayBorrador, LocalDateTime publicadoEn) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<AmbitoDto> listarAmbitos() {
        var porAmbito = new TreeMap<String, AmbitoDto>();
        for (var v : versiones.vigentes()) {
            porAmbito.put(v.getAmbito(), new AmbitoDto(v.getAmbito(), v.getNumero(), false, v.getFecha()));
        }
        for (var ambito : borradores.ambitosConBorrador()) {
            var actual = porAmbito.get(ambito);
            porAmbito.put(ambito, actual == null
                    ? new AmbitoDto(ambito, null, true, null)
                    : new AmbitoDto(ambito, actual.versionVigente(), true, actual.publicadoEn()));
        }
        return List.copyOf(porAmbito.values());
    }

    // ---------- Publicado ----------

    public record VersionDto(String ambito, int numero, String autor, String comentario, LocalDateTime fecha,
                             Map<String, String> archivos) {}

    @GetMapping("/{ambito}")
    @Transactional(readOnly = true)
    public VersionDto obtenerPublicada(@PathVariable String ambito) {
        var v = versiones.findFirstByAmbitoOrderByNumeroDesc(validarAmbito(ambito))
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "El ámbito no tiene configuración publicada"));
        return aDto(v);
    }

    public record PublicarRequest(Map<String, String> archivos,
                                  @NotBlank @Size(max = 100) String autor,
                                  @Size(max = 500) String comentario) {}

    public record PublicadaResponse(String ambito, int numero, LocalDateTime fecha) {}

    /** Crea la versión n+1 con los archivos enviados y elimina el borrador del ámbito si existía. */
    @PostMapping("/{ambito}/publicar")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PublicadaResponse publicar(@PathVariable String ambito, @Valid @RequestBody PublicarRequest req) {
        validarAmbito(ambito);
        var archivos = validarArchivos(req.archivos());
        int numero = versiones.maxNumero(ambito).orElse(0) + 1;
        ConfigAgenteVersion v;
        try {
            v = versiones.saveAndFlush(new ConfigAgenteVersion(ambito, numero, req.autor().trim(), req.comentario(), archivos));
        } catch (DataIntegrityViolationException e) {
            // Red de seguridad de la restricción única (ambito, numero) ante dos publicaciones simultáneas.
            throw new NegocioException(HttpStatus.CONFLICT, "Otra publicación del ámbito ocurrió al mismo tiempo; reintenta");
        }
        borradores.findById(ambito).ifPresent(borradores::delete);
        return new PublicadaResponse(v.getAmbito(), v.getNumero(), v.getFecha());
    }

    // ---------- Borrador ----------

    public record BorradorDto(String ambito, String autor, String comentario, LocalDateTime fecha,
                              Map<String, String> archivos) {}

    @GetMapping("/{ambito}/borrador")
    @Transactional(readOnly = true)
    public BorradorDto obtenerBorrador(@PathVariable String ambito) {
        var b = borradores.findById(validarAmbito(ambito))
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "El ámbito no tiene borrador"));
        return new BorradorDto(b.getAmbito(), b.getAutor(), b.getComentario(), b.getFecha(), new TreeMap<>(b.getArchivos()));
    }

    public record GuardarBorradorRequest(Map<String, String> archivos,
                                         @NotBlank @Size(max = 100) String autor,
                                         @Size(max = 500) String comentario) {}

    public record BorradorGuardadoResponse(String ambito, LocalDateTime fecha) {}

    /** Crea el borrador o reemplaza por completo el existente. */
    @PutMapping("/{ambito}/borrador")
    @Transactional
    public BorradorGuardadoResponse guardarBorrador(@PathVariable String ambito, @Valid @RequestBody GuardarBorradorRequest req) {
        validarAmbito(ambito);
        var archivos = validarArchivos(req.archivos());
        var b = borradores.findById(ambito)
                .map(existente -> {
                    existente.reemplazar(req.autor().trim(), req.comentario(), archivos);
                    return existente;
                })
                .orElseGet(() -> borradores.save(new ConfigAgenteBorrador(ambito, req.autor().trim(), req.comentario(), archivos)));
        return new BorradorGuardadoResponse(b.getAmbito(), b.getFecha());
    }

    /** Idempotente: 204 aunque no hubiera borrador. */
    @DeleteMapping("/{ambito}/borrador")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void eliminarBorrador(@PathVariable String ambito) {
        borradores.findById(validarAmbito(ambito)).ifPresent(borradores::delete);
    }

    // ---------- Versiones ----------

    public record VersionResumenDto(int numero, String autor, String comentario, LocalDateTime fecha) {}

    @GetMapping("/{ambito}/versiones")
    @Transactional(readOnly = true)
    public List<VersionResumenDto> listarVersiones(@PathVariable String ambito) {
        return versiones.findByAmbitoOrderByNumeroDesc(validarAmbito(ambito)).stream()
                .map(v -> new VersionResumenDto(v.getNumero(), v.getAutor(), v.getComentario(), v.getFecha()))
                .toList();
    }

    @GetMapping("/{ambito}/versiones/{numero}")
    @Transactional(readOnly = true)
    public VersionDto obtenerVersion(@PathVariable String ambito, @PathVariable int numero) {
        var v = versiones.findByAmbitoAndNumero(validarAmbito(ambito), numero)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Versión no encontrada"));
        return aDto(v);
    }

    private static VersionDto aDto(ConfigAgenteVersion v) {
        return new VersionDto(v.getAmbito(), v.getNumero(), v.getAutor(), v.getComentario(), v.getFecha(),
                new TreeMap<>(v.getArchivos()));
    }
}
