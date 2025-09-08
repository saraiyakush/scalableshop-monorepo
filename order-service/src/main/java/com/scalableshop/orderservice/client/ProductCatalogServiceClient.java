package com.scalableshop.orderservice.client;

import com.scalableshop.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
public class ProductCatalogServiceClient {
  private final RestTemplate restTemplate;
  private final CircuitBreaker productCircuitBreaker;

  @Autowired
  public ProductCatalogServiceClient(
      RestTemplate restTemplate, CircuitBreaker productCircuitBreaker) {
    this.restTemplate = restTemplate;
    this.productCircuitBreaker = productCircuitBreaker;
  }

  public ProductDetails getProductDetails(String productId) {
    ProductDetails fallbackProductDetails =
        new ProductDetails(-1L, "Product temporarily unavailable", BigDecimal.ZERO, false, true);

    // Wrap the call inside the circuit breaker
    return productCircuitBreaker.execute(
        () -> getProductDetailsFromProductService(productId), fallbackProductDetails);
  }

  private ProductDetails getProductDetailsFromProductService(String productId) {
    String url = "http://localhost:8081/products/" + productId;
    return restTemplate.getForObject(url, ProductDetails.class);
  }
}
