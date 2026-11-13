package pe.cobranza.api;

import jakarta.persistence.Entity;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.SQLServerDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * specs/sql/esquema_propio.sql es la única forma de crear la BD propia en SQL Server (perfil sqlserver,
 * ddl-auto: none). Esta prueba lo ejecuta en H2 en modo de compatibilidad SQL Server y luego hace que Hibernate,
 * con el dialecto de SQL Server, valide el esquema contra las entidades JPA (lo mismo que ddl-auto: validate).
 * Además compara columna a columna cada tabla del script con su entidad, para que no falte ni sobre ninguna.
 *
 * Diferencia de sintaxis que se salta de forma explícita y acotada (documentada en la cabecera del script):
 * las guardas de idempotencia T-SQL (`IF OBJECT_ID(...) IS NULL`, `BEGIN`, `END;`), que H2 no entiende.
 * Todo lo demás se ejecuta tal cual.
 *
 * El dominio configuracionagente (y su script config_agente.sql) es OBSOLETO: agent-voice tendrá su propio
 * PostgreSQL y se retirará. Queda fuera del esquema de desarrollo y de esta validación.
 */
class EsquemaPropioSqlServerTest {

    private static final Path SCRIPT = Path.of("specs/sql/esquema_propio.sql");
    private static final String PAQUETE_OBSOLETO = "pe.cobranza.api.configuracionagente";

    /** H2 en modo SQL Server; el script usa el esquema dbo, que se crea antes de ejecutarlo. */
    private static final String URL = "jdbc:h2:mem:esquema_propio;MODE=MSSQLServer;DB_CLOSE_DELAY=-1";
    private static final String URL_DBO = URL + ";SCHEMA=dbo";

