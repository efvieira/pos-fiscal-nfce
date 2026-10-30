package posfiscalnfce.fiscal;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, UUID> {

    Optional<FiscalDocument> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<FiscalDocument> findBySaleId(UUID saleId);

    List<FiscalDocument> findByStatusInOrderByIssuedAt(Collection<FiscalDocument.Status> statuses, Limit limit);

    List<FiscalDocument> findByStoreIdAndStatusOrderByIssuedAt(UUID storeId, FiscalDocument.Status status);
}
