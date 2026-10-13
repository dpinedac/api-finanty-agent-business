package pe.cobranza.api.deuda;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeudaRepository extends JpaRepository<Deuda, Long> {
    List<Deuda> findByClienteDocumento(String documento);
}
