package pe.cobranza.api.rellamada;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.cobranza.api.PruebaApi;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RellamadaControllerTest extends PruebaApi {

    private String agendar(String documento, LocalDateTime fechaHora, String callId) throws Exception {
        mvc.perform(postJson("/api/v1/rellamadas",
                        """
                        {"documento":"%s","fechaHora":"%s","motivo":"Pidió que lo llamen","callId":"%s"}
                        """.formatted(documento, fechaHora, callId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rellamadaId").isNumber())
                .andExpect(jsonPath("$.mensaje").value("Rellamada agendada."));
        return callId;
    }

    @Test
    @DisplayName("GET /rellamadas?pendientes=true devuelve las agendadas en estado PENDIENTE con todos sus campos")
    void listarPendientes() throws Exception {
        String callId = nuevoCallId();
        LocalDateTime fecha = LocalDateTime.now().plusDays(40).truncatedTo(ChronoUnit.SECONDS);
        agendar("45879632", fecha, callId);

        mvc.perform(getJson("/api/v1/rellamadas?pendientes=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.callId == '%s')]".formatted(callId), hasSize(1)))
                .andExpect(jsonPath("$[?(@.callId == '%s')].documento".formatted(callId), contains("45879632")))
                .andExpect(jsonPath("$[?(@.callId == '%s')].estado".formatted(callId), contains("PENDIENTE")))
                .andExpect(jsonPath("$[?(@.callId == '%s')].motivo".formatted(callId), contains("Pidió que lo llamen")))
                .andExpect(jsonPath("$[?(@.callId == '%s')].fechaHora".formatted(callId), contains(startsWith(fecha.toString()))))
                .andExpect(jsonPath("$[?(@.callId == '%s')].creadaEn".formatted(callId), hasSize(1)));
    }

    @Test
    @DisplayName("GET /rellamadas con desde y hasta acota por fechaHora (inclusivo) y ordena ascendente")
    void filtrarPorRango() throws Exception {
        LocalDateTime base = LocalDateTime.now().plusDays(100).truncatedTo(ChronoUnit.SECONDS);
        String dentro1 = agendar("45879632", base.plusHours(3), nuevoCallId());
        String dentro2 = agendar("70123456", base.plusHours(1), nuevoCallId());
        String fuera = agendar("10293847", base.plusDays(2), nuevoCallId());

        mvc.perform(getJson("/api/v1/rellamadas?pendientes=true&desde=" + base + "&hasta=" + base.plusHours(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].callId").value(dentro2))
                .andExpect(jsonPath("$[1].callId").value(dentro1))
                .andExpect(jsonPath("$[*].callId", not(hasItem(fuera))));

        // Solo desde: incluye la que está dos días después
        mvc.perform(getJson("/api/v1/rellamadas?desde=" + base.plusDays(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].callId", hasItem(fuera)))
                .andExpect(jsonPath("$[*].callId", not(hasItem(dentro1))));
    }

    @Test
    @DisplayName("GET /rellamadas con rango vacío devuelve lista vacía")
    void rangoSinResultados() throws Exception {
        LocalDateTime lejos = LocalDateTime.now().plusYears(30);
        mvc.perform(getJson("/api/v1/rellamadas?pendientes=true&desde=" + lejos + "&hasta=" + lejos.plusDays(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /rellamadas con fecha mal formada o rango invertido responde 400")
    void parametrosInvalidos() throws Exception {
        mvc.perform(getJson("/api/v1/rellamadas?desde=ayer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("desde")));
        LocalDateTime base = LocalDateTime.now().plusDays(1);
        mvc.perform(getJson("/api/v1/rellamadas?desde=" + base.plusDays(1) + "&hasta=" + base))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("hasta")));
    }
}
