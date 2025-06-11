package com.scalableshop.productcatalogservice.service;

import com.scalableshop.productcatalogservice.model.Product;
import com.scalableshop.productcatalogservice.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

  private final ProductRepository productRepository;

  @Autowired
  public ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Transactional
  public Product createProduct(Product product) {
    return productRepository.save(product);
  }

  @Transactional(readOnly = true)
  public Optional<Product> getProductById(Long id) {
    return productRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public List<Product> getAllProducts() {
    return productRepository.findAll();
  }

  @Transactional
  public Product updateProduct(Long id, Product productDetails) {
    Product existingProduct =
        productRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Product not found with id: "
                            + id)); // Custom exception handling could be better

    existingProduct.setName(productDetails.getName());
    existingProduct.setDescription(productDetails.getDescription());
    existingProduct.setPrice(productDetails.getPrice());
    existingProduct.setStock(productDetails.getStock()); // Update stock as well

    return productRepository.save(existingProduct);
  }

  @Transactional
  public void deleteProduct(Long id) {
    if (!productRepository.existsById(id)) {
      throw new RuntimeException("Product not found with id: " + id); // Custom exception handling
    }
    productRepository.deleteById(id);
  }

  @Transactional
  public Product updateStock(Long productId, Integer quantityChange) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

    int currentStock = product.getStock();
    int newStock =
        currentStock + quantityChange; // quantityChange can be positive (add) or negative (deduct)

    if (newStock < 0) {
      // Handle cases where stock would go negative (e.g., throw an InsufficientStockException)
      throw new RuntimeException("Insufficient stock for product id: " + productId);
    }

    product.setStock(newStock);
    return productRepository.save(product);
  }
}
