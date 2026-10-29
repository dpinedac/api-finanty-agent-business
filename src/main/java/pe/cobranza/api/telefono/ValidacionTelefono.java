package pe.cobranza.api.telefono;

import java.time.LocalDateTime;

/**
 * Resultado de validar a quién pertenece el teléfono marcado, con los tiempos de la llamada.
 * Lo registra el perfil validador en la tabla AGENTEIATT01 (TT_VALIDACION_LLAMADA_IA) de CNTSEC.
 */
public record ValidacionTelefono(String documento, String telefono, Resultado resultado, String callId,
                                 LocalDateTime inicioLlamada, LocalDateTime finLlamada, Integer duracionSegundos,
                                 String resumen) {

    /** Subconjunto del catálogo de tipificaciones propio de la validación. */
    public enum Resultado { CONTACTO_VALIDADO, TERCERO, NUMERO_EQUIVOCADO, SIN_VALIDAR, NO_EXISTE }

    /** "Es o no el cliente": solo cuando la identidad quedó validada. */
    public boolean esCliente() {
        return resultado == Resultado.CONTACTO_VALIDADO;
    }
}
