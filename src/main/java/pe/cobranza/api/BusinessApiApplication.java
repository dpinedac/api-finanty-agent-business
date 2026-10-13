package pe.cobranza.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BusinessApiApplication {
    public static void main(String[] args) {
        // Fechas de promesas y rellamadas en hora de Perú
        TimeZone.setDefault(TimeZone.getTimeZone(System.getenv().getOrDefault("ZONA_HORARIA", "America/Lima")));
        SpringApplication.run(BusinessApiApplication.class, args);
    }
}
