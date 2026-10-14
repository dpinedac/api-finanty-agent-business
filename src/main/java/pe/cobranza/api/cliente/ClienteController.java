package pe.cobranza.api.cliente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    /** Intentos máximos de validación por llamada; se configura con COBRANZA_MAX_INTENTOS. */
    private final int maxIntentos;
    private final ClienteRepository repo;
    /** Intentos fallidos por llamada. En producción iría a Redis. */
    private final Map<String, Integer> intentos = new ConcurrentHashMap<>();

    public ClienteController(ClienteRepository repo,
                             @Value("${cobranza.validacion.max-intentos:3}") int maxIntentos) {
        this.repo = repo;
        this.maxIntentos = maxIntentos;
    }

    public record VerificarRequest(@NotBlank String documento, @NotNull Integer anioNacimiento, String callId) {}
    public record VerificarResponse(boolean verificado, String nombre, int intentosRestantes, String mensaje) {}

    /** Precarga para el discador: datos mínimos para saludar (sin revelar deuda). */
    public record ClienteResumen(String documento, String nombre) {}

    @GetMapping("/{documento}")
    public ClienteResumen resumen(@PathVariable String documento) {
        return repo.findByDocumento(documento)
                .map(c -> new ClienteResumen(c.getDocumento(), c.getNombres() + " " + c.getApellidos()))
                .orElseThrow(() -> new pe.cobranza.api.config.ErrorHandler.NegocioException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Cliente no encontrado"));
    }

    @PostMapping("/verificar")
    public VerificarResponse verificar(@Valid @RequestBody VerificarRequest req) {
        String clave = req.callId() == null ? req.documento() : req.callId();
        int usados = intentos.getOrDefault(clave, 0);
        if (usados >= maxIntentos) {
            return new VerificarResponse(false, null, 0, "Se superó el número de intentos de validación.");
        }
        var cliente = repo.findByDocumento(req.documento().trim());
        boolean ok = cliente.isPresent() && req.anioNacimiento().equals(cliente.get().getAnioNacimiento());
        if (ok) {
            intentos.remove(clave);
            return new VerificarResponse(true, cliente.get().getNombres(), maxIntentos, "Identidad validada.");
        }
        intentos.put(clave, usados + 1);
        return new VerificarResponse(false, null, maxIntentos - usados - 1, "Los datos no coinciden.");
    }
}
