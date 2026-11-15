package co.vinni.tienda.comando;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepositorio extends JpaRepository<OutboxEvento, Long> {
    List<OutboxEvento> findTop100ByPublicadoFalseOrderByIdAsc();
}
