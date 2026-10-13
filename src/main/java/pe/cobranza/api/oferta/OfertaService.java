package pe.cobranza.api.oferta;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.cobranza.api.config.ErrorHandler.NegocioException;
import pe.cobranza.api.config.PoliticaDescuentos;
import pe.cobranza.api.deuda.Deuda;
import pe.cobranza.api.deuda.DeudaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Todo cálculo de montos ocurre aquí, nunca en el LLM. */
@Service
public class OfertaService {

    private final DeudaRepository deudas;
    private final PoliticaDescuentos politica;

    public OfertaService(DeudaRepository deudas, PoliticaDescuentos politica) {
        this.deudas = deudas;
        this.politica = politica;
    }

    public record Oferta(Long deudaId, BigDecimal saldoTotal, int cuotas, int porcentajeDescuento,
                         BigDecimal montoConDescuento, BigDecimal montoCuota, LocalDate vigenteHasta) {}

    public Deuda deudaDelCliente(String documento, Long deudaId) {
        var deuda = deudas.findById(deudaId)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Deuda no encontrada"));
        if (!deuda.getCliente().getDocumento().equals(documento)) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "La deuda no pertenece al cliente");
        }
        if (!"VIGENTE".equals(deuda.getEstado())) {
            throw new NegocioException(HttpStatus.CONFLICT, "La deuda ya tiene estado " + deuda.getEstado());
        }
        return deuda;
    }

    public Oferta calcular(String documento, Long deudaId, int cuotas) {
        Integer reduccion = politica.reduccionPorCuotas().get(cuotas);
        if (reduccion == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Número de cuotas no permitido. Opciones: " + politica.reduccionPorCuotas().keySet());
        }
        Deuda deuda = deudaDelCliente(documento, deudaId);
        int descuento = Math.max(0, politica.descuentoMaximo(deuda.getDiasMora()) - reduccion);

        BigDecimal total = deuda.getSaldoTotal()
                .multiply(BigDecimal.valueOf(100 - descuento))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal cuota = total.divide(BigDecimal.valueOf(cuotas), 2, RoundingMode.HALF_UP);

        if (cuotas > 1 && cuota.compareTo(politica.cuotaMinima()) < 0) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "La cuota resultante es menor al mínimo de S/ " + politica.cuotaMinima() + "; ofrezca menos cuotas.");
        }
        return new Oferta(deuda.getId(), deuda.getSaldoTotal(), cuotas, descuento, total, cuota,
                LocalDate.now().plusDays(politica.vigenciaOfertaDias()));
    }

    public PoliticaDescuentos politica() {
        return politica;
    }
}
