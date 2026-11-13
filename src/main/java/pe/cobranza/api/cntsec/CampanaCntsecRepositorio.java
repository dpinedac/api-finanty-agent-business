package pe.cobranza.api.cntsec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import pe.cobranza.api.campana.OfertaCampana;
import pe.cobranza.api.campana.OfertaCampana.Moneda;
import pe.cobranza.api.campana.OfertaCampana.Saldos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Campañas de CobraTwo asignadas a las deudas vigentes de un cliente, con el monto preaprobado ya calculado.
 * Solo lectura sobre COBTT15 (deuda-campaña, con la prioridad), COBTM16 (campaña), COBTM14 (descuento),
 * COBTM15 (montos de la deuda), COBTM07 y COBTM01 (cartera y entidad), COBTT03 (pagos) y
 * TMP_TIPOCAMBIOSUNAT (tipo de cambio).
 * Vigencia: una campaña está vigente si su estado y el de la asignación son activo (3); las fechas no filtran.
 * SQL compatible con SQL Server y H2 (modo MSSQLServer).
 */
@Repository
public class CampanaCntsecRepositorio {

    private static final Logger log = LoggerFactory.getLogger(CampanaCntsecRepositorio.class);

    /** Una deuda suele tener una campaña por tipo (general y digital) con la misma prioridad. */
    public enum TipoCampana { COBRANZA_GENERAL, COBRANZA_DIGITAL, OTRO }

    public enum TipoDescuento { FORMULA, FIJO, LIBRE, OTRO }

    /** Descuento tal como está en COBTM14. */
    public record Descuento(int descuentoId, TipoDescuento tipo, Integer cuotasMaximas, String formula,
                            BigDecimal factor, BigDecimal montoFijo) {}

    public record CampanaDeuda(long deudaId, Long operacion, String entidad, String cartera,
                               int prioridad, int campanaId, String nombre,
                               String descripcion, TipoCampana tipo, LocalDate vigenteDesde, LocalDate vigenteHasta,
                               Descuento descuento, OfertaCampana oferta) {}

    private static final String SQL_POR_DOCUMENTO = """
            SELECT d.TM15NDEUID, d.TM15NDEUOPE, d.TG01NMONID, d.TM15NDEUCAPINI, d.TM15NDEUINI,
                   d.TM15NDEUINTINI, d.TM15NDEUINTMOR, d.TM15NDEUINTCOM, d.TM15NDEUINTLEG, t.TT15NPRIO,
                   c.TM16NCPNID, c.TM16SCPNNOM, c.TM16SCPNDES, c.TG01NCPNTIP, c.TM16DCPNNI, c.TM16DCPNFIN,
                   ds.TM14NDESID, ds.TG01NDESTIP, ds.TM14NDESCUO, ds.TM14SDESFRM, ds.TM14NDESPCENT,
                   co.TM07SCOSDES, en.TM01SENTDES
              FROM COBTM08 cl
             INNER JOIN COBTM15 d ON d.TM08NCLIID = cl.TM08NCLIID AND d.TG01NDEUSIT = :deudaVigente
             INNER JOIN COBTT15 t ON t.TM15NDEUID = d.TM15NDEUID AND t.TT15NPRIO = :prioridad
                                 AND t.TG01NGENCAMPDEUEST = :activo
             INNER JOIN COBTM16 c ON c.TM16NCPNID = t.TM16NCPNID AND c.TG01NCPNEST = :activo
              LEFT JOIN COBTM14 ds ON ds.TM14NDESID = c.TM14NDESID
              LEFT JOIN COBTM07 co ON co.TM07NCOSID = d.TM07NCOSID
              LEFT JOIN COBTM01 en ON en.TM01NENTID = co.TM01NENTID
             WHERE cl.TM08SCLIDOC = :documento
             ORDER BY d.TM15NDEUID, c.TG01NCPNTIP, c.TM16NCPNID
            """;

