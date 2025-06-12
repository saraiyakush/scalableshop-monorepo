package com.scalableshop.productinventoryservice.service;

import com.scalableshop.events.event.OrderCreatedEvent;
import com.scalableshop.events.event.StockReservationFailedEvent;
import com.scalableshop.events.event.StockReservedEvent;
import com.scalableshop.productinventoryservice.model.InventoryItem;
import com.scalableshop.productinventoryservice.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class StockReservationHelper {

  private static final Logger log = LoggerFactory.getLogger(StockReservationHelper.class);
  private final InventoryItemRepository inventoryItemRepository;
  private final StreamBridge streamBridge;

  @Autowired
  public StockReservationHelper(
      InventoryItemRepository inventoryItemRepository, StreamBridge streamBridge) {
    this.inventoryItemRepository = inventoryItemRepository;
    this.streamBridge = streamBridge;
  }

  @Transactional
  public Mono<Boolean> reserveStock(
      Long orderId, Long customerId, List<OrderCreatedEvent.OrderItemEvent> items) {
    log.info("Attempting to reserve stock for Order ID: {}", orderId);
    List<StockReservationFailedEvent.FailedItem> failedItems = new ArrayList<>();
    List<StockReservedEvent.ReservedItem> reservedItems = new ArrayList<>();
    boolean allReserved = true;

    for (OrderCreatedEvent.OrderItemEvent item : items) {
      Optional<InventoryItem> optionalInventoryItem =
          inventoryItemRepository.findByProductId(item.getProductId());

      if (optionalInventoryItem.isEmpty()) {
        log.warn("Product ID {} not found in inventory. Cannot reserve.", item.getProductId());
        failedItems.add(
            new StockReservationFailedEvent.FailedItem(item.getProductId(), item.getQuantity(), 0));
        allReserved = false;
        continue;
      }

      InventoryItem inventoryItem = optionalInventoryItem.get();
      int requestedQuantity = item.getQuantity();

      if (inventoryItem.getQuantityAvailable() >= requestedQuantity) {
        inventoryItem.setQuantityAvailable(
            inventoryItem.getQuantityAvailable() - requestedQuantity);
        inventoryItem.setQuantityReserved(inventoryItem.getQuantityReserved() + requestedQuantity);
        inventoryItemRepository.save(inventoryItem);
        reservedItems.add(
            new StockReservedEvent.ReservedItem(item.getProductId(), requestedQuantity));
        log.info(
            "Reserved {} units of Product ID: {} for Order ID: {}",
            requestedQuantity,
            item.getProductId(),
            orderId);
      } else {
        log.warn(
            "Insufficient stock for Product ID: {}. Available: {}, Requested: {}",
            item.getProductId(),
            inventoryItem.getQuantityAvailable(),
            requestedQuantity);
        failedItems.add(
            new StockReservationFailedEvent.FailedItem(
                item.getProductId(), requestedQuantity, inventoryItem.getQuantityAvailable()));
        allReserved = false;
      }
    }

    if (allReserved) {
      StockReservedEvent event = new StockReservedEvent(orderId, customerId, reservedItems);
      streamBridge.send("stockReservedEventProducer-out-0", event);
      log.info("Published StockReservedEvent for Order ID: {}", orderId);
      return Mono.just(true);
    } else {
      String reason = "Insufficient stock for some items in order " + orderId;
      StockReservationFailedEvent event =
          new StockReservationFailedEvent(orderId, customerId, reason, failedItems);
      streamBridge.send("stockReservationFailedEventProducer-out-0", event);
      log.warn(
          "Published StockReservationFailedEvent for Order ID: {} with reason: {}",
          orderId,
          reason);

      throw new RuntimeException(
          "Stock reservation failed for order " + orderId + ". Reason: " + reason);
    }
  }
}
