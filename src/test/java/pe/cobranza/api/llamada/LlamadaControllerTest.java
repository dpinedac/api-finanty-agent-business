package pe.cobranza.api.llamada;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import pe.cobranza.api.PruebaApi;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LlamadaControllerTest extends PruebaApi {

    private static String cuerpoInicio(String callId) {
        return """
                {"callId":"%s","documento":"45879632","telefono":"51987654321","perfil":"validador",
                 "campana":"VALIDACION-OCT-2026","guionHash":"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"}
                """.formatted(callId);
    }

    // ---------- POST /api/v1/llamadas ----------

    @Test
    @DisplayName("POST /llamadas registra el inicio con fecha y responde 201")
    void iniciarLlamada() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/llamadas", cuerpoInicio(callId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.llamadaId").isNumber())
                .andExpect(jsonPath("$.callId").value(callId))
                .andExpect(jsonPath("$.inicio").isNotEmpty())
                .andExpect(jsonPath("$.mensaje").value("Llamada registrada."));
    }

    @Test
    @DisplayName("POST /llamadas acepta el perfil en mayúsculas y lo devuelve en minúsculas")
    void perfilInsensibleAMayusculas() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/llamadas",
                        """
                        {"callId":"%s","documento":"45879632","telefono":"51987654321","perfil":"NEGOCIADOR"}
                        """.formatted(callId)))
                .andExpect(status().isCreated());
        mvc.perform(getJson("/api/v1/llamadas/" + callId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("negociador"))
                .andExpect(jsonPath("$.campana").value(nullValue()))
                .andExpect(jsonPath("$.guionHash").value(nullValue()));
    }

    @Test
    @DisplayName("POST /llamadas con callId repetido responde 409")
    void callIdDuplicado() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/llamadas", cuerpoInicio(callId))).andExpect(status().isCreated());
        mvc.perform(postJson("/api/v1/llamadas", cuerpoInicio(callId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("callId")));
    }

    @Test
    @DisplayName("POST /llamadas sin perfil o con perfil desconocido responde 400")
    void perfilInvalido() throws Exception {
        mvc.perform(postJson("/api/v1/llamadas",
                        """
                        {"callId":"%s","documento":"45879632","telefono":"51987654321"}
                        """.formatted(nuevoCallId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("perfil")));
        mvc.perform(postJson("/api/v1/llamadas",
                        """
                        {"callId":"%s","documento":"45879632","telefono":"51987654321","perfil":"cobrador"}
                        """.formatted(nuevoCallId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DisplayName("Sin X-API-Key responde 401")
    void sinApiKey() throws Exception {
        mvc.perform(post("/api/v1/llamadas").contentType(MediaType.APPLICATION_JSON).content(cuerpoInicio(nuevoCallId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("API key inválida"));
        mvc.perform(get("/api/v1/llamadas/" + nuevoCallId()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /api/v1/llamadas/{callId}/eventos ----------

    @Test
    @DisplayName("POST /llamadas/{callId}/eventos registra herramienta, argumentos, respuesta y timestamp")
    void registrarEvento() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/llamadas", cuerpoInicio(callId))).andExpect(status().isCreated());

        mvc.perform(postJson("/api/v1/llamadas/" + callId + "/eventos",
                        """
                        {"herramienta":"verificar_identidad",
                         "argumentos":{"documento":"45879632","anioNacimiento":1985},
                         "respuesta":{"verificado":true,"nombre":"Carlos","intentosRestantes":3},
                         "timestamp":"2026-10-14T10:30:25"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventoId").isNumber())
                .andExpect(jsonPath("$.mensaje").value("Evento registrado."));

        // Respuesta no-objeto (lista) con timestamp posterior
        mvc.perform(postJson("/api/v1/llamadas/" + callId + "/eventos",
                        """
                        {"herramienta":"consultar_deudas","argumentos":{"documento":"45879632"},
                         "respuesta":[{"deudaId":1}],"timestamp":"2026-10-14T10:31:00"}
                        """))
                .andExpect(status().isCreated());

        // Sin timestamp: usa la hora del servidor
        mvc.perform(postJson("/api/v1/llamadas/" + callId + "/eventos",
                        """
                        {"herramienta":"finalizar_llamada"}
                        """))
                .andExpect(status().isCreated());

        mvc.perform(getJson("/api/v1/llamadas/" + callId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos", hasSize(3)))
                .andExpect(jsonPath("$.eventos[?(@.herramienta == 'verificar_identidad')].argumentos.anioNacimiento", contains(1985)))
                .andExpect(jsonPath("$.eventos[?(@.herramienta == 'verificar_identidad')].respuesta.verificado", contains(true)))
                .andExpect(jsonPath("$.eventos[?(@.herramienta == 'verificar_identidad')].timestamp", contains(startsWith("2026-10-14T10:30:25"))))
                .andExpect(jsonPath("$.eventos[?(@.herramienta == 'consultar_deudas')].respuesta[0].deudaId", contains(1)))
                .andExpect(jsonPath("$.eventos[?(@.herramienta == 'finalizar_llamada')].argumentos", contains(nullValue())))
                .andExpect(jsonPath("$.eventos[?(@.herramienta == 'finalizar_llamada')].timestamp", contains(notNullValue())))
                // Orden cronológico por timestamp: verificar_identidad (10:30:25) antes que consultar_deudas (10:31:00)
                .andExpect(jsonPath("$.eventos[*].herramienta", containsInRelativeOrder("verificar_identidad", "consultar_deudas")));
    }

    @Test
    @DisplayName("POST /llamadas/{callId}/eventos sobre una llamada no registrada responde 404")
    void eventoDeLlamadaInexistente() throws Exception {
        mvc.perform(postJson("/api/v1/llamadas/" + nuevoCallId() + "/eventos",
                        """
                        {"herramienta":"verificar_identidad","argumentos":{},"respuesta":{}}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Llamada no encontrada"));
    }

    @Test
    @DisplayName("POST /llamadas/{callId}/eventos sin herramienta responde 400")
    void eventoSinHerramienta() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/llamadas", cuerpoInicio(callId))).andExpect(status().isCreated());
        mvc.perform(postJson("/api/v1/llamadas/" + callId + "/eventos", "{\"argumentos\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("herramienta")));
    }

    // ---------- POST /api/v1/llamadas/{callId}/resultado ----------

    @Test
    @DisplayName("Compatibilidad: el cuerpo original del resultado sigue funcionando sin llamada registrada")
    void resultadoCuerpoOriginal() throws Exception {
        mvc.perform(postJson("/api/v1/llamadas/" + nuevoCallId() + "/resultado",
                        """
                        {"documento":"45879632","tipificacion":"RELLAMADA","resumen":"Pidió que lo llamen el jueves."}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultadoId").isNumber())
                .andExpect(jsonPath("$.mensaje").value("Resultado registrado."));
    }

    @Test
    @DisplayName("Compatibilidad: el resultado solo con tipificación sigue funcionando")
    void resultadoSoloTipificacion() throws Exception {
        mvc.perform(postJson("/api/v1/llamadas/" + nuevoCallId() + "/resultado", "{\"tipificacion\":\"OTRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultadoId").isNumber());
    }

    @Test
    @DisplayName("Resultado ampliado: guionHash, transcripción y resumen estructurado quedan en la llamada y la cierran")
    void resultadoAmpliado() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/llamadas", cuerpoInicio(callId))).andExpect(status().isCreated());

        mvc.perform(getJson("/api/v1/llamadas/" + callId))
                .andExpect(jsonPath("$.fin").value(nullValue()))
                .andExpect(jsonPath("$.resultado").value(nullValue()));

        String transcripcion = "Agente: Buenas tardes, le saluda Lucía de Finanty.\\nCliente: Sí, soy yo. ".repeat(60);
        mvc.perform(postJson("/api/v1/llamadas/" + callId + "/resultado",
                        """
                        {"documento":"45879632","tipificacion":"CONTACTO_VALIDADO",
                         "resumen":"El cliente confirmó su identidad.",
                         "guionHash":"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                         "transcripcion":"%s",
                         "resumenEstructurado":{"identidadValidada":true,"telefonoCorrecto":true,"duracionSeg":58}}
                        """.formatted(transcripcion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultadoId").isNumber());

        mvc.perform(getJson("/api/v1/llamadas/" + callId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fin").isNotEmpty())
                .andExpect(jsonPath("$.resultado.tipificacion").value("CONTACTO_VALIDADO"))
                .andExpect(jsonPath("$.resultado.resumen").value("El cliente confirmó su identidad."))
                .andExpect(jsonPath("$.resultado.guionHash").value(startsWith("9f86d081")))
                .andExpect(jsonPath("$.resultado.transcripcion").value(startsWith("Agente: Buenas tardes")))
                .andExpect(jsonPath("$.resultado.transcripcion", hasLength(greaterThan(2000))))
                .andExpect(jsonPath("$.resultado.resumenEstructurado.identidadValidada").value(true))
                .andExpect(jsonPath("$.resultado.resumenEstructurado.duracionSeg").value(58))
                .andExpect(jsonPath("$.resultado.registradoEn").isNotEmpty());
    }

    @ParameterizedTest(name = "tipificación {0} aceptada")
    @EnumSource(ResultadoLlamada.Tipificacion.class)
    @DisplayName("Las doce tipificaciones del catálogo se aceptan en el resultado")
    void catalogoDeTipificaciones(ResultadoLlamada.Tipificacion tipificacion) throws Exception {
        mvc.perform(postJson("/api/v1/llamadas/" + nuevoCallId() + "/resultado",
                        "{\"tipificacion\":\"" + tipificacion.name() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una tipificación fuera del catálogo responde 400 con mensaje")
    void tipificacionDesconocida() throws Exception {
        mvc.perform(postJson("/api/v1/llamadas/" + nuevoCallId() + "/resultado", "{\"tipificacion\":\"INVENTADA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(startsWith("Cuerpo inválido")));
    }

    // ---------- GET /api/v1/llamadas/{callId} ----------

    @Test
    @DisplayName("GET /llamadas/{callId} devuelve los datos del inicio")
    void obtenerLlamada() throws Exception {
        String callId = nuevoCallId();
        mvc.perform(postJson("/api/v1/llamadas", cuerpoInicio(callId))).andExpect(status().isCreated());
        mvc.perform(getJson("/api/v1/llamadas/" + callId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").value(callId))
                .andExpect(jsonPath("$.documento").value("45879632"))
                .andExpect(jsonPath("$.telefono").value("51987654321"))
                .andExpect(jsonPath("$.perfil").value("validador"))
                .andExpect(jsonPath("$.campana").value("VALIDACION-OCT-2026"))
                .andExpect(jsonPath("$.inicio").isNotEmpty())
                .andExpect(jsonPath("$.eventos", hasSize(0)));
    }

    @Test
    @DisplayName("GET /llamadas/{callId} inexistente responde 404")
    void obtenerInexistente() throws Exception {
        mvc.perform(getJson("/api/v1/llamadas/" + nuevoCallId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Llamada no encontrada"));
    }
}
