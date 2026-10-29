package pe.cobranza.api.cliente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.cobranza.api.cntsec.ClienteCntsec;
import pe.cobranza.api.cntsec.ClienteCntsecRepositorio;
import pe.cobranza.api.config.ErrorHandler.NegocioException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clientes leídos de CNTSEC (COBTM08). Reglas que viven aquí y no en el prompt:
 * no se llama a fallecidos, ni a clientes sin deuda vigente o con negociación vigente;
 * la identidad se valida con la confirmación del interlocutor y los primeros dígitos del documento.
 */
@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    /** Intentos máximos de validación por llamada; se configura con COBRANZA_MAX_INTENTOS. */
    private final int maxIntentos;
    /** Dígitos iniciales del documento que debe decir el cliente; COBRANZA_DIGITOS_DOCUMENTO. */
    private final int digitosDocumento;
    private final ClienteCntsecRepositorio clientes;
    /** Intentos fallidos por llamada. En producción iría a Redis. */
    private final Map<String, Integer> intentos = new ConcurrentHashMap<>();

    public ClienteController(ClienteCntsecRepositorio clientes,
                             @Value("${cobranza.validacion.max-intentos:3}") int maxIntentos,
                             @Value("${cobranza.validacion.digitos-documento:4}") int digitosDocumento) {
        this.clientes = clientes;
        this.maxIntentos = maxIntentos;
        this.digitosDocumento = digitosDocumento;
    }

    /** Por qué no debe llamarse a un cliente; null cuando sí es llamable. */
    public enum MotivoNoLlamable { FALLECIDO, SIN_DEUDA_VIGENTE, NEGOCIACION_VIGENTE }

    /** Precarga para el discador y el saludo: datos mínimos, sin revelar deuda. */
    public record ClienteResumen(String documento, String nombre, String nombres, String apellidos,
                                 boolean llamable, MotivoNoLlamable motivoNoLlamable) {}

    /**
     * Verificación de identidad. Vía principal: el interlocutor confirma que es el titular
     * (confirmaIdentidad) y dice los primeros dígitos de su documento (digitosDocumento).
     * anioNacimiento se mantiene por compatibilidad con el contrato anterior.
     */
    public record VerificarRequest(@NotBlank String documento, String callId,
                                   Boolean confirmaIdentidad, String digitosDocumento,
                                   Integer anioNacimiento) {}

    public record VerificarResponse(boolean verificado, String nombre, int intentosRestantes, String mensaje) {}

    @GetMapping("/{documento}")
    public ClienteResumen resumen(@PathVariable String documento) {
        var c = buscar(documento);
        var motivo = motivoNoLlamable(c);
        return new ClienteResumen(c.documento(), c.nombreCompleto(), textoONulo(c.nombres()), textoONulo(c.apellidos()),
                motivo == null, motivo);
    }

    @PostMapping("/verificar")
    public VerificarResponse verificar(@Valid @RequestBody VerificarRequest req) {
        if (req.digitosDocumento() == null && req.anioNacimiento() == null && req.confirmaIdentidad() == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST,
                    "Envía confirmaIdentidad y digitosDocumento (o anioNacimiento).");
        }
        String clave = req.callId() == null ? req.documento() : req.callId();
        int usados = intentos.getOrDefault(clave, 0);
        if (usados >= maxIntentos) {
            return new VerificarResponse(false, null, 0, "Se superó el número de intentos de validación.");
        }
        var cliente = clientes.porDocumento(req.documento());
        boolean ok = cliente.isPresent() && coincide(cliente.get(), req);
        if (ok) {
            intentos.remove(clave);
            return new VerificarResponse(true, textoONulo(cliente.get().nombres()), maxIntentos, "Identidad validada.");
        }
        intentos.put(clave, usados + 1);
        return new VerificarResponse(false, null, maxIntentos - usados - 1, "Los datos no coinciden.");
    }

    private boolean coincide(ClienteCntsec c, VerificarRequest req) {
        if (Boolean.FALSE.equals(req.confirmaIdentidad())) return false;
        if (req.digitosDocumento() != null) {
            var digitos = req.digitosDocumento().trim();
            return Boolean.TRUE.equals(req.confirmaIdentidad())
                    && digitos.length() == digitosDocumento
                    && c.documento().startsWith(digitos);
        }
        if (req.anioNacimiento() != null) {
            return c.fechaNacimiento() != null && c.fechaNacimiento().getYear() == req.anioNacimiento();
        }
        // Solo confirmación verbal, sin dato de control: no basta.
        return false;
    }

    private MotivoNoLlamable motivoNoLlamable(ClienteCntsec c) {
        if (clientes.fallecido(c)) return MotivoNoLlamable.FALLECIDO;
        if (c.deudasVigentes() == 0) return MotivoNoLlamable.SIN_DEUDA_VIGENTE;
        if (c.negociacionesVigentes() > 0) return MotivoNoLlamable.NEGOCIACION_VIGENTE;
        return null;
    }

    private ClienteCntsec buscar(String documento) {
        return clientes.porDocumento(documento)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
    }

    private static String textoONulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
