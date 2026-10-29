package pe.cobranza.api.cliente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.cobranza.api.PruebaApi;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Clientes leídos de CNTSEC (simulada en H2 con cntsec-h2.sql). */
class ClienteControllerTest extends PruebaApi {

    @Test
    @DisplayName("GET /clientes/{documento} devuelve nombre, apellidos y llamable=true para un deudor vigente")
    void clienteLlamable() throws Exception {
        mvc.perform(getJson("/api/v1/clientes/45879632"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("45879632"))
                .andExpect(jsonPath("$.nombre").value("Carlos Ramírez Soto"))
                .andExpect(jsonPath("$.nombres").value("Carlos"))
                .andExpect(jsonPath("$.apellidos").value("Ramírez Soto"))
                .andExpect(jsonPath("$.llamable").value(true))
                .andExpect(jsonPath("$.motivoNoLlamable").doesNotExist());
    }

    @Test
    @DisplayName("No se llama a fallecidos, a clientes sin deuda vigente ni con negociación vigente")
    void clientesNoLlamables() throws Exception {
        mvc.perform(getJson("/api/v1/clientes/33445566"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llamable").value(false))
                .andExpect(jsonPath("$.motivoNoLlamable").value("FALLECIDO"));
        mvc.perform(getJson("/api/v1/clientes/55667788"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llamable").value(false))
                .andExpect(jsonPath("$.motivoNoLlamable").value("SIN_DEUDA_VIGENTE"));
        mvc.perform(getJson("/api/v1/clientes/10293847"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llamable").value(false))
                .andExpect(jsonPath("$.motivoNoLlamable").value("NEGOCIACION_VIGENTE"));
    }

    @Test
    @DisplayName("POST /clientes/verificar valida con confirmación y los primeros 4 dígitos del documento")
    void verificarConDigitos() throws Exception {
        String callId = nuevoCallId();
        // Confirma pero los dígitos no coinciden: consume un intento
        mvc.perform(postJson("/api/v1/clientes/verificar",
                        "{\"documento\":\"45879632\",\"confirmaIdentidad\":true,\"digitosDocumento\":\"4588\",\"callId\":\"" + callId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(false))
                .andExpect(jsonPath("$.intentosRestantes").value(2));
        // Dígitos correctos pero no confirma ser el titular
        mvc.perform(postJson("/api/v1/clientes/verificar",
                        "{\"documento\":\"45879632\",\"confirmaIdentidad\":false,\"digitosDocumento\":\"4587\",\"callId\":\"" + callId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(false))
                .andExpect(jsonPath("$.intentosRestantes").value(1));
        // Confirma y dice los 4 primeros dígitos
        mvc.perform(postJson("/api/v1/clientes/verificar",
                        "{\"documento\":\"45879632\",\"confirmaIdentidad\":true,\"digitosDocumento\":\"4587\",\"callId\":\"" + callId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(true))
                .andExpect(jsonPath("$.nombre").value("Carlos"))
                .andExpect(jsonPath("$.intentosRestantes").value(3));
    }

    @Test
    @DisplayName("Solo decir 'sí, soy yo' sin dígitos no valida; sin ningún dato responde 400")
    void confirmacionSolaNoBasta() throws Exception {
        mvc.perform(postJson("/api/v1/clientes/verificar",
                        "{\"documento\":\"45879632\",\"confirmaIdentidad\":true,\"callId\":\"" + nuevoCallId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(false));
        mvc.perform(postJson("/api/v1/clientes/verificar",
                        "{\"documento\":\"45879632\",\"callId\":\"" + nuevoCallId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Documento inexistente: 404 en la consulta y verificado=false sin revelar nada")
    void documentoInexistente() throws Exception {
        mvc.perform(getJson("/api/v1/clientes/00000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cliente no encontrado"));
        mvc.perform(postJson("/api/v1/clientes/verificar",
                        "{\"documento\":\"00000000\",\"confirmaIdentidad\":true,\"digitosDocumento\":\"0000\",\"callId\":\"" + nuevoCallId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(false))
                .andExpect(jsonPath("$.nombre").doesNotExist());
    }
}
