package pe.cobranza.api.campana;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Optional;

/**
 * Evalúa la fórmula de descuento de CobraTwo (COBTM14.TM14SDESFRM), por ejemplo
 * {@code (fSaldoCapital*(1-0.45))}, {@code (fTotalDeuda-(fTotalDeuda*0.40))} o un monto fijo {@code 720}.
 *
 * Solo aritmética: números, variables, + - * / y paréntesis. A propósito no se usa un motor de expresiones:
 * la fórmula viene de una tabla y no debe poder ejecutar nada más que una cuenta.
 * Si la fórmula no se entiende o usa una variable desconocida no hay resultado: nunca se inventa un monto.
 */
public final class FormulaDescuento {

    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private final String texto;
    private final Map<String, BigDecimal> variables;
    private int pos;

    private FormulaDescuento(String texto, Map<String, BigDecimal> variables) {
        this.texto = texto;
        this.variables = variables;
    }

    /** Resultado de la fórmula, o vacío si está mal formada, usa una variable desconocida o divide entre cero. */
    public static Optional<BigDecimal> evaluar(String formula, Map<String, BigDecimal> variables) {
        if (formula == null || formula.isBlank()) return Optional.empty();
        var f = new FormulaDescuento(formula, variables);
        try {
            BigDecimal valor = f.suma();
            f.espacios();
            return f.pos == f.texto.length() ? Optional.of(valor) : Optional.empty();
        } catch (IllegalArgumentException | ArithmeticException e) {
            return Optional.empty();
        }
    }

    private BigDecimal suma() {
        BigDecimal valor = producto();
        while (true) {
            if (consume('+')) valor = valor.add(producto(), PRECISION);
            else if (consume('-')) valor = valor.subtract(producto(), PRECISION);
            else return valor;
        }
    }

    private BigDecimal producto() {
        BigDecimal valor = unario();
        while (true) {
            if (consume('*')) valor = valor.multiply(unario(), PRECISION);
            else if (consume('/')) valor = valor.divide(unario(), PRECISION);
            else return valor;
        }
    }

    private BigDecimal unario() {
        if (consume('-')) return unario().negate();
        if (consume('+')) return unario();
        if (consume('(')) {
            BigDecimal valor = suma();
            if (!consume(')')) throw new IllegalArgumentException("falta )");
            return valor;
        }
        espacios();
        int inicio = pos;
        if (pos < texto.length() && (Character.isDigit(texto.charAt(pos)) || texto.charAt(pos) == '.')) {
            while (pos < texto.length() && (Character.isDigit(texto.charAt(pos)) || texto.charAt(pos) == '.')) pos++;
            return new BigDecimal(texto.substring(inicio, pos));
        }
        while (pos < texto.length() && (Character.isLetterOrDigit(texto.charAt(pos)) || texto.charAt(pos) == '_')) pos++;
        BigDecimal valor = variables.get(texto.substring(inicio, pos));
        if (valor == null) throw new IllegalArgumentException("variable desconocida");
        return valor;
    }

    private boolean consume(char c) {
        espacios();
        if (pos < texto.length() && texto.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private void espacios() {
        while (pos < texto.length() && Character.isWhitespace(texto.charAt(pos))) pos++;
    }
}
