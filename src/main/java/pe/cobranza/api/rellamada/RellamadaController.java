package pe.cobranza.api.rellamada;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.cobranza.api.config.ErrorHandler.NegocioException;

import java.time.LocalDateTime;
import java.util.List;
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

    public record RellamadaDto(Long rellamadaId, String documento, LocalDateTime fechaHora, String motivo,
                               String callId, Rellamada.Estado estado, LocalDateTime creadaEn) {}

    /** Lectura para back-discador: pendientes y/o dentro de un rango de fechaHora (inclusivo). */
    @GetMapping
    public List<RellamadaDto> listar(
            @RequestParam(required = false, defaultValue = "false") boolean pendientes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "hasta debe ser posterior o igual a desde");
        }
        Specification<Rellamada> filtro = (root, q, cb) -> cb.conjunction();
        if (pendientes) {
            filtro = filtro.and((root, q, cb) -> cb.equal(root.get("estado"), Rellamada.Estado.PENDIENTE));
        }
        if (desde != null) {
            filtro = filtro.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("fechaHora"), desde));
        }
        if (hasta != null) {
            filtro = filtro.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("fechaHora"), hasta));
        }
        return repo.findAll(filtro, Sort.by("fechaHora", "id")).stream()
                .map(r -> new RellamadaDto(r.getId(), r.getDocumento(), r.getFechaHora(), r.getMotivo(),
                        r.getCallId(), r.getEstado(), r.getCreadaEn()))
                .toList();
    }
}
