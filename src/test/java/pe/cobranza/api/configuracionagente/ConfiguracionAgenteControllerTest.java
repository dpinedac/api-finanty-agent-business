package pe.cobranza.api.configuracionagente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.cobranza.api.PruebaApi;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Depósito versionado de configuración del agente: contexto completo con H2, un ámbito distinto por prueba. */
class ConfiguracionAgenteControllerTest extends PruebaApi {

    private static final String BASE = "/api/v1/configuracion-agente";

    /** Ámbito único por prueba (el contexto Spring y la H2 se comparten entre pruebas). */
    private static String nuevoAmbito() {
        return "p-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String cuerpo(String autor, String comentario, String archivosJson) {
        return """
                {"archivos":%s,"autor":"%s","comentario":"%s"}
                """.formatted(archivosJson, autor, comentario);
    }

    private static final String ARCHIVOS_V1 = """
            {"prompt.md":"Eres Lucía, asesora de Finanty.\\n","guion.json":"{\\"saludo\\":\\"Buenos días\\"}","config.yaml":"llm:\\n  modelo: pequeno\\n"}
            """;
    private static final String ARCHIVOS_V2 = """
            {"prompt.md":"Eres Lucía, asesora de Finanty. Sé breve.\\n","config.yaml":"llm:\\n  modelo: mini\\n"}
            """;

