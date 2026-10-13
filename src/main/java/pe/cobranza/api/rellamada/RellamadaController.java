package pe.cobranza.api.rellamada;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rellamadas")
public class RellamadaController {

    private final RellamadaRepository repo;

    public RellamadaController(RellamadaRepository repo) {
        this.repo = repo;
    }

    public record RellamadaRequest(@NotBlank String documento, @NotNull @Future LocalDateTime fechaHora,
                                   String motivo, String callId) {}

    /** El discador propio leerá estas rellamadas para reprogramar la campaña. */
    @PostMapping
    public Map<String, Object> agendar(@Valid @RequestBody RellamadaRequest req) {
        var r = repo.save(new Rellamada(req.documento(), req.fechaHora(), req.motivo(), req.callId()));
        return Map.of("rellamadaId", r.getId(), "fechaHora", req.fechaHora().toString(), "mensaje", "Rellamada agendada.");
    }
}
