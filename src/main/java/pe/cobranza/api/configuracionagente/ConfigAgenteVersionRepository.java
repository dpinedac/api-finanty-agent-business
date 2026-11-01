package pe.cobranza.api.configuracionagente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConfigAgenteVersionRepository extends JpaRepository<ConfigAgenteVersion, Long> {

    /** La versión vigente del ámbito: la de mayor número. */
    Optional<ConfigAgenteVersion> findFirstByAmbitoOrderByNumeroDesc(String ambito);

    Optional<ConfigAgenteVersion> findByAmbitoAndNumero(String ambito, int numero);

    /** Historial del ámbito, de la más reciente a la más antigua (los archivos no se cargan). */
    List<ConfigAgenteVersion> findByAmbitoOrderByNumeroDesc(String ambito);

    @Query("select max(v.numero) from ConfigAgenteVersion v where v.ambito = :ambito")
    Optional<Integer> maxNumero(@Param("ambito") String ambito);

    /** Una fila por ámbito: su versión vigente. */
    @Query("select v from ConfigAgenteVersion v where v.numero = "
            + "(select max(v2.numero) from ConfigAgenteVersion v2 where v2.ambito = v.ambito) order by v.ambito")
    List<ConfigAgenteVersion> vigentes();
}
