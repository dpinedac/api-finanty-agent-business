package pe.cobranza.api.telefono;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import pe.cobranza.api.PruebaApi;
import pe.cobranza.api.cntsec.CntsecJdbc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La validación se registra en AGENTEIATT01 de CNTSEC (simulada en H2 con cntsec-h2.sql). */
class TelefonoControllerTest extends PruebaApi {

    @Autowired
    CntsecJdbc cntsec;

    private Map<String, Object> fila(String callId) {
        return cntsec.sql().sql("""
                        SELECT TM08NCLIID, TT01SCLIDOC, TT01STELEFONO, TT01DLLAMADA, TT01DCOLGADO, TT01NDURACIONSEG,
                               TT01BESCLIENTE, TT01SRESULTADO, TT01SRESUMEN, TT01DREGISTRO
                          FROM AGENTEIATT01 WHERE TT01SCALLID = :callId""")
                .param("callId", callId).query().singleRow();
    }

    @Test
    @DisplayName("POST /telefonos/validacion guarda cliente, teléfono, fechas, duración, resumen y si es el cliente")
    void registrarValidacionCompleta() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"45879632","telefono":"51987654321","resultado":"CONTACTO_VALIDADO","callId":"%s",
                         "inicioLlamada":"2026-10-29T10:15:00","finLlamada":"2026-10-29T10:17:30",
                         "resumen":"El titular confirmó su identidad con los primeros dígitos del DNI."}
                        """.formatted(callId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validacionId").isNumber())
                .andExpect(jsonPath("$.resultado").value("CONTACTO_VALIDADO"))
                .andExpect(jsonPath("$.esCliente").value(true))
                .andExpect(jsonPath("$.duracionSegundos").value(150))
                .andExpect(jsonPath("$.mensaje").value("Validación registrada."));

        var f = fila(callId);
        assertThat(f.get("TM08NCLIID")).isEqualTo(1);                 // cliente resuelto en COBTM08
        assertThat(f.get("TT01SCLIDOC")).isEqualTo("45879632");
        assertThat(f.get("TT01STELEFONO")).isEqualTo("51987654321");
        assertThat(((Timestamp) f.get("TT01DLLAMADA")).toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-10-29T10:15:00"));
        assertThat(((Timestamp) f.get("TT01DCOLGADO")).toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-10-29T10:17:30"));
        assertThat(f.get("TT01NDURACIONSEG")).isEqualTo(150);
        assertThat(f.get("TT01BESCLIENTE")).isEqualTo(true);
        assertThat(f.get("TT01SRESULTADO")).isEqualTo("CONTACTO_VALIDADO");
        assertThat((String) f.get("TT01SRESUMEN")).contains("confirmó su identidad");
        assertThat(f.get("TT01DREGISTRO")).isNotNull();
    }

    @Test
    @DisplayName("Con duracionSegundos y sin finLlamada se deduce el fin; sin cliente en CNTSEC el id de cliente queda nulo")
    void duracionSinFinYSinCliente() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"00000000","telefono":"900000000","resultado":"NUMERO_EQUIVOCADO","callId":"%s",
                         "inicioLlamada":"2026-10-29T11:00:00","duracionSegundos":42}
                        """.formatted(callId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esCliente").value(false))
                .andExpect(jsonPath("$.duracionSegundos").value(42));

        var f = fila(callId);
        assertThat(f.get("TM08NCLIID")).isNull();
        assertThat(((Timestamp) f.get("TT01DCOLGADO")).toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-10-29T11:00:42"));
        assertThat(f.get("TT01BESCLIENTE")).isEqualTo(false);
    }

    @Test
    @DisplayName("Sin fechas se usa la hora del servidor y la duración queda nula")
    void sinFechas() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"70123456","telefono":"912345678","resultado":"SIN_VALIDAR","callId":"%s"}
                        """.formatted(callId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duracionSegundos").doesNotExist());
        var f = fila(callId);
        assertThat(f.get("TT01DLLAMADA")).isNotNull();
        assertThat(f.get("TT01DCOLGADO")).isNull();
        assertThat(f.get("TT01NDURACIONSEG")).isNull();
    }

    @Test
    @DisplayName("Un callId repetido responde 409 y el fin anterior al inicio responde 400")
    void duplicadoYFechasInvalidas() throws Exception {
        String callId = nuevoCallId();
        String cuerpo = """
                {"documento":"45879632","telefono":"987654321","resultado":"TERCERO","callId":"%s"}
                """.formatted(callId);
        mvc.perform(postJson("/api/v1/telefonos/validacion", cuerpo)).andExpect(status().isCreated());
        mvc.perform(postJson("/api/v1/telefonos/validacion", cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe una validación para ese callId"));

        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"45879632","telefono":"987654321","resultado":"TERCERO","callId":"%s",
                         "inicioLlamada":"2026-10-29T10:00:00","finLlamada":"2026-10-29T09:00:00"}
                        """.formatted(nuevoCallId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("finLlamada")));
    }

    @ParameterizedTest(name = "resultado {0} aceptado")
    @EnumSource(ValidacionTelefono.Resultado.class)
    void catalogoDeResultados(ValidacionTelefono.Resultado resultado) throws Exception {
        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"70123456","telefono":"912345678","resultado":"%s","callId":"%s"}
                        """.formatted(resultado.name(), nuevoCallId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value(resultado.name()))
                .andExpect(jsonPath("$.esCliente").value(resultado == ValidacionTelefono.Resultado.CONTACTO_VALIDADO));
    }

    @Test
    @DisplayName("Resultado fuera de catálogo o campos faltantes responden 400 con mensaje")
    void validacionDeEntrada() throws Exception {
        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"70123456","telefono":"912345678","resultado":"INVENTADO","callId":"%s"}
                        """.formatted(nuevoCallId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(startsWith("Cuerpo inválido")));
        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"70123456","resultado":"TERCERO","callId":"%s"}
                        """.formatted(nuevoCallId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("telefono")));
    }
}
