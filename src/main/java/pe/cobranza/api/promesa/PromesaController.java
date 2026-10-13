package pe.cobranza.api.promesa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.cobranza.api.config.ErrorHandler.NegocioException;
import pe.cobranza.api.oferta.OfertaService;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/promesas")
public class PromesaController {

    private final OfertaService ofertas;
    private final PromesaRepository repo;

    public PromesaController(OfertaService ofertas, PromesaRepository repo) {
        this.ofertas = ofertas;
        this.repo = repo;
    }

    public record PromesaRequest(@NotBlank String documento, @NotNull Long deudaId, @NotNull @Min(1) Integer cuotas,
                                 @NotNull LocalDate fechaPrimerPago, String callId) {}

    public record PromesaResponse(Long promesaId, int cuotas, BigDecimal montoTotal, BigDecimal montoCuota,
                                  LocalDate fechaPrimerPago, String mensaje) {}

    /** El monto se recalcula en el servidor: el agente no puede inventar montos. */
    @PostMapping
    @Transactional
    public PromesaResponse registrar(@Valid @RequestBody PromesaRequest req) {
        var hoy = LocalDate.now();
        int maxDias = ofertas.politica().diasMaxPrimerPago();
        if (req.fechaPrimerPago().isBefore(hoy) || req.fechaPrimerPago().isAfter(hoy.plusDays(maxDias))) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "La fecha del primer pago debe estar entre hoy y " + hoy.plusDays(maxDias));
        }
        var oferta = ofertas.calcular(req.documento(), req.deudaId(), req.cuotas());
        var deuda = ofertas.deudaDelCliente(req.documento(), req.deudaId());
        var promesa = repo.save(new PromesaPago(deuda, oferta.cuotas(), oferta.porcentajeDescuento(),
                oferta.montoConDescuento(), oferta.montoCuota(), req.fechaPrimerPago(), req.callId()));
        deuda.setEstado("CON_PROMESA");
        return new PromesaResponse(promesa.getId(), oferta.cuotas(), oferta.montoConDescuento(),
                oferta.montoCuota(), req.fechaPrimerPago(), "Promesa de pago registrada.");
    }
}
