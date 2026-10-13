package pe.cobranza.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Política comercial de descuentos. Vive en application.yml para que negocio
 * la ajuste sin tocar código ni el prompt del agente.
 */
@ConfigurationProperties(prefix = "cobranza.politica")
public record PoliticaDescuentos(
        List<Tramo> tramos,
        Map<Integer, Integer> reduccionPorCuotas,
        BigDecimal cuotaMinima,
        int diasMaxPrimerPago,
        int vigenciaOfertaDias
) {
    /** Descuento máximo (%) aplicable a partir de cierto número de días de mora. */
    public record Tramo(int diasMoraDesde, int descuentoMaximo) {}

    public int descuentoMaximo(int diasMora) {
        return tramos.stream()
                .filter(t -> diasMora >= t.diasMoraDesde())
                .mapToInt(Tramo::descuentoMaximo)
                .max().orElse(0);
    }
}