    private static final Pattern GUARDA = Pattern.compile("^(IF OBJECT_ID\\(.*\\) IS NULL|BEGIN|END;?)$", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("esquema_propio.sql crea en H2 (MODE=MSSQLServer) un esquema que Hibernate valida contra las entidades, columna a columna")
    void hibernateValidaElEsquemaCreadoPorElScript() throws Exception {
        try (Connection con = DriverManager.getConnection(URL, "sa", "")) {
            try (var st = con.createStatement()) {
                st.execute("CREATE SCHEMA IF NOT EXISTS dbo");
                st.execute("SET SCHEMA dbo");
            }
            for (String sentencia : sentenciasEjecutables(SCRIPT)) {
                try (var st = con.createStatement()) {
                    st.execute(sentencia);
                } catch (SQLException e) {
                    throw new AssertionError("H2 (MODE=MSSQLServer) rechazó una sentencia de " + SCRIPT
                            + ":\n" + sentencia + "\n" + e.getMessage(), e);
                }
            }

            StandardServiceRegistry registro = new StandardServiceRegistryBuilder()
                    .applySetting(AvailableSettings.URL, URL_DBO)
                    .applySetting(AvailableSettings.USER, "sa")
                    .applySetting(AvailableSettings.PASS, "")
                    // Instancia, no nombre de clase: así Hibernate no consulta sys.databases (que H2 no tiene).
                    .applySetting(AvailableSettings.DIALECT, new SQLServerDialect())
                    // H2 reporta sus tablas como "BASE TABLE"; el dialecto de SQL Server solo busca TABLE y VIEW.
                    .applySetting(AvailableSettings.EXTRA_PHYSICAL_TABLE_TYPES, "BASE TABLE")
                    // Equivalente a spring.jpa.hibernate.ddl-auto=validate: tablas, columnas y tipos compatibles.
                    .applySetting(AvailableSettings.HBM2DDL_AUTO, "validate")
                    // Las mismas estrategias de nombres que aplica Spring Boot (callId -> call_id).
                    .applySetting(AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                    .applySetting(AvailableSettings.IMPLICIT_NAMING_STRATEGY,
                            "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                    .build();
            try {
                var fuentes = new MetadataSources(registro);
                entidadesDelNucleo().forEach(fuentes::addAnnotatedClass);
                Metadata metadata = fuentes.buildMetadata();

                // Construir la SessionFactory ejecuta la validación; falla con SchemaManagementException si algo no cuadra.
                metadata.buildSessionFactory().close();

                // Ni falta ni sobra ninguna columna: el script debe ser exactamente lo que mapean las entidades.
                Map<String, Set<String>> enEntidades = new TreeMap<>();
                for (var espacio : metadata.getDatabase().getNamespaces()) {
                    for (var tabla : espacio.getTables()) {
                        Set<String> columnas = new TreeSet<>();
                        tabla.getColumns().forEach(c -> columnas.add(c.getName().toLowerCase()));
                        enEntidades.put(tabla.getName().toLowerCase(), columnas);
                    }
                }
                Map<String, Set<String>> enScript = columnasDelScript(SCRIPT);

                assertThat(enScript.keySet()).as("tablas del script vs. entidades").isEqualTo(enEntidades.keySet());
                enScript.forEach((tabla, columnas) ->
                        assertThat(columnas).as("columnas de " + tabla).isEqualTo(enEntidades.get(tabla)));
            } finally {
                StandardServiceRegistryBuilder.destroy(registro);
            }
        }
    }

    @Test
    @DisplayName("esquema_propio.sql cubre las ocho tablas del núcleo y no incluye las del depósito de configuración (obsoleto)")
    void esquemaPropioSoloNucleo() throws IOException {
        assertThat(columnasDelScript(SCRIPT).keySet()).containsExactly("cliente", "deuda", "evento_llamada", "llamada",
                "promesa_campana", "promesa_pago", "rellamada", "resultado_llamada");
    }

    // ---------- utilidades ----------

    /** Entidades JPA del proyecto salvo el dominio obsoleto, descubiertas por anotación: una entidad nueva sin tabla hace fallar la prueba. */
    private static List<Class<?>> entidadesDelNucleo() throws ClassNotFoundException {
        var escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> clases = new ArrayList<>();
        for (var bd : escaner.findCandidateComponents("pe.cobranza.api")) {
            if (!bd.getBeanClassName().startsWith(PAQUETE_OBSOLETO + ".")) {
                clases.add(Class.forName(bd.getBeanClassName()));
            }
        }
        assertThat(clases).hasSize(8);
        return clases;
    }

    /** Quita comentarios y las guardas T-SQL de idempotencia; devuelve las sentencias separadas por ';'. */
    private static List<String> sentenciasEjecutables(Path script) throws IOException {
        var sb = new StringBuilder();
        for (String linea : Files.readAllLines(script, StandardCharsets.UTF_8)) {
            String sinComentario = linea.contains("--") ? linea.substring(0, linea.indexOf("--")) : linea;
            String limpia = sinComentario.strip();
            if (limpia.isEmpty() || GUARDA.matcher(limpia).matches()) continue;
            sb.append(sinComentario).append('\n');
        }
        List<String> sentencias = new ArrayList<>();
        for (String s : sb.toString().split(";")) {
            if (!s.isBlank()) sentencias.add(s.strip());
        }
        return sentencias;
    }

    /** tabla -> columnas declaradas en cada CREATE TABLE del script (primer token de cada línea que no sea CONSTRAINT). */
    private static Map<String, Set<String>> columnasDelScript(Path script) throws IOException {
        Map<String, Set<String>> resultado = new TreeMap<>();
        String tablaActual = null;
        for (String linea : Files.readAllLines(script, StandardCharsets.UTF_8)) {
            String limpia = (linea.contains("--") ? linea.substring(0, linea.indexOf("--")) : linea).strip();
            if (limpia.isEmpty()) continue;
            if (limpia.toUpperCase().startsWith("CREATE TABLE ")) {
                tablaActual = limpia.substring("CREATE TABLE ".length()).replace("(", " ").split("\\s+")[0]
                        .replace("dbo.", "").toLowerCase();
                resultado.put(tablaActual, new TreeSet<>());
                continue;
            }
            if (tablaActual == null) continue;
            if (limpia.startsWith(")")) { tablaActual = null; continue; }
            String token = limpia.split("\\s+")[0];
            if (token.equalsIgnoreCase("CONSTRAINT") || token.equalsIgnoreCase("REFERENCES")) continue;
            resultado.get(tablaActual).add(token.toLowerCase());
        }
        return resultado;
    }
}
