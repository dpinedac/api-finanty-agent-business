package pe.cobranza.api.configuracionagente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConfigAgenteBorradorRepository extends JpaRepository<ConfigAgenteBorrador, String> {

    @Query("select b.ambito from ConfigAgenteBorrador b order by b.ambito")
    List<String> ambitosConBorrador();
}