    /** Pagos válidos de las deudas vigentes del cliente, con el tipo de cambio del día de cada pago. */
    private static final String SQL_PAGOS = """
            SELECT p.TM15NDEUID, d.TG01NMONID AS MONEDA_DEUDA, p.TG01NMONID AS MONEDA_PAGO, p.TT03NPAGIMP,
                   tc.dcTipoCambio
              FROM COBTM08 cl
             INNER JOIN COBTM15 d ON d.TM08NCLIID = cl.TM08NCLIID AND d.TG01NDEUSIT = :deudaVigente
             INNER JOIN COBTT03 p ON p.TM15NDEUID = d.TM15NDEUID AND p.TG01NPAGEST = :activo
              LEFT JOIN TMP_TIPOCAMBIOSUNAT tc ON tc.dFecha = p.TT03DPAGFEC
             WHERE cl.TM08SCLIDOC = :documento
               AND p.TG01NMONID IN (:soles, :dolares)
            """;

    private static final String SQL_ULTIMO_TIPO_CAMBIO =
            "SELECT TOP 1 dcTipoCambio FROM TMP_TIPOCAMBIOSUNAT ORDER BY dFecha DESC";

    private final CntsecJdbc cntsec;
    private final CntsecPropiedades.Catalogo catalogo;
    private final BigDecimal tipoCambioPorDefecto;

    public CampanaCntsecRepositorio(CntsecJdbc cntsec, CntsecPropiedades props) {
        this.cntsec = cntsec;
        this.catalogo = props.catalogo();
        this.tipoCambioPorDefecto = props.tipoCambioPorDefecto() != null
                ? props.tipoCambioPorDefecto() : new BigDecimal("3.2");
    }

    /** Campañas activas (estado 3, sin mirar fechas) con esa prioridad, de las deudas vigentes del documento. */
    public List<CampanaDeuda> porDocumento(String documento, int prioridad) {
        String doc = documento.trim();
        Map<Long, BigDecimal> pagos = pagosPorDeuda(doc);
        return cntsec.sql().sql(SQL_POR_DOCUMENTO)
                .param("documento", doc)
                .param("deudaVigente", catalogo.deudaVigente())
                .param("activo", catalogo.registroActivo())
                .param("prioridad", prioridad)
                .query((rs, i) -> fila(rs, pagos))
                .list();
    }

    /** Total pagado por deuda, convertido a la moneda de la deuda con el tipo de cambio del día del pago. */
    private Map<Long, BigDecimal> pagosPorDeuda(String documento) {
        Map<Long, BigDecimal> total = new HashMap<>();
        cntsec.sql().sql(SQL_PAGOS)
                .param("documento", documento)
                .param("deudaVigente", catalogo.deudaVigente())
                .param("activo", catalogo.registroActivo())
                .param("soles", catalogo.monedaSoles())
                .param("dolares", catalogo.monedaDolares())
                .query((rs, i) -> {
                    BigDecimal importe = rs.getBigDecimal("TT03NPAGIMP");
                    if (importe == null) return null;
                    int monedaDeuda = rs.getInt("MONEDA_DEUDA");
                    int monedaPago = rs.getInt("MONEDA_PAGO");
                    BigDecimal tc = rs.getBigDecimal("dcTipoCambio");
                    if (tc == null || tc.signum() <= 0) tc = tipoCambioPorDefecto;
                    if (monedaPago == catalogo.monedaSoles() && monedaDeuda == catalogo.monedaDolares()) {
                        importe = importe.divide(tc, 6, RoundingMode.HALF_UP);
                    } else if (monedaPago == catalogo.monedaDolares() && monedaDeuda == catalogo.monedaSoles()) {
                        importe = importe.multiply(tc);
                    }
                    total.merge(rs.getLong("TM15NDEUID"), importe, BigDecimal::add);
                    return null;
                })
                .list();
        return total;
    }

