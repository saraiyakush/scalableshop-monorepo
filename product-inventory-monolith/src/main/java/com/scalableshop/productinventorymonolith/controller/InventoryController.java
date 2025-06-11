package com.scalableshop.productinventorymonolith.controller;

import com.scalableshop.productinventorymonolith.client.ProductCatalogClient;
import com.scalableshop.productinventorymonolith.model.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

  private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

  private final ProductCatalogClient productCatalogClient;

  @Autowired
  public InventoryController(ProductCatalogClient productCatalogClient) {
    this.productCatalogClient = productCatalogClient;
  }

  @PutMapping("/{productId}/stock")
  public Mono<ResponseEntity<Product>> updateProductStock(
      @PathVariable Long productId, @RequestBody StockUpdateRequest request) {
    log.info("Updating product stock with id {}", productId);
    // First, get product details from the Catalog Service
    return productCatalogClient
        .updateCatalogProductStock(productId, request.getQuantityChange())
        .map(
            updatedProduct -> {
              // Successfully updated via Catalog Service
              log.info(
                  "Catalog Service reported stock updated for product {}. New stock: {}",
                  updatedProduct.getId(),
                  updatedProduct.getStock());
              // Return the updated Product object (or a custom DTO) as JSON
              return ResponseEntity.ok(updatedProduct);
            })
        .onErrorResume(
            e -> {
              String errorMessage =
                  "An unexpected error occurred during Catalog Service communication.";
              HttpStatus statusCode = HttpStatus.INTERNAL_SERVER_ERROR;

              if (e instanceof WebClientResponseException) {
                WebClientResponseException webException = (WebClientResponseException) e;
                statusCode = HttpStatus.valueOf(webException.getStatusCode().value());
                errorMessage = "Catalog Service error: " + webException.getResponseBodyAsString();
                log.error(
                    "Catalog Service responded with status {} and body: {}",
                    statusCode,
                    webException.getResponseBodyAsString());
              }

              // To satisfy 'Mono<ResponseEntity<Product>>' requirement, return
              // ResponseEntity<Product>
              // with a null body and put the error message in a header.
              return Mono.just(
                  ResponseEntity.<Product>status(statusCode)
                      .header(
                          "X-Error-Message", errorMessage) // Add custom header for error message
                      .build()); // No body, or null body
            })
        .defaultIfEmpty(
            new ResponseEntity<Product>(
                HttpStatus.INTERNAL_SERVER_ERROR)); // Should ideally not be hit with flatMap
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StockUpdateRequest {
    private Integer quantityChange;
  }
}
