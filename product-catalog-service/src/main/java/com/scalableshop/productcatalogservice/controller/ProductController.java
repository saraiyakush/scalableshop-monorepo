package com.scalableshop.productcatalogservice.controller;

import com.scalableshop.productcatalogservice.model.Product;
import com.scalableshop.productcatalogservice.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

  private static final Logger log = LoggerFactory.getLogger(ProductController.class);

  private final ProductService productService;

  @Autowired
  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @PostMapping
  public ResponseEntity<Product> createProduct(@RequestBody Product product) {
    Product createdProduct = productService.createProduct(product);
    return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Product> getProductById(@PathVariable Long id) {
    log.info("Getting product with id {}", id);
    return productService
        .getProductById(id)
        .map(product -> new ResponseEntity<>(product, HttpStatus.OK))
        .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @GetMapping
  public ResponseEntity<List<Product>> getAllProducts() {
    List<Product> products = productService.getAllProducts();
    return new ResponseEntity<>(products, HttpStatus.OK); // Returns 200 OK
  }

  @PutMapping("/{id}")
  public ResponseEntity<Product> updateProduct(
      @PathVariable Long id, @RequestBody Product productDetails) {
    try {
      Product updatedProduct = productService.updateProduct(id, productDetails);
      return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
    } catch (RuntimeException e) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
    try {
      productService.deleteProduct(id);
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    } catch (RuntimeException e) {
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  // --- Inventory Specific Endpoint ---

  // PUT /api/products/{productId}/stock
  // Request Body: {"quantityChange": -5} or {"quantityChange": 10}
  @PutMapping("/{productId}/stock")
  public ResponseEntity<Product> updateProductStock(
      @PathVariable Long productId, @RequestBody StockUpdateRequest request) {
    log.info("Updating product stock with id {}, request {}", productId,  request);
    try {
      Product updatedProduct = productService.updateStock(productId, request.getQuantityChange());
      return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
    } catch (RuntimeException e) {
      // You would typically differentiate between not found and insufficient stock
      if (e.getMessage().contains("not found")) {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 404
      } else if (e.getMessage().contains("Insufficient stock")) {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST); // 400
      }
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR); // 500 for other errors
    }
  }
}
