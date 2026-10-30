package posfiscalnfce.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByStoreIdOrderByName(UUID storeId);

    Optional<Product> findByIdAndStoreId(UUID id, UUID storeId);

    List<Product> findByStoreIdAndIdIn(UUID storeId, Collection<UUID> ids);

    boolean existsByStoreIdAndSku(UUID storeId, String sku);
}
