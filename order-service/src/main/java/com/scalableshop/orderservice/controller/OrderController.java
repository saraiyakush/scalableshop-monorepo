package com.scalableshop.orderservice.controller;

import com.scalableshop.orderservice.model.Order;
import com.scalableshop.orderservice.model.OrderItem;
import com.scalableshop.orderservice.service.OrderService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private static final Logger log = LoggerFactory.getLogger(OrderController.class);
  private final OrderService orderService;

  @Autowired
  public OrderController(OrderService orderService) {
    this.orderService = orderService;
    log.info("OrderController initialized.");
  }

  /** DTO for incoming order creation request. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OrderRequest {
    private Long customerId;
    private List<OrderItemRequest> items;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OrderItemRequest {
    private Long productId;
    private String productName; // Snapshot product name
    private BigDecimal unitPrice; // Snapshot unit price
    private Integer quantity;
  }

  /**
   * Endpoint to create a new order. POST /api/orders Body: {"customerId": 123, "items":
   * [{"productId": 1, "productName": "Laptop", "unitPrice": 1500.00, "quantity": 1}]}
   */
  @PostMapping
  public Mono<ResponseEntity<Order>> createOrder(@RequestBody OrderRequest request) {
    log.info("Received order creation request for customerId: {}", request.getCustomerId());

    List<OrderItem> orderItems =
        request.getItems().stream()
            .map(
                itemReq -> {
                  OrderItem item = new OrderItem();
                  item.setProductId(itemReq.getProductId());
                  item.setProductName(itemReq.getProductName());
                  item.setUnitPrice(itemReq.getUnitPrice());
                  item.setQuantity(itemReq.getQuantity());
                  if (itemReq.getUnitPrice() == null || itemReq.getQuantity() == null) {
                    throw new IllegalArgumentException(
                        "Unit price and quantity must be provided for each order item.");
                  }
                  item.setSubtotal(
                      itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
                  // Subtotal will be calculated by @PrePersist
                  return item;
                })
            .toList();

    return orderService
        .createOrder(request.getCustomerId(), orderItems)
        .map(order -> ResponseEntity.status(HttpStatus.CREATED).body(order))
        .onErrorResume(
            e -> {
              log.error(
                  "Error creating order for customerId {}: {}",
                  request.getCustomerId(),
                  e.getMessage());
              return Mono.just(
                  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                      .header("X-Error-Message", e.getMessage())
                      .build());
            });
  }

  /** Endpoint to get an order by ID. GET /api/orders/{orderId} */
  @GetMapping("/{orderId}")
  public Mono<ResponseEntity<Order>> getOrderById(@PathVariable Long orderId) {
    log.info("Received request to get order with ID: {}", orderId);
    return orderService
        .getOrderById(orderId)
        .map(ResponseEntity::ok)
        .onErrorResume(
            e -> {
              log.error("Error fetching order with ID {}: {}", orderId, e.getMessage());
              return Mono.just(
                  ResponseEntity.status(HttpStatus.NOT_FOUND)
                      .header("X-Error-Message", e.getMessage())
                      .build());
            });
  }
}
