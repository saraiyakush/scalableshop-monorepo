package com.scalableshop.productinventoryservice.service;

import com.scalableshop.events.event.OrderCreatedEvent;
import com.scalableshop.events.event.StockReservationFailedEvent;
import com.scalableshop.events.event.StockReservedEvent;
import com.scalableshop.productinventoryservice.model.InventoryItem;
import com.scalableshop.productinventoryservice.repository.InventoryItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class InventoryService {

  private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
  private final InventoryItemRepository inventoryItemRepository;
  private final StreamBridge streamBridge;
  private final StockReservationHelper stockReservationHelper;

  @Autowired
  public InventoryService(
      InventoryItemRepository inventoryItemRepository,
      StreamBridge streamBridge,
      StockReservationHelper stockReservationHelper) {
    this.inventoryItemRepository = inventoryItemRepository;
    this.streamBridge = streamBridge;
    this.stockReservationHelper = stockReservationHelper;
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

  @Bean
  public Consumer<OrderCreatedEvent> orderCreatedEventConsumer() {
    return event -> {
      log.info(
          "Received OrderCreatedEvent for Order ID: {} by Customer ID: {}",
          event.getOrderId(),
          event.getCustomerId());
      stockReservationHelper
          .reserveStock(event.getOrderId(), event.getCustomerId(), event.getOrderItems())
          .subscribe(
              success -> {
                if (success) {
                  log.info(
                      "Stock reservation process completed successfully for Order ID: {}",
                      event.getOrderId());
                } else {
                  // This else block might not be hit if an exception is thrown
                  log.warn(
                      "Stock reservation process failed for Order ID: {}. Details in logs above (via published event).",
                      event.getOrderId());
                }
              },
              error -> {
                // This error block will now catch the RuntimeException thrown by reserveStock
                log.error(
                    "Error during stock reservation for Order ID: {}: {}",
                    event.getOrderId(),
                    error.getMessage(),
                    error);
                // The StockReservationFailedEvent has already been published.
                // No further action needed here, as the transaction is rolled back.
              });
    };
  }
}
