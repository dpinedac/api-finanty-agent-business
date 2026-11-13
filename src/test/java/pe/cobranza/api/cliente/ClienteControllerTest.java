package pe.cobranza.api.cliente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.cobranza.api.PruebaApi;

import static org.hamcrest.Matchers.hasSize;
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
    @DisplayName("GET /clientes/{documento}/campanas devuelve por defecto las campañas de prioridad 1 activas (estado 3), sin mirar fechas")
    void campanasDePrioridadUno() throws Exception {
        mvc.perform(getJson("/api/v1/clientes/45879632/campanas"))
                .andExpect(status().isOk())
                // Fuera: prioridad 2, campaña inactiva y asignación inactiva
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].deudaId").value(101))
                .andExpect(jsonPath("$[0].operacion").value(90000001))
                .andExpect(jsonPath("$[0].entidad").value("Banco Ejemplo"))
                .andExpect(jsonPath("$[0].cartera").value("Cartera 2019"))
                .andExpect(jsonPath("$[0].prioridad").value(1))
                .andExpect(jsonPath("$[0].campanaId").value(801))
                .andExpect(jsonPath("$[0].nombre").value("C_OFF_01_45%"))
                .andExpect(jsonPath("$[0].tipo").value("COBRANZA_GENERAL"))
                .andExpect(jsonPath("$[0].vigenteDesde").value("2020-01-01"))
                .andExpect(jsonPath("$[0].vigenteHasta").doesNotExist())
                .andExpect(jsonPath("$[0].descuento.tipo").value("FORMULA"))
                .andExpect(jsonPath("$[0].descuento.cuotasMaximas").value(1))
                .andExpect(jsonPath("$[0].descuento.formula").value("(fSaldoCapital*(1-0.45))"))
                .andExpect(jsonPath("$[0].descuento.factor").value(0.45))
                .andExpect(jsonPath("$[0].descuento.montoFijo").doesNotExist())
                .andExpect(jsonPath("$[1].campanaId").value(802))
                .andExpect(jsonPath("$[1].tipo").value("COBRANZA_DIGITAL"))
                .andExpect(jsonPath("$[1].vigenteHasta").value("2099-12-31"))
                .andExpect(jsonPath("$[1].descuento.tipo").value("FIJO"))
                .andExpect(jsonPath("$[1].descuento.montoFijo").value(720))
                // Activa con fecha de fin pasada: el estado 3 manda
                .andExpect(jsonPath("$[2].campanaId").value(804))
                .andExpect(jsonPath("$[2].vigenteHasta").value("2020-12-31"));
    }

    @Test
    @DisplayName("La campaña trae el monto preaprobado y el porcentaje de descuento calculados como en CobraTwo")
    void ofertaDeLaCampana() throws Exception {
        // Deuda 101: pagos válidos 1650 + US$ 100 x 3.500 = 2000 (el anulado no cuenta).
        // interesTotal 1650; deudaActual 4850 + 100 - 2000 = 2950; capitalActual 3200 - (2000 - 1750) = 2950.
        mvc.perform(getJson("/api/v1/clientes/45879632/campanas?tipo=COBRANZA_GENERAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].oferta.moneda").value("SOLES"))
                .andExpect(jsonPath("$[0].oferta.deudaActual").value(2950.00))
                .andExpect(jsonPath("$[0].oferta.capitalActual").value(2950.00))
                .andExpect(jsonPath("$[0].oferta.interesTotal").value(1650.00))
                .andExpect(jsonPath("$[0].oferta.montoPreaprobado").value(1622.50))   // 2950 x (1 - 0.45)
                .andExpect(jsonPath("$[0].oferta.porcentajeDescuento").value(45.00))
                .andExpect(jsonPath("$[0].oferta.cuotas").value(1))
                .andExpect(jsonPath("$[0].oferta.cuotaMensual").value(1622.50));
        // Monto fijo: el preaprobado es el monto y no hay porcentaje
        mvc.perform(getJson("/api/v1/clientes/45879632/campanas?tipo=COBRANZA_DIGITAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oferta.montoPreaprobado").value(720.00))
                .andExpect(jsonPath("$[0].oferta.porcentajeDescuento").doesNotExist());
        // Varias cuotas: 2950 x 0.70 = 2065 en 6 cuotas
        mvc.perform(getJson("/api/v1/clientes/45879632/campanas?prioridad=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oferta.montoPreaprobado").value(2065.00))
                .andExpect(jsonPath("$[0].oferta.cuotaMensual").value(344.17));
        // Deuda en dólares con un pago en soles un día sin tipo de cambio: 340 / 3.2 = 106.25
        mvc.perform(getJson("/api/v1/clientes/70123456/campanas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oferta.moneda").value("DOLARES"))
                .andExpect(jsonPath("$[0].oferta.deudaActual").value(1993.75))
                .andExpect(jsonPath("$[0].oferta.capitalActual").value(1500.00))
                .andExpect(jsonPath("$[0].entidad").doesNotExist())
                .andExpect(jsonPath("$[0].oferta.montoPreaprobado").value(1993.75))   // fTotalDeuda * 1
                // Factor 1.00 ("sin campaña"): vale el monto, no hay porcentaje
                .andExpect(jsonPath("$[0].descuento.factor").value(1.00))
                .andExpect(jsonPath("$[0].oferta.porcentajeDescuento").doesNotExist())
                .andExpect(jsonPath("$[0].oferta.cuotaMensual").value(41.54));
    }

    @Test
    @DisplayName("GET /clientes/{documento}/campanas filtra por tipo y por otra prioridad")
    void campanasPorTipoYPrioridad() throws Exception {
        mvc.perform(getJson("/api/v1/clientes/45879632/campanas?tipo=COBRANZA_DIGITAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].campanaId").value(802));
        mvc.perform(getJson("/api/v1/clientes/45879632/campanas?prioridad=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].campanaId").value(803))
                .andExpect(jsonPath("$[0].prioridad").value(2))
                .andExpect(jsonPath("$[0].descuento.cuotasMaximas").value(6));
        mvc.perform(getJson("/api/v1/clientes/70123456/campanas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].descuento.tipo").value("LIBRE"));
    }

    @Test
    @DisplayName("GET /clientes/{documento}/campanas: lista vacía sin deuda vigente, 404 sin cliente, 400 con parámetros inválidos")
    void campanasCasosBorde() throws Exception {
        // Pedro solo tiene una deuda cancelada: su campaña no se entrega
        mvc.perform(getJson("/api/v1/clientes/55667788/campanas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(getJson("/api/v1/clientes/00000000/campanas"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Cliente no encontrado"));
        mvc.perform(getJson("/api/v1/clientes/45879632/campanas?prioridad=0"))
                .andExpect(status().isBadRequest());
        mvc.perform(getJson("/api/v1/clientes/45879632/campanas?tipo=TELEVISION"))
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
