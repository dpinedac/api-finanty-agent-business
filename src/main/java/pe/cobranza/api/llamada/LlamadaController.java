package pe.cobranza.api.llamada;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.cobranza.api.config.ErrorHandler.NegocioException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/llamadas")
public class LlamadaController {

    private final ResultadoRepository resultados;
    private final LlamadaRepository llamadas;
    private final EventoLlamadaRepository eventos;
    private final ObjectMapper json;

    public LlamadaController(ResultadoRepository resultados, LlamadaRepository llamadas,
                             EventoLlamadaRepository eventos, ObjectMapper json) {
        this.resultados = resultados;
        this.llamadas = llamadas;
        this.eventos = eventos;
        this.json = json;
    }

    // ---------- Inicio de llamada ----------

    public record IniciarRequest(@NotBlank @Size(max = 64) String callId,
                                 @NotBlank @Size(max = 15) String documento,
                                 @NotBlank @Size(max = 20) String telefono,
                                 @NotNull Perfil perfil,
                                 @Size(max = 100) String campana,
                                 @Size(max = 64) String guionHash) {}

    public record IniciadaResponse(Long llamadaId, String callId, LocalDateTime inicio, String mensaje) {}

    /** El agente lo invoca al iniciar la sesión, antes del saludo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IniciadaResponse iniciar(@Valid @RequestBody IniciarRequest req) {
        if (llamadas.existsByCallId(req.callId())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe una llamada con ese callId");
        }
        var l = llamadas.save(new Llamada(req.callId(), req.documento().trim(), req.telefono().trim(),
                req.perfil(), req.campana(), req.guionHash()));
        return new IniciadaResponse(l.getId(), l.getCallId(), l.getInicio(), "Llamada registrada.");
    }

    // ---------- Eventos (herramientas ejecutadas) ----------

    public record EventoRequest(@NotBlank @Size(max = 100) String herramienta,
                                JsonNode argumentos,
                                JsonNode respuesta,
                                LocalDateTime timestamp) {}

    @PostMapping("/{callId}/eventos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registrarEvento(@PathVariable String callId, @Valid @RequestBody EventoRequest req) {
        if (!llamadas.existsByCallId(callId)) {
            throw new NegocioException(HttpStatus.NOT_FOUND, "Llamada no encontrada");
        }
        var e = eventos.save(new EventoLlamada(callId, req.herramienta(), aTexto(req.argumentos()),
                aTexto(req.respuesta()), req.timestamp() == null ? LocalDateTime.now() : req.timestamp()));
        return Map.of("eventoId", e.getId(), "mensaje", "Evento registrado.");
    }

    // ---------- Resultado (cuerpo original + campos aditivos) ----------

    public record ResultadoRequest(String documento, @NotNull ResultadoLlamada.Tipificacion tipificacion,
                                   @Size(max = 2000) String resumen,
                                   @Size(max = 64) String guionHash,
                                   String transcripcion,
                                   JsonNode resumenEstructurado) {}

    /** Tipificación final; luego se devuelve al discador propio. Cierra la llamada si fue registrada. */
    @PostMapping("/{callId}/resultado")
    @Transactional
    public Map<String, Object> registrar(@PathVariable String callId, @Valid @RequestBody ResultadoRequest req) {
        var r = resultados.save(new ResultadoLlamada(callId, req.documento(), req.tipificacion(), req.resumen(),
                req.guionHash(), req.transcripcion(), aTexto(req.resumenEstructurado())));
        llamadas.findByCallId(callId).ifPresent(l -> l.setFin(LocalDateTime.now()));
        return Map.of("resultadoId", r.getId(), "mensaje", "Resultado registrado.");
    }

    // ---------- Consulta ----------

    public record EventoDto(Long eventoId, String herramienta, JsonNode argumentos, JsonNode respuesta,
                            LocalDateTime timestamp) {}

    public record ResultadoDto(Long resultadoId, ResultadoLlamada.Tipificacion tipificacion, String resumen,
                               String guionHash, JsonNode resumenEstructurado, String transcripcion,
                               LocalDateTime registradoEn) {}

    public record LlamadaDetalle(Long llamadaId, String callId, String documento, String telefono, Perfil perfil,
                                 String campana, String guionHash, LocalDateTime inicio, LocalDateTime fin,
                                 ResultadoDto resultado, List<EventoDto> eventos) {}

    @GetMapping("/{callId}")
    @Transactional(readOnly = true)
    public LlamadaDetalle obtener(@PathVariable String callId) {
        var l = llamadas.findByCallId(callId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Llamada no encontrada"));
        var resultado = resultados.findFirstByCallIdOrderByIdDesc(callId)
                .map(r -> new ResultadoDto(r.getId(), r.getTipificacion(), r.getResumen(), r.getGuionHash(),
                        aJson(r.getResumenEstructurado()), r.getTranscripcion(), r.getCreadoEn()))
                .orElse(null);
        var lista = eventos.findByCallIdOrderByOcurridoEnAscIdAsc(callId).stream()
                .map(e -> new EventoDto(e.getId(), e.getHerramienta(), aJson(e.getArgumentos()),
                        aJson(e.getRespuesta()), e.getOcurridoEn()))
                .toList();
        return new LlamadaDetalle(l.getId(), l.getCallId(), l.getDocumento(), l.getTelefono(), l.getPerfil(),
                l.getCampana(), l.getGuionHash(), l.getInicio(), l.getFin(), resultado, lista);
    }

    private String aTexto(JsonNode nodo) {
        return nodo == null || nodo.isNull() ? null : json.writeValueAsString(nodo);
    }

    private JsonNode aJson(String texto) {
        return texto == null ? null : json.readTree(texto);
    }
}
