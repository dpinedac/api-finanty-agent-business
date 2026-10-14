package pe.cobranza.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de las pruebas de endpoints: contexto completo con H2 en memoria y datos sintéticos,
 * MockMvc y la API key de prueba inyectada por propiedad (nunca una clave real).
 */
@SpringBootTest
@AutoConfigureMockMvc
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
}
