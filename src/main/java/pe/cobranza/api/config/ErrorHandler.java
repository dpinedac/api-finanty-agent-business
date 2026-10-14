package pe.cobranza.api.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class ErrorHandler {

    /** Errores de negocio: el agente los recibe como texto y los explica al cliente. */
    public static class NegocioException extends RuntimeException {
        private final HttpStatus status;
        public NegocioException(HttpStatus status, String mensaje) {
            super(mensaje);
            this.status = status;
        }
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Map<String, String>> negocio(NegocioException e) {
        return ResponseEntity.status(e.status).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacion(MethodArgumentNotValidException e) {
        var campo = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("datos inválidos");
        return ResponseEntity.badRequest().body(Map.of("error", campo));
    }

    /** Cuerpo JSON malformado o valor fuera de catálogo (por ejemplo una tipificación desconocida). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException e) {
        var causa = e.getMostSpecificCause();
        var detalle = causa == null || causa.getMessage() == null ? "cuerpo JSON inválido" : primeraLinea(causa.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", "Cuerpo inválido: " + detalle));
    }

    /** Parámetro de consulta con formato incorrecto (por ejemplo una fecha no ISO). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error",
                "Parámetro inválido: " + e.getName() + " (valor '" + e.getValue() + "')"));
    }

    private static String primeraLinea(String texto) {
        int salto = texto.indexOf('\n');
        return salto < 0 ? texto : texto.substring(0, salto);
    }
}
