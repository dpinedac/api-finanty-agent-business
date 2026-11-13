package pe.cobranza.api.campana;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.cobranza.api.campana.OfertaCampana.Moneda;
import pe.cobranza.api.campana.OfertaCampana.Saldos;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Cálculo puro del monto preaprobado (mismas definiciones que TM_ClienteDeuda.getTM15NDEUPREAPR de CobraTwo). */
class OfertaCampanaTest {

    private static final Map<String, BigDecimal> VARIABLES = Map.of(
            "fSaldoCapital", new BigDecimal("3200"),
            "fTotalDeuda", new BigDecimal("4850"),
            "fTotalIntereses", new BigDecimal("1650"));

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = ';', value = {
            " (fSaldoCapital*(1-0.45)); 1760.00",
            "(fSaldoCapital*(1)); 3200",
            "(fTotalDeuda-(fTotalDeuda*0.40)); 2910.00",
            "(fSaldoCapital+((fTotalDeuda-fSaldoCapital)*0.10)); 3365.00",
            "(fTotalDeuda-(fTotalIntereses*0.70)); 3695.00",
            "720; 720",
            "fTotalDeuda/4; 1212.5",
            "-fSaldoCapital + 3300; 100",
    })
    void evaluaLasFormulasDeCobraTwo(String formula, String esperado) {
        assertThat(FormulaDescuento.evaluar(formula, VARIABLES)).get()
                .usingComparator(BigDecimal::compareTo).isEqualTo(n(esperado));
    }

    @ParameterizedTest(name = "\"{0}\" no se evalúa")
    @ValueSource(strings = {"", "  ", "(fTotalDeuda-(fTotalInteres*0.70))", "(fSaldoCapital*(1-0.45)", "fSaldoCapital 2",
            "fSaldoCapital/0", "System.exit(0)", "fSaldoCapital*x"})
    void formulaInvalidaOConVariableDesconocidaNoDaMonto(String formula) {
        assertThat(FormulaDescuento.evaluar(formula, VARIABLES)).isEmpty();
    }

    @Test
    @DisplayName("Saldos como en CobraTwo: los pagos cubren primero los intereses y solo el exceso baja el capital")
    void saldosYPreaprobado() {
        var saldos = new Saldos(n("3200"), n("4850"), n("1000"), n("400"), n("250"), n("100"), n("2000"));
        var o = OfertaCampana.calcular(Moneda.SOLES, saldos, " (fSaldoCapital*(1-0.45))", n("0.45"), 3);

        assertThat(o.interesTotal()).isEqualByComparingTo("1650");
        assertThat(o.deudaActual()).isEqualByComparingTo("2950");      // 4850 + 100 - 2000
        assertThat(o.capitalActual()).isEqualByComparingTo("2950");    // 3200 - (2000 - 1750)
        assertThat(o.montoPreaprobado()).isEqualByComparingTo("1622.50");
        assertThat(o.porcentajeDescuento()).isEqualByComparingTo("45");
        assertThat(o.cuotaMensual()).isEqualByComparingTo("540.83");
    }

    @Test
    @DisplayName("Si los pagos no superan los intereses el capital no baja; sin fórmula el preaprobado es la deuda actual")
    void sinExcesoDePagosYSinFormula() {
        var saldos = new Saldos(n("3200"), n("4850"), n("1000"), n("400"), n("250"), null, n("500"));
        var o = OfertaCampana.calcular(Moneda.SOLES, saldos, null, null, null);

        assertThat(o.capitalActual()).isEqualByComparingTo("3200");
        assertThat(o.deudaActual()).isEqualByComparingTo("4350");
        assertThat(o.montoPreaprobado()).isEqualByComparingTo("4350");
        assertThat(o.porcentajeDescuento()).isNull();
        assertThat(o.cuotaMensual()).isNull();
    }

    @Test
    @DisplayName("Una fórmula que no se puede evaluar deja el preaprobado vacío: nunca se inventa un monto")
    void formulaDesconocida() {
        var saldos = new Saldos(n("3200"), n("4850"), n("0"), n("0"), n("0"), n("0"), null);
        var o = OfertaCampana.calcular(Moneda.SOLES, saldos, "(fTotalDeuda-(fTotalInteres*0.70))", n("0.70"), 1);

        assertThat(o.montoPreaprobado()).isNull();
        assertThat(o.cuotaMensual()).isNull();
        assertThat(o.deudaActual()).isEqualByComparingTo("4850");
        assertThat(o.porcentajeDescuento()).isEqualByComparingTo("70");
    }
}
