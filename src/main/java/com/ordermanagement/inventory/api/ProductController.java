package com.ordermanagement.inventory.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import com.ordermanagement.inventory.application.ProductService;
import com.ordermanagement.inventory.application.ProductView;
import com.ordermanagement.shared.common.paging.PageRequests;
import com.ordermanagement.shared.common.paging.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductView> create(@Valid @RequestBody CreateProductRequest request) {
        ProductView created = productService.create(
                request.sku(), request.name(), request.description(), request.price());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ProductView get(@PathVariable("id") UUID id) {
        return productService.get(id);
    }

    @GetMapping
    public PageResponse<ProductView> list(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + PageRequests.DEFAULT_SIZE) int size) {
        return productService.list(q, active, page, size);
    }

    @PutMapping("/{id}")
    public ProductView update(@PathVariable("id") UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request.name(), request.description(), request.price(), request.active());
    }

    /** Xóa mềm: ngừng bán sản phẩm. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable("id") UUID id) {
        productService.deactivate(id);
    }
}