    private CampanaDeuda fila(ResultSet rs, Map<Long, BigDecimal> pagos) throws SQLException {
        long deudaId = rs.getLong("TM15NDEUID");
        long operacion = rs.getLong("TM15NDEUOPE");
        Long operacionONulo = rs.wasNull() ? null : operacion;
        Moneda moneda = moneda(rs.getInt("TG01NMONID"));
        Descuento descuento = descuento(rs, moneda);

        var saldos = new Saldos(rs.getBigDecimal("TM15NDEUCAPINI"), rs.getBigDecimal("TM15NDEUINI"),
                rs.getBigDecimal("TM15NDEUINTINI"), rs.getBigDecimal("TM15NDEUINTMOR"),
                rs.getBigDecimal("TM15NDEUINTCOM"), rs.getBigDecimal("TM15NDEUINTLEG"), pagos.get(deudaId));
        String formula = descuento == null ? null
                : descuento.tipo() == TipoDescuento.FIJO && descuento.montoFijo() != null
                        ? descuento.montoFijo().toPlainString() : descuento.formula();
        var oferta = OfertaCampana.calcular(moneda, saldos, formula,
                descuento == null ? null : descuento.factor(),
                descuento == null ? null : descuento.cuotasMaximas());
        if (oferta.montoPreaprobado() == null) {
            log.warn("Deuda {}: no se pudo evaluar la fórmula de la campaña {} ({}); sin monto preaprobado",
                    deudaId, rs.getInt("TM16NCPNID"), formula);
        }

        return new CampanaDeuda(deudaId, operacionONulo,
                texto(rs.getString("TM01SENTDES")), texto(rs.getString("TM07SCOSDES")), rs.getInt("TT15NPRIO"),
                rs.getInt("TM16NCPNID"), texto(rs.getString("TM16SCPNNOM")), texto(rs.getString("TM16SCPNDES")),
                tipoCampana(rs.getInt("TG01NCPNTIP")),
                fecha(rs.getDate("TM16DCPNNI")), fecha(rs.getDate("TM16DCPNFIN")),
                descuento, oferta);
    }

    private Descuento descuento(ResultSet rs, Moneda moneda) throws SQLException {
        int id = rs.getInt("TM14NDESID");
        if (rs.wasNull()) return null;
        TipoDescuento tipo = tipoDescuento(rs.getInt("TG01NDESTIP"));
        int cuotas = rs.getInt("TM14NDESCUO");
        Integer cuotasONulo = rs.wasNull() ? null : cuotas;
        String formula = texto(rs.getString("TM14SDESFRM"));
        BigDecimal factor = rs.getBigDecimal("TM14NDESPCENT");
        // En un descuento FIJO la "fórmula" es el monto a pagar en soles (p. ej. "720"); para una deuda
        // en dólares CobraTwo lo convierte con el último tipo de cambio, a un decimal.
        BigDecimal montoFijo = null;
        if (tipo == TipoDescuento.FIJO) {
            montoFijo = monto(formula);
            if (montoFijo != null && moneda == Moneda.DOLARES) {
                montoFijo = montoFijo.divide(ultimoTipoCambio(), 1, RoundingMode.HALF_UP);
            }
        }
        return new Descuento(id, tipo, cuotasONulo, formula, factor, montoFijo);
    }

    private BigDecimal ultimoTipoCambio() {
        return cntsec.sql().sql(SQL_ULTIMO_TIPO_CAMBIO).query(BigDecimal.class).optional()
                .filter(tc -> tc.signum() > 0)
                .orElse(tipoCambioPorDefecto);
    }

    private Moneda moneda(int id) {
        if (id == catalogo.monedaSoles()) return Moneda.SOLES;
        if (id == catalogo.monedaDolares()) return Moneda.DOLARES;
        return Moneda.OTRA;
    }

    private TipoCampana tipoCampana(int id) {
        if (id == catalogo.campanaGeneral()) return TipoCampana.COBRANZA_GENERAL;
        if (id == catalogo.campanaDigital()) return TipoCampana.COBRANZA_DIGITAL;
        return TipoCampana.OTRO;
    }

    private TipoDescuento tipoDescuento(int id) {
        if (id == catalogo.descuentoFormula()) return TipoDescuento.FORMULA;
        if (id == catalogo.descuentoFijo()) return TipoDescuento.FIJO;
        if (id == catalogo.descuentoLibre()) return TipoDescuento.LIBRE;
        return TipoDescuento.OTRO;
    }

    private static BigDecimal monto(String s) {
        if (s == null) return null;
        try {
            return new BigDecimal(s.replace(",", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate fecha(Date d) {
        return d == null ? null : d.toLocalDate();
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
