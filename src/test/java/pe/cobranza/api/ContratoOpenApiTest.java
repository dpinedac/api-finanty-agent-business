package pe.cobranza.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;
import pe.cobranza.api.llamada.Perfil;
import pe.cobranza.api.llamada.ResultadoLlamada;
import pe.cobranza.api.telefono.ValidacionTelefono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** El contrato se escribe primero: specs/openapi.yaml debe describir exactamente lo que expone la API. */
class ContratoOpenApiTest extends PruebaApi {

    @Autowired
    RequestMappingHandlerMapping mapeos;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> contrato() throws IOException {
        try (var in = Files.newInputStream(Path.of("specs/openapi.yaml"))) {
            return new Yaml().load(in);
        }
    }

    @Test
    @DisplayName("Cada operación expuesta por la API está en el contrato y viceversa")
    @SuppressWarnings("unchecked")
    void operacionesCoinciden() throws IOException {
        var spec = contrato();
        assertThat(spec.get("openapi")).isEqualTo("3.1.0");

        var paths = (Map<String, Map<String, Object>>) spec.get("paths");
        Set<String> enContrato = new TreeSet<>();
        paths.forEach((ruta, ops) -> ops.keySet().stream()
                .filter(m -> List.of("get", "post", "put", "patch", "delete").contains(m))
                .forEach(m -> enContrato.add(m.toUpperCase() + " " + ruta)));

        Set<String> enCodigo = new TreeSet<>();
        mapeos.getHandlerMethods().forEach((info, metodo) -> {
            if (info.getPathPatternsCondition() == null) return;
            for (var patron : info.getPathPatternsCondition().getPatternValues()) {
                if (!patron.startsWith("/api/")) continue;
                for (var m : info.getMethodsCondition().getMethods()) {
                    enCodigo.add(m.name() + " " + patron);
                }
            }
        });

        assertThat(enCodigo).containsExactlyInAnyOrderElementsOf(enContrato);
        assertThat(enCodigo).contains(
                "POST /api/v1/llamadas",
                "GET /api/v1/llamadas/{callId}",
                "POST /api/v1/llamadas/{callId}/eventos",
                "POST /api/v1/llamadas/{callId}/resultado",
                "POST /api/v1/telefonos/validacion",
                "GET /api/v1/rellamadas");
    }

    @Test
    @DisplayName("Los catálogos del contrato coinciden con los enums del código")
    @SuppressWarnings("unchecked")
    void catalogosCoinciden() throws IOException {
        var esquemas = (Map<String, Map<String, Object>>) ((Map<String, Object>) contrato().get("components")).get("schemas");

        assertThat((List<String>) esquemas.get("Tipificacion").get("enum"))
                .containsExactlyInAnyOrderElementsOf(nombres(ResultadoLlamada.Tipificacion.values()));
        assertThat((List<String>) esquemas.get("ResultadoValidacionTelefono").get("enum"))
                .containsExactlyInAnyOrderElementsOf(nombres(ValidacionTelefono.Resultado.values()));
        assertThat((List<String>) esquemas.get("Perfil").get("enum"))
                .containsExactlyInAnyOrder(Arrays.stream(Perfil.values()).map(Perfil::valor).toArray(String[]::new));
    }

    @Test
    @DisplayName("La cabecera X-API-Key está declarada como securityScheme y aplicada globalmente")
    @SuppressWarnings("unchecked")
    void seguridadDeclarada() throws IOException {
        var spec = contrato();
        var esquemas = (Map<String, Map<String, Object>>) ((Map<String, Object>) spec.get("components")).get("securitySchemes");
        assertThat(esquemas.get("ApiKey")).containsEntry("type", "apiKey").containsEntry("in", "header")
                .containsEntry("name", "X-API-Key");
        var seguridad = (List<Map<String, Object>>) spec.get("security");
        assertThat(seguridad).anySatisfy(s -> assertThat(s).containsKey("ApiKey"));
    }

    private static List<String> nombres(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).collect(Collectors.toList());
    }
}
