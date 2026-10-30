package posfiscalnfce.cash;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    Optional<CashSession> findByStoreIdAndOperatorAndStatus(UUID storeId, String operator, CashSession.Status status);

    Optional<CashSession> findByIdAndStoreId(UUID id, UUID storeId);

    List<CashSession> findByStoreIdAndStatusAndClosedAtBetweenOrderByClosedAt(UUID storeId, CashSession.Status status,
                                                                               Instant start, Instant end);
}
