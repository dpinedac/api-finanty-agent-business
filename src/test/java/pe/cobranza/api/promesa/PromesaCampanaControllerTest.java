package pe.cobranza.api.promesa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.cobranza.api.PruebaApi;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La promesa se registra sobre la campaña de CobraTwo: el monto lo recalcula el servidor. */
class PromesaCampanaControllerTest extends PruebaApi {

    private static String cuerpo(String documento, long deudaId, int cuotas, LocalDate fecha, String extra) {
        return "{\"documento\":\"" + documento + "\",\"deudaId\":" + deudaId + ",\"cuotas\":" + cuotas
                + ",\"fechaPrimerPago\":\"" + fecha + "\",\"callId\":\"" + nuevoCallId() + "\"" + extra + "}";
    }

    private static LocalDate manana() {
        return LocalDate.now().plusDays(1);
    }

    @Test
    @DisplayName("Por defecto promete sobre la campaña general de prioridad 1, con el monto preaprobado")
    void registraSobreLaCampanaGeneral() throws Exception {
        mvc.perform(postJson("/api/v1/promesas/campana", cuerpo("45879632", 101, 1, manana(), "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promesaId").isNumber())
                .andExpect(jsonPath("$.deudaId").value(101))
                .andExpect(jsonPath("$.operacion").value(90000001))
                .andExpect(jsonPath("$.campanaId").value(801))
                .andExpect(jsonPath("$.moneda").value("SOLES"))
                .andExpect(jsonPath("$.cuotas").value(1))
                .andExpect(jsonPath("$.montoTotal").value(1622.50))
                .andExpect(jsonPath("$.montoCuota").value(1622.50));
    }

    @Test
    @DisplayName("Admite hasta las cuotas de la campaña y la cuota es el monto entre las cuotas pedidas")
    void cuotasDeLaCampana() throws Exception {
        // Prioridad 2 de la deuda 101: 2065.00 en hasta 6 cuotas
        mvc.perform(postJson("/api/v1/promesas/campana", cuerpo("45879632", 101, 3, manana(), ",\"prioridad\":2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campanaId").value(803))
                .andExpect(jsonPath("$.montoTotal").value(2065.00))
                .andExpect(jsonPath("$.montoCuota").value(688.33));
        mvc.perform(postJson("/api/v1/promesas/campana", cuerpo("45879632", 101, 7, manana(), ",\"prioridad\":2")))
                .andExpect(status().isBadRequest());
        // La general de prioridad 1 es de una sola cuota
        mvc.perform(postJson("/api/v1/promesas/campana", cuerpo("45879632", 101, 2, manana(), "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("404 si la deuda no es del cliente o no tiene esa campaña; 400 con fecha fuera de plazo")
    void casosBorde() throws Exception {
        // La deuda 102 es de otro cliente
        mvc.perform(postJson("/api/v1/promesas/campana", cuerpo("45879632", 102, 1, manana(), "")))
                .andExpect(status().isNotFound());
        // María no tiene campaña digital
        mvc.perform(postJson("/api/v1/promesas/campana",
                        cuerpo("70123456", 102, 1, manana(), ",\"tipo\":\"COBRANZA_DIGITAL\"")))
                .andExpect(status().isNotFound());
        mvc.perform(postJson("/api/v1/promesas/campana", cuerpo("45879632", 101, 1, LocalDate.now().minusDays(1), "")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/promesas/campana", cuerpo("45879632", 101, 1, LocalDate.now().plusDays(400), "")))
                .andExpect(status().isBadRequest());
    }
}
