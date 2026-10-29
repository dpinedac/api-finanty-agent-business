package pe.cobranza.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Cortafuegos contra un error de configuración: Hibernate solo puede crear o borrar tablas en H2.
 * Si DB_URL apunta a cualquier otra base (por ejemplo CNTSEC) con ddl-auto destructivo o siembra de datos,
 * la aplicación no arranca.
 */
@Component
public class GuardaEsquema {

    private static final Set<String> DESTRUCTIVOS = Set.of("create", "create-drop", "create-only", "update", "drop");

    private final String url;
    private final String ddlAuto;
    private final String initMode;

    public GuardaEsquema(@Value("${spring.datasource.url:}") String url,
                         @Value("${spring.jpa.hibernate.ddl-auto:none}") String ddlAuto,
                         @Value("${spring.sql.init.mode:never}") String initMode) {
        this.url = url;
        this.ddlAuto = ddlAuto;
        this.initMode = initMode;
    }

    @PostConstruct
    void verificar() {
        boolean h2 = url.startsWith("jdbc:h2:");
        if (!h2 && (DESTRUCTIVOS.contains(ddlAuto.toLowerCase()) || "always".equalsIgnoreCase(initMode))) {
            throw new IllegalStateException("Configuración peligrosa: ddl-auto=" + ddlAuto + " y sql.init.mode=" + initMode
                    + " contra " + url + ". Fuera de H2 usa JPA_DDL_AUTO=none y SQL_INIT_MODE=never (perfil sqlserver).");
        }
    }
}
