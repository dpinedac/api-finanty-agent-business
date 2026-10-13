package pe.cobranza.api.llamada;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/llamadas")
public class LlamadaController {

    private final ResultadoRepository repo;

    public LlamadaController(ResultadoRepository repo) {
        this.repo = repo;
    }

    public record ResultadoRequest(String documento, @NotNull ResultadoLlamada.Tipificacion tipificacion, String resumen) {}

    /** Tipificación final; luego se devuelve al discador propio. */
    @PostMapping("/{callId}/resultado")
    public Map<String, Object> registrar(@PathVariable String callId, @Valid @RequestBody ResultadoRequest req) {
        var r = repo.save(new ResultadoLlamada(callId, req.documento(), req.tipificacion(), req.resumen()));
        return Map.of("resultadoId", r.getId(), "mensaje", "Resultado registrado.");
    }
}
