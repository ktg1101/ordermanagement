package com.ordermanagement.inventory.api;

import java.util.UUID;

import jakarta.validation.Valid;

import com.ordermanagement.inventory.application.InventoryService;
import com.ordermanagement.inventory.application.InventoryView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public InventoryView get(@PathVariable("productId") UUID productId) {
        return inventoryService.get(productId);
    }

    @PostMapping("/{productId}/adjustments")
    public InventoryView adjust(@PathVariable("productId") UUID productId,
                                @Valid @RequestBody AdjustStockRequest request) {
        return inventoryService.adjust(productId, request.delta());
    }
}
