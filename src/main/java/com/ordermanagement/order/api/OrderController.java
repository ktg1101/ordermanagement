package com.ordermanagement.order.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import com.ordermanagement.order.application.OrderService;
import com.ordermanagement.order.application.OrderView;
import com.ordermanagement.order.domain.OrderStatus;
import com.ordermanagement.shared.common.paging.PageRequests;
import com.ordermanagement.shared.common.paging.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderView> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderView created = orderService.create(request.userId(), request.toCommands());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable("id") UUID id) {
        return orderService.get(id);
    }

    @GetMapping
    public PageResponse<OrderView> list(
            @RequestParam(name = "userId", required = false) UUID userId,
            @RequestParam(name = "status", required = false) OrderStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + PageRequests.DEFAULT_SIZE) int size) {
        return orderService.list(userId, status, page, size);
    }

    @PostMapping("/{id}/cancel")
    public OrderView cancel(@PathVariable("id") UUID id) {
        return orderService.cancel(id);
    }

    /** Hoàn tất đơn đã thanh toán (xuất kho). Dành cho vận hành/kho. */
    @PostMapping("/{id}/complete")
    public OrderView complete(@PathVariable("id") UUID id) {
        return orderService.complete(id);
    }
}
