package pe.cobranza.api.llamada;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Perfil del agente que atiende la llamada; lo define el tipo de campaña en back-discador. */
public enum Perfil {
    VALIDADOR, SEGUIMIENTO, NEGOCIADOR;

    /** En el contrato viaja en minúsculas (validador | seguimiento | negociador). */
    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static Perfil desde(String valor) {
        if (valor == null) return null;
        for (Perfil p : values()) {
            if (p.name().equalsIgnoreCase(valor.trim())) return p;
        }
        throw new IllegalArgumentException("Perfil desconocido: " + valor + ". Opciones: validador, seguimiento, negociador");
    }
}
