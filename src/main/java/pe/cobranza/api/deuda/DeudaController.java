package pe.cobranza.api.deuda;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/deudas")
public class DeudaController {

    private final DeudaRepository repo;

    public DeudaController(DeudaRepository repo) {
        this.repo = repo;
    }

    public record DeudaDto(Long deudaId, String producto, String entidadOrigen,
                           BigDecimal saldoTotal, int diasMora, String estado) {}

    @GetMapping("/{documento}")
    @Transactional(readOnly = true)
    public List<DeudaDto> porDocumento(@PathVariable String documento) {
        return repo.findByClienteDocumento(documento).stream()
                .map(d -> new DeudaDto(d.getId(), d.getProducto(), d.getEntidadOrigen(),
                        d.getSaldoTotal(), d.getDiasMora(), d.getEstado()))
                .toList();
    }
}
