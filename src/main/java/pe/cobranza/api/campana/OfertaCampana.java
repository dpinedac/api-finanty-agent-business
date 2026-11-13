package pe.cobranza.api.campana;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Monto preaprobado de una deuda según la fórmula de su campaña. Es el mismo cálculo que hace CobraTwo en
 * {@code TM_ClienteDeuda.getTM15NDEUPREAPR()} (proyecto clientes), con las mismas definiciones:
 *
 * <pre>
 * interesTotal  = interés inicial + moratorio + compensatorio
 * deudaActual   = deuda inicial + interés legal − pagos                      (fTotalDeuda)
 * capitalActual = capital inicial − max(0, pagos − (interesTotal + legal))   (fSaldoCapital)
 * preaprobado   = fórmula(fSaldoCapital, fTotalDeuda, fTotalIntereses), a 2 decimales;
 *                 sin fórmula, la deuda actual
 * cuotaMensual  = preaprobado / cuotas de la campaña
 * </pre>
 *
 * Porcentaje de descuento: el factor de la campaña por 100. Las campañas de factor 1.00 ("sin campaña": se cobra
 * la deuda total) no tienen descuento que comunicar: el porcentaje va en null y solo vale el monto.
 *
 * Los pagos llegan ya convertidos a la moneda de la deuda. Función pura: no consulta nada.
 */
public record OfertaCampana(Moneda moneda, BigDecimal deudaActual, BigDecimal capitalActual, BigDecimal interesTotal,
                            BigDecimal montoPreaprobado, BigDecimal porcentajeDescuento, Integer cuotas,
                            BigDecimal cuotaMensual) {

    public enum Moneda { SOLES, DOLARES, OTRA }

    /** Montos de la deuda tal como están en COBTM15 (null = 0) y el total pagado en la moneda de la deuda. */
    public record Saldos(BigDecimal capitalInicial, BigDecimal deudaInicial, BigDecimal interesInicial,
                         BigDecimal interesMoratorio, BigDecimal interesCompensatorio, BigDecimal interesLegal,
                         BigDecimal pagos) {}

    /**
     * @param formula fórmula de la campaña (para un descuento de monto fijo, el monto ya en la moneda de la deuda)
     * @param factor  TM14NDESPCENT (0.45 = 45 %); null en los descuentos de monto fijo
     * @param cuotas  cuotas de la campaña; null o 0 = sin cuota mensual
     */
    public static OfertaCampana calcular(Moneda moneda, Saldos s, String formula, BigDecimal factor, Integer cuotas) {
        BigDecimal pagos = cero(s.pagos());
        BigDecimal legal = cero(s.interesLegal());
        BigDecimal interesTotal = cero(s.interesInicial()).add(cero(s.interesMoratorio())).add(cero(s.interesCompensatorio()));
        BigDecimal deudaActual = cero(s.deudaInicial()).add(legal).subtract(pagos);
        BigDecimal exceso = pagos.subtract(interesTotal.add(legal));
        BigDecimal capitalActual = exceso.signum() > 0 ? cero(s.capitalInicial()).subtract(exceso) : cero(s.capitalInicial());

        BigDecimal preaprobado;
        if (formula == null || formula.isBlank()) {
            preaprobado = deudaActual;
        } else {
            preaprobado = FormulaDescuento.evaluar(formula, Map.of(
                    "fSaldoCapital", capitalActual,
                    "fTotalDeuda", deudaActual,
                    "fTotalIntereses", interesTotal)).orElse(null);
        }
        if (preaprobado != null) preaprobado = preaprobado.setScale(2, RoundingMode.HALF_UP);

        BigDecimal cuotaMensual = preaprobado != null && cuotas != null && cuotas > 0
                ? preaprobado.divide(BigDecimal.valueOf(cuotas), 2, RoundingMode.HALF_UP)
                : null;
        BigDecimal porcentaje = factor == null || factor.compareTo(BigDecimal.ONE) >= 0 ? null
                : factor.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

        return new OfertaCampana(moneda, dosDecimales(deudaActual), dosDecimales(capitalActual), dosDecimales(interesTotal),
                preaprobado, porcentaje, cuotas, cuotaMensual);
    }

    private static BigDecimal cero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal dosDecimales(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
