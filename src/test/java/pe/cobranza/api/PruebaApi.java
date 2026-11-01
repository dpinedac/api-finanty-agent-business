package pe.cobranza.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base de las pruebas de endpoints: contexto completo con H2 en memoria y datos sintéticos,
 * MockMvc y la API key de prueba inyectada por propiedad (nunca una clave real).
 * El perfil `test` (src/test/resources/application-test.yml) fija H2 para la BD propia y para la CNTSEC simulada,
 * de modo que las pruebas nunca toquen una BD real aunque el entorno tenga DB_URL o SPRING_PROFILES_ACTIVE.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "cobranza.api-key=" + PruebaApi.API_KEY)
public abstract class PruebaApi {

    public static final String API_KEY = "clave-de-prueba";

    @Autowired
    protected MockMvc mvc;

    protected static String nuevoCallId() {
        return UUID.randomUUID().toString();
    }

    protected static MockHttpServletRequestBuilder postJson(String ruta, String cuerpo) {
        return post(ruta)
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    protected static MockHttpServletRequestBuilder getJson(String ruta) {
        return get(ruta).header("X-API-Key", API_KEY);
    }

    protected static MockHttpServletRequestBuilder putJson(String ruta, String cuerpo) {
        return put(ruta)
                .header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    protected static MockHttpServletRequestBuilder deleteJson(String ruta) {
        return delete(ruta).header("X-API-Key", API_KEY);
    }
}
