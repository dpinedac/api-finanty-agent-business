package pe.cobranza.api.telefono;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.cobranza.api.cntsec.ClienteCntsec;
import pe.cobranza.api.cntsec.ClienteCntsecRepositorio;
import pe.cobranza.api.cntsec.ValidacionLlamadaCntsecRepositorio;
import pe.cobranza.api.config.ErrorHandler.NegocioException;

import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/telefonos")
public class TelefonoController {

    private final ValidacionLlamadaCntsecRepositorio validaciones;
    private final ClienteCntsecRepositorio clientes;

    public TelefonoController(ValidacionLlamadaCntsecRepositorio validaciones, ClienteCntsecRepositorio clientes) {
        this.validaciones = validaciones;
        this.clientes = clientes;
    }

    /**
     * inicioLlamada es el momento en que contestaron y finLlamada el momento en que colgaron (ISO-8601, hora local).
     * Si falta inicioLlamada se usa la hora del servidor. La duración se calcula entre ambos salvo que se envíe
     * duracionSegundos; con duracionSegundos y sin finLlamada, el fin se deduce.
     */
    public record ValidacionRequest(@NotBlank @Size(max = 15) String documento,
                                    @NotBlank @Size(max = 20) String telefono,
                                    @NotNull ValidacionTelefono.Resultado resultado,
                                    @NotBlank @Size(max = 64) String callId,
                                    LocalDateTime inicioLlamada,
                                    LocalDateTime finLlamada,
                                    @PositiveOrZero Integer duracionSegundos,
                                    @Size(max = 2000) String resumen) {}

    public record ValidacionResponse(Long validacionId, ValidacionTelefono.Resultado resultado, String mensaje,
                                     boolean esCliente, Integer duracionSegundos) {}

    /**
     * El validador registra a quién pertenece el número marcado y cómo fue la llamada en la tabla AGENTEIATT01
     * de CNTSEC. No modifica los teléfonos del cliente. Un callId solo se registra una vez (409 si se repite).
     */
    @PostMapping("/validacion")
    @ResponseStatus(HttpStatus.CREATED)
    public ValidacionResponse registrar(@Valid @RequestBody ValidacionRequest req) {
        LocalDateTime inicio = req.inicioLlamada() == null ? LocalDateTime.now() : req.inicioLlamada();
        LocalDateTime fin = req.finLlamada();
        Integer duracion = req.duracionSegundos();
        if (fin != null && fin.isBefore(inicio)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "finLlamada no puede ser anterior a inicioLlamada");
        }
        if (duracion == null && fin != null) {
            duracion = (int) Duration.between(inicio, fin).toSeconds();
        } else if (duracion != null && fin == null) {
            fin = inicio.plusSeconds(duracion);
        }
        var v = new ValidacionTelefono(req.documento().trim(), req.telefono().trim(), req.resultado(), req.callId().trim(),
                inicio, fin, duracion, req.resumen());
        Long clienteId = clientes.porDocumento(v.documento()).map(ClienteCntsec::clienteId).orElse(null);
        try {
            long id = validaciones.insertar(v, clienteId);
            return new ValidacionResponse(id, v.resultado(), "Validación registrada.", v.esCliente(), v.duracionSegundos());
        } catch (DuplicateKeyException e) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe una validación para ese callId");
        }
    }
}
