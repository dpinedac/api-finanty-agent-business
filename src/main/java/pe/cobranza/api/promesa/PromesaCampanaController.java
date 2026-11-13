package pe.cobranza.api.promesa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.cobranza.api.campana.OfertaCampana.Moneda;
import pe.cobranza.api.cntsec.CampanaCntsecRepositorio;
import pe.cobranza.api.cntsec.CampanaCntsecRepositorio.TipoCampana;
import pe.cobranza.api.config.ErrorHandler.NegocioException;
import pe.cobranza.api.config.PoliticaDescuentos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Promesa de pago sobre la campaña de CobraTwo de la deuda. La deuda y la oferta salen solo de CNTSEC: el monto
 * se vuelve a calcular aquí con la campaña de la deuda y el agente no envía montos. La promesa se guarda en la
 * BD propia; en CNTSEC no se escribe nada.
 */
@RestController
@RequestMapping("/api/v1/promesas/campana")
public class PromesaCampanaController {

    private final CampanaCntsecRepositorio campanas;
    private final PromesaCampanaRepository repo;
    private final int diasMaxPrimerPago;

    public PromesaCampanaController(CampanaCntsecRepositorio campanas, PromesaCampanaRepository repo,
                                    PoliticaDescuentos politica) {
        this.campanas = campanas;
        this.repo = repo;
        this.diasMaxPrimerPago = politica.diasMaxPrimerPago();
    }

    public record PromesaCampanaRequest(@NotBlank String documento, @NotNull Long deudaId,
                                        @NotNull @Min(1) Integer cuotas, @NotNull LocalDate fechaPrimerPago,
                                        String callId, @Min(1) Integer prioridad, TipoCampana tipo) {}

    public record PromesaCampanaResponse(Long promesaId, long deudaId, Long operacion, int campanaId, Moneda moneda,
                                         int cuotas, BigDecimal montoTotal, BigDecimal montoCuota,
                                         LocalDate fechaPrimerPago, String mensaje) {}

    @PostMapping
    @Transactional
    public PromesaCampanaResponse registrar(@Valid @RequestBody PromesaCampanaRequest req) {
        var hoy = LocalDate.now();
        if (req.fechaPrimerPago().isBefore(hoy) || req.fechaPrimerPago().isAfter(hoy.plusDays(diasMaxPrimerPago))) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "La fecha del primer pago debe estar entre hoy y " + hoy.plusDays(diasMaxPrimerPago));
        }
        int prioridad = req.prioridad() == null ? 1 : req.prioridad();
        TipoCampana tipo = req.tipo() == null ? TipoCampana.COBRANZA_GENERAL : req.tipo();
        var campana = campanas.porDocumento(req.documento(), prioridad).stream()
                .filter(c -> c.deudaId() == req.deudaId() && c.tipo() == tipo)
                .findFirst()
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND,
                        "La deuda no tiene una campaña activa de ese tipo y prioridad para el cliente"));

        var oferta = campana.oferta();
        if (oferta.montoPreaprobado() == null) {
            throw new NegocioException(HttpStatus.CONFLICT, "La campaña de la deuda no tiene un monto preaprobado");
        }
        int cuotasMaximas = oferta.cuotas() == null || oferta.cuotas() < 1 ? 1 : oferta.cuotas();
        if (req.cuotas() > cuotasMaximas) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "La campaña admite como máximo " + cuotasMaximas + " cuota(s)");
        }
        BigDecimal montoCuota = oferta.montoPreaprobado()
                .divide(BigDecimal.valueOf(req.cuotas()), 2, RoundingMode.HALF_UP);

        var promesa = repo.save(new PromesaCampana(req.documento().trim(), campana.deudaId(), campana.operacion(),
                campana.campanaId(), oferta.moneda().name(), req.cuotas(), oferta.montoPreaprobado(), montoCuota,
                req.fechaPrimerPago(), req.callId()));
        return new PromesaCampanaResponse(promesa.getId(), campana.deudaId(), campana.operacion(),
                campana.campanaId(), oferta.moneda(), req.cuotas(), oferta.montoPreaprobado(), montoCuota,
                req.fechaPrimerPago(), "Promesa de pago registrada.");
    }
}
