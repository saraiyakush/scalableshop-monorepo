package com.scalableshop.productinventoryservice.controller;

import com.scalableshop.productinventoryservice.model.InventoryItem;
import com.scalableshop.productinventoryservice.service.InventoryService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

  private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

  private final InventoryService inventoryService;

  @Autowired
  public InventoryController(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
  }

  /**
   * Endpoint to initialize/set stock directly within this Inventory Service's database. This is for
   * initial setup or direct setting of stock levels. PUT /api/inventory/{productId}/set-stock Body:
   * {"quantity": 100}
   */
  @PutMapping("/{productId}/set-stock")
  public Mono<ResponseEntity<InventoryItem>> setProductStock(
      @PathVariable Long productId, @RequestBody StockUpdateRequest request) {
    if (request.getQuantityChange() == null) {
      return Mono.just(ResponseEntity.badRequest().build()); // Quantity is required for setting
    }
    log.info(
        "Received request to SET stock for product ID: {} to quantity: {}",
        productId,
        request.getQuantityChange());
    return inventoryService
        .initializeStock(productId, request.getQuantityChange())
        .map(ResponseEntity::ok)
        .onErrorResume(
            e -> {
              log.error("Error setting stock for product {}: {}", productId, e.getMessage());
              return Mono.just(
                  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                      .header("X-Error-Message", e.getMessage())
                      .build());
            });
  }

  /**
   * Endpoint to update (add/subtract) stock locally within this Inventory Service's database. This
   * is typically for adjustments or when orders are processed. PUT /api/inventory/{productId}/stock
   * Body: {"quantityChange": -1}
   */
  @PutMapping("/{productId}/stock")
  public Mono<ResponseEntity<InventoryItem>> updateProductStock(
      @PathVariable Long productId, @RequestBody StockUpdateRequest request) {
    if (request.getQuantityChange() == null) {
      return Mono.just(ResponseEntity.badRequest().build()); // Quantity change is required
    }
    log.info(
        "Received request to UPDATE stock for product ID: {} with quantity change: {}",
        productId,
        request.getQuantityChange());

    return inventoryService
        .updateStock(productId, request.getQuantityChange())
        .map(ResponseEntity::ok)
        .onErrorResume(
            e -> {
              log.error("Error updating stock for product {}: {}", productId, e.getMessage());
              HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
              if (e.getMessage().contains("Insufficient stock")) {
                status = HttpStatus.BAD_REQUEST; // Specific error for insufficient stock
              }
              return Mono.just(
                  ResponseEntity.status(status).header("X-Error-Message", e.getMessage()).build());
            });
  }

  /**
   * Endpoint to get local stock levels from this Inventory Service's database. GET
   * /api/inventory/{productId}/stock
   */
  @GetMapping("/{productId}/stock")
  public Mono<ResponseEntity<InventoryItem>> getProductStock(@PathVariable Long productId) {
    log.info("Received request to get stock for product ID: {}", productId);
    return inventoryService
        .getInventoryByProductId(productId)
        .map(ResponseEntity::ok)
        .onErrorResume(
            e -> {
              log.error("Error getting stock for product {}: {}", productId, e.getMessage());
              HttpStatus status = HttpStatus.NOT_FOUND; // Assume not found if not in inventory
              if (e.getMessage().contains("Inventory item not found")) {
                status = HttpStatus.NOT_FOUND;
              }
              return Mono.just(
                  ResponseEntity.status(status).header("X-Error-Message", e.getMessage()).build());
            });
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StockUpdateRequest {
    private Integer quantityChange;
    private Long productId; // Add productId to request body for initialization/direct update
  }
}
