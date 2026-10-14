package pe.cobranza.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regresión de la fase 1: los endpoints existentes conservan su forma. */
class EndpointsExistentesTest extends PruebaApi {

    @Test
    @DisplayName("GET /clientes/{documento} devuelve documento y nombre; 404 si no existe")
    void clienteResumen() throws Exception {
        mvc.perform(getJson("/api/v1/clientes/45879632"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("45879632"))
                .andExpect(jsonPath("$.nombre").value("Carlos Ramírez Soto"));
        mvc.perform(getJson("/api/v1/clientes/00000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cliente no encontrado"));
    }

    @Test
    @DisplayName("POST /clientes/verificar valida con año de nacimiento y descuenta intentos por callId")
    void verificar() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/clientes/verificar",
                        "{\"documento\":\"70123456\",\"anioNacimiento\":1990,\"callId\":\"" + callId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(false))
                .andExpect(jsonPath("$.intentosRestantes").value(2));
        mvc.perform(postJson("/api/v1/clientes/verificar",
                        "{\"documento\":\"70123456\",\"anioNacimiento\":1992,\"callId\":\"" + callId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(true))
                .andExpect(jsonPath("$.nombre").value("María"))
                .andExpect(jsonPath("$.intentosRestantes").value(3));
    }

    @Test
    @DisplayName("GET /deudas/{documento} y POST /ofertas/calcular conservan su forma")
    void deudasYOfertas() throws Exception {
        mvc.perform(getJson("/api/v1/deudas/10293847"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].deudaId").isNumber())
                .andExpect(jsonPath("$[0].saldoTotal").isNumber())
                .andExpect(jsonPath("$[0].estado").value("VIGENTE"));
        mvc.perform(postJson("/api/v1/ofertas/calcular", "{\"documento\":\"45879632\",\"deudaId\":1,\"cuotas\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deudaId").value(1))
                .andExpect(jsonPath("$.cuotas").value(3))
                .andExpect(jsonPath("$.porcentajeDescuento").value(50))
                .andExpect(jsonPath("$.montoConDescuento").value(2425.00))
                .andExpect(jsonPath("$.montoCuota").value(808.33))
                .andExpect(jsonPath("$.vigenteHasta").isNotEmpty());
        mvc.perform(postJson("/api/v1/ofertas/calcular", "{\"documento\":\"45879632\",\"deudaId\":1,\"cuotas\":5}"))
                .andExpect(status().isBadRequest());
    }
}
