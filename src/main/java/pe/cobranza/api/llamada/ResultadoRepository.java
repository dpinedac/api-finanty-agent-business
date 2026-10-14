package pe.cobranza.api.llamada;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResultadoRepository extends JpaRepository<ResultadoLlamada, Long> {
    /** Último resultado registrado para la llamada (el endpoint de resultado no impide repetirlo). */
    Optional<ResultadoLlamada> findFirstByCallIdOrderByIdDesc(String callId);
}
