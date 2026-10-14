package pe.cobranza.api.llamada;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LlamadaRepository extends JpaRepository<Llamada, Long> {
    Optional<Llamada> findByCallId(String callId);
    boolean existsByCallId(String callId);
}
