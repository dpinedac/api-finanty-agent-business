package pe.cobranza.api.rellamada;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RellamadaRepository extends JpaRepository<Rellamada, Long>, JpaSpecificationExecutor<Rellamada> {}
