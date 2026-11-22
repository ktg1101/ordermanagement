package com.ordermanagement.inventory.infrastructure;

import java.util.Collection;
import java.util.UUID;

import com.ordermanagement.inventory.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySku(String sku);

    /** {@code pattern} và {@code activeValues} luôn khác null nên query không có tham số null. */
    @Query("select p from Product p "
            + "where p.active in :activeValues "
            + "and (lower(p.name) like :pattern or lower(p.sku) like :pattern)")
    Page<Product> search(@Param("pattern") String pattern,
                         @Param("activeValues") Collection<Boolean> activeValues,
                         Pageable pageable);
}