    private void publicar(String ambito, String comentario, String archivosJson, int numeroEsperado) throws Exception {
        mvc.perform(postJson(BASE + "/" + ambito + "/publicar", cuerpo("dpineda", comentario, archivosJson)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ambito").value(ambito))
                .andExpect(jsonPath("$.numero").value(numeroEsperado))
                .andExpect(jsonPath("$.fecha").isNotEmpty());
    }

    // ---------- Publicar y leer publicado ----------

    @Test
    @DisplayName("Publicar crea la versión 1 y luego la 2; el publicado es la última y conserva los archivos tal cual")
    void publicarCreaVersionesConsecutivas() throws Exception {
        String ambito = nuevoAmbito();

        mvc.perform(getJson(BASE + "/" + ambito))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("El ámbito no tiene configuración publicada"));

        publicar(ambito, "Primera versión", ARCHIVOS_V1, 1);
        publicar(ambito, "Prompt más breve", ARCHIVOS_V2, 2);

        mvc.perform(getJson(BASE + "/" + ambito))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ambito").value(ambito))
                .andExpect(jsonPath("$.numero").value(2))
                .andExpect(jsonPath("$.autor").value("dpineda"))
                .andExpect(jsonPath("$.comentario").value("Prompt más breve"))
                .andExpect(jsonPath("$.fecha").isNotEmpty())
                .andExpect(jsonPath("$.archivos.length()").value(2))
                .andExpect(jsonPath("$.archivos['prompt.md']").value("Eres Lucía, asesora de Finanty. Sé breve.\n"))
                .andExpect(jsonPath("$.archivos['config.yaml']").value("llm:\n  modelo: mini\n"))
                .andExpect(jsonPath("$.archivos['guion.json']").doesNotExist());
    }

    @Test
    @DisplayName("Las versiones son inmutables: la 1 sigue intacta tras publicar la 2 y el historial va de mayor a menor")
    void versionesInmutablesYOrdenadas() throws Exception {
        String ambito = nuevoAmbito();
        publicar(ambito, "Primera versión", ARCHIVOS_V1, 1);
        publicar(ambito, "Segunda", ARCHIVOS_V2, 2);

        mvc.perform(getJson(BASE + "/" + ambito + "/versiones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].numero").value(2))
                .andExpect(jsonPath("$[0].comentario").value("Segunda"))
                .andExpect(jsonPath("$[1].numero").value(1))
                .andExpect(jsonPath("$[1].autor").value("dpineda"))
                .andExpect(jsonPath("$[1].fecha").isNotEmpty())
                .andExpect(jsonPath("$[*].archivos").doesNotExist());

        mvc.perform(getJson(BASE + "/" + ambito + "/versiones/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.archivos.length()").value(3))
                .andExpect(jsonPath("$.archivos['guion.json']").value("{\"saludo\":\"Buenos días\"}"))
                .andExpect(jsonPath("$.archivos['config.yaml']").value("llm:\n  modelo: pequeno\n"));

        mvc.perform(getJson(BASE + "/" + ambito + "/versiones/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Versión no encontrada"));
        mvc.perform(getJson(BASE + "/" + ambito + "/versiones/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("numero")));
    }

    @Test
    @DisplayName("Ámbito sin versiones: historial vacío y 404 en versión concreta")
    void ambitoSinVersiones() throws Exception {
        String ambito = nuevoAmbito();
        mvc.perform(getJson(BASE + "/" + ambito + "/versiones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(getJson(BASE + "/" + ambito + "/versiones/1"))
                .andExpect(status().isNotFound());
    }

    // ---------- Borrador ----------

    @Test
    @DisplayName("Borrador: 404 si no hay, PUT lo crea, un segundo PUT lo reemplaza por completo y DELETE es idempotente")
    void borradorCrearReemplazarEliminar() throws Exception {
        String ambito = nuevoAmbito();

        mvc.perform(getJson(BASE + "/" + ambito + "/borrador"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("El ámbito no tiene borrador"));

        mvc.perform(putJson(BASE + "/" + ambito + "/borrador", cuerpo("dpineda", "Probando", ARCHIVOS_V1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ambito").value(ambito))
                .andExpect(jsonPath("$.fecha").isNotEmpty());

        mvc.perform(getJson(BASE + "/" + ambito + "/borrador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autor").value("dpineda"))
                .andExpect(jsonPath("$.comentario").value("Probando"))
                .andExpect(jsonPath("$.archivos.length()").value(3))
                .andExpect(jsonPath("$.archivos['prompt.md']").value(startsWith("Eres Lucía")));

        // Reemplazo completo: desaparece guion.json y cambia el autor
        mvc.perform(putJson(BASE + "/" + ambito + "/borrador", cuerpo("otro", "Segundo intento", ARCHIVOS_V2)))
                .andExpect(status().isOk());
        mvc.perform(getJson(BASE + "/" + ambito + "/borrador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autor").value("otro"))
                .andExpect(jsonPath("$.comentario").value("Segundo intento"))
                .andExpect(jsonPath("$.archivos.length()").value(2))
                .andExpect(jsonPath("$.archivos['guion.json']").doesNotExist())
                .andExpect(jsonPath("$.archivos['config.yaml']").value("llm:\n  modelo: mini\n"));

        mvc.perform(deleteJson(BASE + "/" + ambito + "/borrador")).andExpect(status().isNoContent());
        mvc.perform(getJson(BASE + "/" + ambito + "/borrador")).andExpect(status().isNotFound());
        // Idempotente
        mvc.perform(deleteJson(BASE + "/" + ambito + "/borrador")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Publicar elimina el borrador del ámbito y no toca el de otros ámbitos")
    void publicarBorraElBorrador() throws Exception {
        String ambito = nuevoAmbito();
        String otro = nuevoAmbito();
        mvc.perform(putJson(BASE + "/" + ambito + "/borrador", cuerpo("dpineda", "Trabajo en curso", ARCHIVOS_V1)))
                .andExpect(status().isOk());
        mvc.perform(putJson(BASE + "/" + otro + "/borrador", cuerpo("dpineda", "Otro ámbito", ARCHIVOS_V1)))
                .andExpect(status().isOk());

        publicar(ambito, "Publico lo del borrador", ARCHIVOS_V1, 1);

        mvc.perform(getJson(BASE + "/" + ambito + "/borrador")).andExpect(status().isNotFound());
        mvc.perform(getJson(BASE + "/" + otro + "/borrador")).andExpect(status().isOk());
        mvc.perform(getJson(BASE + "/" + ambito)).andExpect(status().isOk()).andExpect(jsonPath("$.numero").value(1));
    }

    // ---------- Listado de ámbitos ----------

    @Test
    @DisplayName("El listado de ámbitos refleja versión vigente, borrador y fecha de publicación")
    void listarAmbitos() throws Exception {
        String publicado = nuevoAmbito();
        String conAmbos = nuevoAmbito();
        String soloBorrador = nuevoAmbito();
        publicar(publicado, "v1", ARCHIVOS_V1, 1);
        publicar(conAmbos, "v1", ARCHIVOS_V1, 1);
        publicar(conAmbos, "v2", ARCHIVOS_V2, 2);
        mvc.perform(putJson(BASE + "/" + conAmbos + "/borrador", cuerpo("dpineda", "wip", ARCHIVOS_V1))).andExpect(status().isOk());
        mvc.perform(putJson(BASE + "/" + soloBorrador + "/borrador", cuerpo("dpineda", "wip", ARCHIVOS_V1))).andExpect(status().isOk());

        String filtro = "$[?(@.ambito == '%s')]";
        mvc.perform(getJson(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(filtro.formatted(publicado), hasSize(1)))
                .andExpect(jsonPath(filtro.formatted(publicado) + ".versionVigente", contains(1)))
                .andExpect(jsonPath(filtro.formatted(publicado) + ".hayBorrador", contains(false)))
                .andExpect(jsonPath(filtro.formatted(publicado) + ".publicadoEn", contains(notNullValue())))
                .andExpect(jsonPath(filtro.formatted(conAmbos) + ".versionVigente", contains(2)))
                .andExpect(jsonPath(filtro.formatted(conAmbos) + ".hayBorrador", contains(true)))
                .andExpect(jsonPath(filtro.formatted(soloBorrador), hasSize(1)))
                .andExpect(jsonPath(filtro.formatted(soloBorrador) + ".versionVigente", contains(nullValue())))
                .andExpect(jsonPath(filtro.formatted(soloBorrador) + ".hayBorrador", contains(true)))
                .andExpect(jsonPath(filtro.formatted(soloBorrador) + ".publicadoEn", contains(nullValue())));
    }

    // ---------- 400 ----------

    @Test
    @DisplayName("Publicar o guardar borrador con archivos vacío, nombre no permitido, contenido nulo o autor en blanco responde 400")
    void archivosInvalidos() throws Exception {
        String ambito = nuevoAmbito();

        mvc.perform(postJson(BASE + "/" + ambito + "/publicar", cuerpo("dpineda", "x", "{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("archivos no puede estar vacío"));
        mvc.perform(postJson(BASE + "/" + ambito + "/publicar", "{\"autor\":\"dpineda\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("archivos no puede estar vacío"));

        mvc.perform(postJson(BASE + "/" + ambito + "/publicar", cuerpo("dpineda", "x", "{\"notas.txt\":\"hola\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(startsWith("Nombre de archivo no permitido: notas.txt")));

        mvc.perform(putJson(BASE + "/" + ambito + "/borrador", cuerpo("dpineda", "x", "{\"prompt.md\":null}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("null")));

        mvc.perform(postJson(BASE + "/" + ambito + "/publicar", cuerpo("  ", "x", ARCHIVOS_V1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("autor")));

        // Nada quedó registrado
        mvc.perform(getJson(BASE + "/" + ambito)).andExpect(status().isNotFound());
        mvc.perform(getJson(BASE + "/" + ambito + "/borrador")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un archivo de más de 256 KB responde 400; uno de 256 KB exactos se acepta")
    void contenidoDemasiadoGrande() throws Exception {
        String ambito = nuevoAmbito();
        String justo = "a".repeat(256 * 1024);
        String excedido = justo + "a";

        mvc.perform(postJson(BASE + "/" + ambito + "/publicar", cuerpo("dpineda", "grande", "{\"prompt.md\":\"" + excedido + "\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("256 KB")));
        mvc.perform(postJson(BASE + "/" + ambito + "/publicar", cuerpo("dpineda", "justo", "{\"prompt.md\":\"" + justo + "\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1));
        mvc.perform(getJson(BASE + "/" + ambito))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivos['prompt.md']", hasLength(256 * 1024)));
    }

    @Test
    @DisplayName("Un ámbito que no cumple ^[a-z][a-z0-9_-]{0,31}$ responde 400 en todas las rutas")
    void ambitoInvalido() throws Exception {
        for (String malo : new String[]{"Validador", "1abc", "a".repeat(33), "con%20espacio", "ñandu"}) {
            mvc.perform(getJson(BASE + "/" + malo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(startsWith("Ámbito inválido")));
            mvc.perform(postJson(BASE + "/" + malo + "/publicar", cuerpo("dpineda", "x", ARCHIVOS_V1)))
                    .andExpect(status().isBadRequest());
            mvc.perform(putJson(BASE + "/" + malo + "/borrador", cuerpo("dpineda", "x", ARCHIVOS_V1)))
                    .andExpect(status().isBadRequest());
            mvc.perform(deleteJson(BASE + "/" + malo + "/borrador")).andExpect(status().isBadRequest());
            mvc.perform(getJson(BASE + "/" + malo + "/borrador")).andExpect(status().isBadRequest());
            mvc.perform(getJson(BASE + "/" + malo + "/versiones")).andExpect(status().isBadRequest());
            mvc.perform(getJson(BASE + "/" + malo + "/versiones/1")).andExpect(status().isBadRequest());
        }
        // Límite válido: 32 caracteres con dígitos, guion y guion bajo
        mvc.perform(getJson(BASE + "/" + "a1_-" + "b".repeat(28) + "/versiones"))
                .andExpect(status().isOk());
    }

    // ---------- 401 ----------

    @Test
    @DisplayName("Sin X-API-Key todas las rutas responden 401")
    void sinApiKey() throws Exception {
        String ambito = nuevoAmbito();
        String json = cuerpo("dpineda", "x", ARCHIVOS_V1);
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("API key inválida"));
        mvc.perform(get(BASE + "/" + ambito)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/" + ambito + "/publicar").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(BASE + "/" + ambito + "/borrador").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE + "/" + ambito + "/borrador")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + ambito + "/versiones")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + ambito + "/versiones/1")).andExpect(status().isUnauthorized());
        // Y con clave incorrecta
        mvc.perform(get(BASE).header("X-API-Key", "otra")).andExpect(status().isUnauthorized());
    }
}
