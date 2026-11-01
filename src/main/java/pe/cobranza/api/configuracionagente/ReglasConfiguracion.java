package pe.cobranza.api.configuracionagente;

import org.springframework.http.HttpStatus;
import pe.cobranza.api.config.ErrorHandler.NegocioException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Reglas del depósito de configuración: forma del ámbito, nombres de archivo permitidos y tamaño máximo. */
final class ReglasConfiguracion {

    static final Pattern AMBITO = Pattern.compile("^[a-z][a-z0-9_-]{0,31}$");
    static final List<String> NOMBRES_PERMITIDOS = List.of("prompt.md", "guion.json", "config.yaml", "global.yaml");
    static final int MAX_BYTES_ARCHIVO = 256 * 1024;

    private ReglasConfiguracion() {}

    static String validarAmbito(String ambito) {
        if (ambito == null || !AMBITO.matcher(ambito).matches()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Ámbito inválido: '" + ambito + "'. Debe cumplir " + AMBITO.pattern());
        }
        return ambito;
    }

    /** Devuelve una copia ordenada del mapa tras comprobar que no está vacío y que cada archivo es admisible. */
    static Map<String, String> validarArchivos(Map<String, String> archivos) {
        if (archivos == null || archivos.isEmpty()) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "archivos no puede estar vacío");
        }
        var copia = new LinkedHashMap<String, String>();
        for (var entrada : archivos.entrySet()) {
            String nombre = entrada.getKey();
            if (!NOMBRES_PERMITIDOS.contains(nombre)) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "Nombre de archivo no permitido: " + nombre
                        + ". Permitidos: " + String.join(", ", NOMBRES_PERMITIDOS));
            }
            String contenido = entrada.getValue();
            if (contenido == null) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "El contenido de " + nombre + " no puede ser null");
            }
            if (contenido.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_ARCHIVO) {
                throw new NegocioException(HttpStatus.BAD_REQUEST, "El contenido de " + nombre
                        + " supera el máximo de " + (MAX_BYTES_ARCHIVO / 1024) + " KB");
            }
            copia.put(nombre, contenido);
        }
        return copia;
    }
}
