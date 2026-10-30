package posfiscalnfce.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AuditRepository extends JpaRepository<AuditEntry, UUID> {

    List<AuditEntry> findByStoreId(UUID storeId, Pageable page);
}
