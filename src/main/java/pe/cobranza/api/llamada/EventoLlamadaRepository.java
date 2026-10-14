package pe.cobranza.api.llamada;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoLlamadaRepository extends JpaRepository<EventoLlamada, Long> {
    List<EventoLlamada> findByCallIdOrderByOcurridoEnAscIdAsc(String callId);
}
