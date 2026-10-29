package pe.cobranza.api.cntsec;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Pool propio hacia CNTSEC, separado del DataSource principal de la API (el de las tablas propias).
 * No se expone como bean DataSource para no interferir con la autoconfiguración de JPA.
 * La API solo lee las tablas de CobraTwo y escribe únicamente en su propia tabla AGENTEIATT01.
 */
@Component
public class CntsecJdbc implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CntsecJdbc.class);

    private final HikariDataSource pool;
    private final JdbcClient jdbc;

    public CntsecJdbc(CntsecPropiedades props) {
        var ds = props.datasource();
        var cfg = new HikariConfig();
        cfg.setPoolName("cntsec");
        cfg.setJdbcUrl(ds.url());
        if (ds.username() != null && !ds.username().isBlank()) cfg.setUsername(ds.username());
        if (ds.password() != null && !ds.password().isBlank()) cfg.setPassword(ds.password());
        cfg.setMaximumPoolSize(ds.maxPoolSize() > 0 ? ds.maxPoolSize() : 5);
        // No abortar el arranque si CNTSEC no responde: las consultas fallarán con error claro al usarse.
        cfg.setInitializationFailTimeout(-1);
        this.pool = new HikariDataSource(cfg);
        this.jdbc = JdbcClient.create(pool);

        if (ds.initScript() != null && !ds.initScript().isBlank()) {
            log.warn("CNTSEC simulada: ejecutando {} (solo desarrollo y pruebas)", ds.initScript());
            var populator = new ResourceDatabasePopulator(new DefaultResourceLoader().getResource(ds.initScript()));
            populator.execute(pool);
        }
        log.info("CNTSEC configurada en {}", ds.url());
    }

    public JdbcClient sql() {
        return jdbc;
    }

    @Override
    public void close() {
        pool.close();
    }
}
