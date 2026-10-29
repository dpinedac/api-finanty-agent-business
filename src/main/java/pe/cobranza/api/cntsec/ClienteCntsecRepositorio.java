package pe.cobranza.api.cntsec;

import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

/** Consultas de solo lectura sobre COBTM08, COBTM15 y COBTT02. SQL compatible con SQL Server y H2 (modo MSSQLServer). */
@Repository
public class ClienteCntsecRepositorio {

    private static final String SQL_POR_DOCUMENTO = """
            SELECT c.TM08NCLIID, c.TM08SCLIDOC, c.TG01NCLITDC, c.TM08SCLINOM, c.TM08SCLIAPP, c.TM08SCLIAPM,
                   c.TM08DCLINAC, c.TG01NCLISIT,
                   (SELECT COUNT(*) FROM COBTM15 d
                     WHERE d.TM08NCLIID = c.TM08NCLIID AND d.TG01NDEUSIT = :deudaVigente) AS deudas_vigentes,
                   (SELECT COUNT(*) FROM COBTT02 n INNER JOIN COBTM15 d2 ON d2.TM15NDEUID = n.TM15NDEUID
                     WHERE d2.TM08NCLIID = c.TM08NCLIID AND n.TG01NNEGSIT = :negociacionVigente) AS negociaciones_vigentes
              FROM COBTM08 c
             WHERE c.TM08SCLIDOC = :documento
            """;

    private final CntsecJdbc cntsec;
    private final CntsecPropiedades.Catalogo catalogo;

    public ClienteCntsecRepositorio(CntsecJdbc cntsec, CntsecPropiedades props) {
        this.cntsec = cntsec;
        this.catalogo = props.catalogo();
    }

    public Optional<ClienteCntsec> porDocumento(String documento) {
        return cntsec.sql().sql(SQL_POR_DOCUMENTO)
                .param("documento", documento.trim())
                .param("deudaVigente", catalogo.deudaVigente())
                .param("negociacionVigente", catalogo.negociacionVigente())
                .query((rs, i) -> {
                    Timestamp nac = rs.getTimestamp("TM08DCLINAC");
                    int situacion = rs.getInt("TG01NCLISIT");
                    Integer situacionId = rs.wasNull() ? null : situacion;
                    return new ClienteCntsec(
                            rs.getLong("TM08NCLIID"), rs.getString("TM08SCLIDOC"), rs.getInt("TG01NCLITDC"),
                            rs.getString("TM08SCLINOM"), rs.getString("TM08SCLIAPP"), rs.getString("TM08SCLIAPM"),
                            nac == null ? null : nac.toLocalDateTime().toLocalDate(),
                            situacionId,
                            rs.getInt("deudas_vigentes"), rs.getInt("negociaciones_vigentes"));
                })
                .optional();
    }

    public boolean fallecido(ClienteCntsec c) {
        return c.situacionId() != null && c.situacionId() == catalogo.clienteFallecido();
    }
}
