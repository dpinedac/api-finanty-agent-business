package pe.cobranza.api.oferta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ofertas")
public class OfertaController {

    private final OfertaService service;

    public OfertaController(OfertaService service) {
        this.service = service;
    }

    public record CalcularRequest(@NotBlank String documento, @NotNull Long deudaId, @NotNull @Min(1) Integer cuotas) {}

    @PostMapping("/calcular")
    @Transactional(readOnly = true)
    public OfertaService.Oferta calcular(@Valid @RequestBody CalcularRequest req) {
        return service.calcular(req.documento(), req.deudaId(), req.cuotas());
    }
}
