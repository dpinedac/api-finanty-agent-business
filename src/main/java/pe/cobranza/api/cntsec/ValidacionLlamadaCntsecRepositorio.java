package pe.cobranza.api.cntsec;

import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import pe.cobranza.api.telefono.ValidacionTelefono;

/**
 * Única escritura de la API en CNTSEC: la tabla propia del agente AGENTEIATT01 (sinónimo TT_VALIDACION_LLAMADA_IA),
 * creada con specs/sql/AGENTEIATT01.sql. Guarda cliente, teléfono, fecha de la llamada, resumen,
 * si es o no el cliente, duración desde que contestó hasta que colgó y fecha de registro.
 */
@Repository
public class ValidacionLlamadaCntsecRepositorio {

    private static final String SQL_INSERT = """
            INSERT INTO AGENTEIATT01 (TT01SCALLID, TM08NCLIID, TT01SCLIDOC, TT01STELEFONO, TT01DLLAMADA, TT01DCOLGADO,
                                      TT01NDURACIONSEG, TT01BESCLIENTE, TT01SRESULTADO, TT01SRESUMEN, TT01DREGISTRO)
            VALUES (:callId, :clienteId, :documento, :telefono, :inicio, :fin,
                    :duracion, :esCliente, :resultado, :resumen, CURRENT_TIMESTAMP)
            """;

    private final CntsecJdbc cntsec;

    public ValidacionLlamadaCntsecRepositorio(CntsecJdbc cntsec) {
        this.cntsec = cntsec;
    }

    /** @return id generado (TT01NVALLLAID). Lanza DuplicateKeyException si el callId ya fue registrado. */
    public long insertar(ValidacionTelefono v, Long clienteId) {
        var keys = new GeneratedKeyHolder();
        cntsec.sql().sql(SQL_INSERT)
                .param("callId", v.callId())
                .param("clienteId", clienteId)
                .param("documento", v.documento())
                .param("telefono", v.telefono())
                .param("inicio", v.inicioLlamada())
                .param("fin", v.finLlamada())
                .param("duracion", v.duracionSegundos())
                .param("esCliente", v.esCliente())
                .param("resultado", v.resultado().name())
                .param("resumen", v.resumen())
                .update(keys);
        return keys.getKey().longValue();
    }
}
