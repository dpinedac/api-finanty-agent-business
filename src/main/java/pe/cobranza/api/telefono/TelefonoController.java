package pe.cobranza.api.telefono;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/telefonos")
public class TelefonoController {

    private final ValidacionTelefonoRepository repo;

    public TelefonoController(ValidacionTelefonoRepository repo) {
        this.repo = repo;
    }

    public record ValidacionRequest(@NotBlank @Size(max = 15) String documento,
                                    @NotBlank @Size(max = 20) String telefono,
                                    @NotNull ValidacionTelefono.Resultado resultado,
                                    @NotBlank @Size(max = 64) String callId) {}

    public record ValidacionResponse(Long validacionId, ValidacionTelefono.Resultado resultado, String mensaje) {}

    /** El validador registra a quién pertenece el número marcado; no modifica el teléfono del cliente. */
    @PostMapping("/validacion")
    @ResponseStatus(HttpStatus.CREATED)
    public ValidacionResponse registrar(@Valid @RequestBody ValidacionRequest req) {
        var v = repo.save(new ValidacionTelefono(req.documento().trim(), req.telefono().trim(),
                req.resultado(), req.callId()));
        return new ValidacionResponse(v.getId(), v.getResultado(), "Validación registrada.");
    }
}
