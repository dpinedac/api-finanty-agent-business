package pe.cobranza.api.cntsec;

import java.time.LocalDate;

/** Cliente tal como está en COBTM08 (TM_CLIENTECB), con los indicadores que deciden si se le puede llamar. */
public record ClienteCntsec(long clienteId, String documento, int tipoDocumento, String nombres,
                            String apellidoPaterno, String apellidoMaterno, LocalDate fechaNacimiento,
                            Integer situacionId, int deudasVigentes, int negociacionesVigentes) {

    public String apellidos() {
        return unir(apellidoPaterno, apellidoMaterno);
    }

    /** Nombre completo para el saludo; en personas jurídicas es la razón social. */
    public String nombreCompleto() {
        return unir(nombres, apellidos());
    }

    private static String unir(String a, String b) {
        var sa = a == null ? "" : a.trim();
        var sb = b == null ? "" : b.trim();
        return (sa + " " + sb).trim();
    }
}
