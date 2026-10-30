package posfiscalnfce.sale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    Optional<Sale> findByIdAndStoreId(UUID id, UUID storeId);

    List<Sale> findByStoreIdAndStatusAndCreatedAtBetween(UUID storeId, Sale.Status status, Instant start, Instant end);
}
