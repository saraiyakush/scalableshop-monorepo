package com.scalableshop.productinventoryservice.service;

import com.scalableshop.productinventoryservice.model.InventoryItem;
import com.scalableshop.productinventoryservice.repository.InventoryItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InventoryService {

  private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
  private final InventoryItemRepository inventoryItemRepository;

  @Autowired
  public InventoryService(InventoryItemRepository inventoryItemRepository) {
    this.inventoryItemRepository = inventoryItemRepository;
  }

  // Method to initialize some stock (for testing purposes)
  @Transactional
  public Mono<InventoryItem> initializeStock(Long productId, Integer initialQuantity) {
    return Mono.fromCallable(
        () -> {
          log.info(
              "Initializing stock for productId: {} with quantity: {}", productId, initialQuantity);
          return inventoryItemRepository
              .findByProductId(productId)
              .map(
                  item -> {
                    item.setQuantityAvailable(initialQuantity);
                    item.setQuantityReserved(0);
                    log.info("Updating existing stock for productId {}: {}", productId, item);
                    return inventoryItemRepository.save(item);
                  })
              .orElseGet(
                  () -> {
                    InventoryItem newItem = new InventoryItem(productId, initialQuantity, 0);
                    log.info("Creating new stock for productId {}: {}", productId, newItem);
                    return inventoryItemRepository.save(newItem);
                  });
        });
  }

  // Method to update stock locally
  @Transactional
  public Mono<InventoryItem> updateStock(Long productId, Integer quantityChange) {
    return Mono.fromCallable(
        () -> {
          log.info(
              "Attempting to update stock for productId: {} by quantity: {}",
              productId,
              quantityChange);
          return inventoryItemRepository
              .findByProductId(productId)
              .map(
                  item -> {
                    int newQuantity = item.getQuantityAvailable() + quantityChange;
                    if (newQuantity < 0) {
                      throw new RuntimeException(
                          "Insufficient stock for product " + productId); // Or a custom exception
                    }
                    item.setQuantityAvailable(newQuantity);
                    log.info(
                        "Updated stock for productId {}: New quantity: {}", productId, newQuantity);
                    return inventoryItemRepository.save(item);
                  })
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          "Inventory item not found for product ID: "
                              + productId)); // Or a custom exception
        });
  }

  // Method to get stock locally
  public Mono<InventoryItem> getInventoryByProductId(Long productId) {
    return Mono.fromCallable(
        () -> {
          log.info("Fetching stock for productId: {}", productId);
          return inventoryItemRepository
              .findByProductId(productId)
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          "Inventory item not found for product ID: " + productId));
        });
  }
}
