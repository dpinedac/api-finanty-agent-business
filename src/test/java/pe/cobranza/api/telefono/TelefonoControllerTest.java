package pe.cobranza.api.telefono;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import pe.cobranza.api.PruebaApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TelefonoControllerTest extends PruebaApi {

    @Autowired
    ValidacionTelefonoRepository repo;

    @Test
    @DisplayName("POST /telefonos/validacion persiste documento, teléfono, resultado y callId")
    void registrarValidacion() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"45879632","telefono":"51987654321","resultado":"CONTACTO_VALIDADO","callId":"%s"}
                        """.formatted(callId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validacionId").isNumber())
                .andExpect(jsonPath("$.resultado").value("CONTACTO_VALIDADO"))
                .andExpect(jsonPath("$.mensaje").value("Validación registrada."));

        var guardadas = repo.findByCallId(callId);
        assertThat(guardadas).hasSize(1);
        assertThat(guardadas.get(0).getDocumento()).isEqualTo("45879632");
        assertThat(guardadas.get(0).getTelefono()).isEqualTo("51987654321");
        assertThat(guardadas.get(0).getResultado()).isEqualTo(ValidacionTelefono.Resultado.CONTACTO_VALIDADO);
        assertThat(guardadas.get(0).getCreadaEn()).isNotNull();
    }

    @ParameterizedTest(name = "resultado {0} aceptado")
    @EnumSource(ValidacionTelefono.Resultado.class)
    void catalogoDeResultados(ValidacionTelefono.Resultado resultado) throws Exception {
        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"70123456","telefono":"912345678","resultado":"%s","callId":"%s"}
                        """.formatted(resultado.name(), nuevoCallId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value(resultado.name()));
    }

    @Test
    @DisplayName("Un resultado fuera del subconjunto de validación (p. ej. PROMESA_PAGO) responde 400")
    void resultadoFueraDelCatalogo() throws Exception {
        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"45879632","telefono":"51987654321","resultado":"PROMESA_PAGO","callId":"%s"}
                        """.formatted(nuevoCallId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(startsWith("Cuerpo inválido")));
    }

    @Test
    @DisplayName("Sin callId responde 400")
    void sinCallId() throws Exception {
        mvc.perform(postJson("/api/v1/telefonos/validacion",
                        """
                        {"documento":"45879632","telefono":"51987654321","resultado":"TERCERO"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("callId")));
    }
}
