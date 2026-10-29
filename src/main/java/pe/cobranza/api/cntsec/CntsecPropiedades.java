package pe.cobranza.api.cntsec;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión y catálogo de CNTSEC (core CobraTwo). Todo se configura en application.yml y por variables de entorno;
 * los ids del catálogo COBTG01 nunca van fijos en el código.
 */
@ConfigurationProperties(prefix = "cntsec")
public record CntsecPropiedades(Datasource datasource, Catalogo catalogo) {

    public record Datasource(String host, String url, String username, String password, String initScript,
                             int maxPoolSize) {}

    /** Ids de COBTG01 (TM_GENERALCB). */
    public record Catalogo(int deudaVigente, int negociacionVigente, int clienteFallecido) {}
}
