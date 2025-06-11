package com.scalableshop.productinventorymonolith.client;

import com.scalableshop.productinventorymonolith.model.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ProductCatalogClient {
  private final WebClient webClient;

  public ProductCatalogClient(@Value("${product-catalog-service.url}") String catalogServiceUrl) {
    this.webClient = WebClient.builder().baseUrl(catalogServiceUrl).build();
  }

  public Mono<Product> getProductById(Long id) {
    return webClient.get().uri("/api/products/{id}", id).retrieve().bodyToMono(Product.class);
  }

  public Mono<Product> updateCatalogProductStock(Long productId, Integer quantityChange) {
    StockUpdateRequest requestBody = new StockUpdateRequest(quantityChange);
    return webClient
        .put()
        .uri("/api/products/{id}/stock", productId)
        .bodyValue(requestBody)
        .retrieve()
        .bodyToMono(Product.class);
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  private static class StockUpdateRequest {
    private Integer quantityChange;
    // Add other fields here if the Catalog Service's DTO for stock update expects them.
    // For example: private String reason;
  }
}
