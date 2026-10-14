package pe.cobranza.api.telefono;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidacionTelefonoRepository extends JpaRepository<ValidacionTelefono, Long> {
    List<ValidacionTelefono> findByCallId(String callId);
}
