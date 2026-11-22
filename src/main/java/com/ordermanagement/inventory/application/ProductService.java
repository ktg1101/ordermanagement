package com.ordermanagement.inventory.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ordermanagement.inventory.domain.Product;
import com.ordermanagement.inventory.infrastructure.ProductRepository;
import com.ordermanagement.shared.common.paging.LikePatterns;
import com.ordermanagement.shared.common.paging.PageRequests;
import com.ordermanagement.shared.common.paging.PageResponse;
import com.ordermanagement.shared.exception.BusinessRuleException;
import com.ordermanagement.shared.exception.ConflictException;
import com.ordermanagement.shared.exception.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case của danh mục sản phẩm, thuộc module {@code inventory}. Module khác (ví dụ {@code order})
 * chỉ dùng {@link #get(UUID)} và {@link #getSellableByIds(Collection)}.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductView create(String sku, String name, String description, BigDecimal price) {
        String normalizedSku = normalizeSku(sku);
        if (productRepository.existsBySku(normalizedSku)) {
            throw skuTaken(normalizedSku);
        }
        Product product = Product.create(normalizedSku, name.trim(), clean(description), scale(price));
        try {
            productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw skuTaken(normalizedSku);
        }
        return toView(product);
    }

    public ProductView get(UUID id) {
        return toView(find(id));
    }

    /** {@code active == null} nghĩa là không lọc theo trạng thái. */
    public PageResponse<ProductView> list(String q, Boolean active, int page, int size) {
        Collection<Boolean> activeValues = active == null ? List.of(Boolean.TRUE, Boolean.FALSE) : List.of(active);
        Pageable pageable = PageRequests.of(page, size, Sort.by("name").and(Sort.by("id")));
        return PageResponse.from(productRepository.search(LikePatterns.contains(q), activeValues, pageable)
                .map(this::toView));
    }

    /** Cập nhật thông tin/giá. SKU bất biến. Giá mới không ảnh hưởng đơn cũ (đơn lưu snapshot giá). */
    @Transactional
    public ProductView update(UUID id, String name, String description, BigDecimal price, boolean active) {
        Product product = find(id);
        product.update(name.trim(), clean(description), scale(price), active);
        productRepository.saveAndFlush(product);
        return toView(product);
    }

    /** "Xóa" sản phẩm = ngừng bán (FK RESTRICT nên không xóa cứng được). Idempotent. */
    @Transactional
    public ProductView deactivate(UUID id) {
        Product product = find(id);
        product.deactivate();
        productRepository.saveAndFlush(product);
        return toView(product);
    }

    /**
     * Dùng khi đặt hàng: mọi id phải tồn tại (404) và đang bán (422).
     * Kết quả giữ đúng thứ tự của {@code ids}.
     */
    public Map<UUID, ProductView> getSellableByIds(Collection<UUID> ids) {
        Map<UUID, Product> found = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<UUID> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new NotFoundException("Product", missing);
        }
        List<UUID> inactive = ids.stream().filter(id -> !found.get(id).isActive()).toList();
        if (!inactive.isEmpty()) {
            throw new BusinessRuleException("PRODUCT_NOT_AVAILABLE",
                    "Products are not available for sale: " + inactive);
        }

        Map<UUID, ProductView> result = new LinkedHashMap<>();
        for (UUID id : ids) {
            result.put(id, toView(found.get(id)));
        }
        return result;
    }

    private Product find(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    private ProductView toView(Product product) {
        return new ProductView(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                product.getPrice(), product.isActive(), product.getCreatedAt(), product.getUpdatedAt());
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static BigDecimal scale(BigDecimal price) {
        return price.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static ConflictException skuTaken(String sku) {
        return new ConflictException("SKU_ALREADY_EXISTS", "SKU is already in use: " + sku);
    }
}
