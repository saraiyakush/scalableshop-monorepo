package com.scalableshop.productinventoryservice;

import com.scalableshop.events.event.OrderCreatedEvent;
import com.scalableshop.events.event.StockReservationFailedEvent;
import com.scalableshop.events.event.StockReservedEvent;
import com.scalableshop.productinventoryservice.model.InventoryItem;
import com.scalableshop.productinventoryservice.model.ProcessedOrderEvent;
import com.scalableshop.productinventoryservice.repository.InventoryItemRepository;
import com.scalableshop.productinventoryservice.repository.ProcessedOrderEventRepository;
import com.scalableshop.productinventoryservice.service.InventoryService;
import com.scalableshop.productinventoryservice.service.StockReservationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
public class ProductInventoryServiceIntegrationTest {

  @Autowired private InventoryService inventoryService; // The main service being tested

  @Autowired private InventoryItemRepository inventoryItemRepository;

  @Autowired private ProcessedOrderEventRepository processedOrderEventRepository;

  @MockBean // Mock StreamBridge to verify published events without real broker interaction
  private StreamBridge streamBridge;

  // We don't directly autowire StockReservationHelper here to test the consumer flow
  // The InventoryService's consumer will call it internally.

  @BeforeEach
  public void setup() {
    // Clean up repositories before each test to ensure a clean state
    inventoryItemRepository.deleteAll();
    processedOrderEventRepository.deleteAll();
    // Reset mock interactions before each test
    reset(streamBridge);
  }

  @Test
  @Transactional // Ensures test runs in a transaction and rolls back
  void orderCreatedEventConsumer_shouldReserveStockAndPublishEvent_Success() {
    // Given
    Long productId = 101L;
    int initialStock = 10;
    int requestedQuantity = 5;
    Long orderId = 1L;
    Long customerId = 10L;

    // Initialize stock for the product
    inventoryService.initializeStock(productId, initialStock).block();
    assertThat(inventoryItemRepository.findByProductId(productId)).isPresent();
    assertThat(inventoryItemRepository.findByProductId(productId).get().getQuantityAvailable())
        .isEqualTo(initialStock);

    // Create an OrderCreatedEvent that the consumer will process
    OrderCreatedEvent.OrderItemEvent itemEvent =
        new OrderCreatedEvent.OrderItemEvent(productId, requestedQuantity);
    OrderCreatedEvent orderCreatedEvent =
        new OrderCreatedEvent(
            orderId,
            customerId,
            LocalDateTime.now(),
            BigDecimal.valueOf(requestedQuantity * 10.0),
            Collections.singletonList(itemEvent));

    // When
    // Manually call the consumer bean with the event
    inventoryService.orderCreatedEventConsumer().accept(orderCreatedEvent);

    // Then
    // Verify InventoryItem was updated correctly
    Optional<InventoryItem> updatedItem = inventoryItemRepository.findByProductId(productId);
    assertThat(updatedItem).isPresent();
    assertThat(updatedItem.get().getQuantityAvailable())
        .isEqualTo(initialStock - requestedQuantity);
    assertThat(updatedItem.get().getQuantityReserved()).isEqualTo(requestedQuantity);

    // Verify ProcessedOrderEvent was saved
    Optional<ProcessedOrderEvent> processedEvent =
        processedOrderEventRepository.findByOrderId(orderId);
    assertThat(processedEvent).isPresent();
    assertThat(processedEvent.get().getOrderId()).isEqualTo(orderId);

    // Verify StockReservedEvent was published
    // We use timeout because the consumer might be slightly asynchronous or use reactive
    // subscribers
    verify(streamBridge, timeout(500).times(1))
        .send(eq("stockReservedEventProducer-out-0"), any(StockReservedEvent.class));
    verify(streamBridge, never()).send(eq("stockReservationFailedEventProducer-out-0"), any());
  }

  @Test
  void orderCreatedEventConsumer_shouldFailStockReservationAndPublishFailedEvent_InsufficientStock()
      throws InterruptedException {
    // Given
    Long productId = 102L;
    int initialStock = 3;
    int requestedQuantity = 5; // Requesting more than available
    Long orderId = 2L;
    Long customerId = 11L;

    // Initialize stock for the product
    inventoryService.initializeStock(productId, initialStock).block();
    assertThat(inventoryItemRepository.findByProductId(productId)).isPresent();
    assertThat(inventoryItemRepository.findByProductId(productId).get().getQuantityAvailable())
        .isEqualTo(initialStock);

    // Create an OrderCreatedEvent that the consumer will process
    OrderCreatedEvent.OrderItemEvent itemEvent =
        new OrderCreatedEvent.OrderItemEvent(productId, requestedQuantity);
    OrderCreatedEvent orderCreatedEvent =
        new OrderCreatedEvent(
            orderId,
            customerId,
            LocalDateTime.now(),
            BigDecimal.valueOf(requestedQuantity * 10.0),
            Collections.singletonList(itemEvent));

    // When
    // Manually call the consumer bean. The consumer's subscribe will hit the error block.
    inventoryService.orderCreatedEventConsumer().accept(orderCreatedEvent);

    // Since the consumer's subscribe method handles the error asynchronously,
    // we need to verify the side effects (DB state, published events).
    // The RuntimeException thrown by StockReservationHelper will cause the Mono returned
    // by reserveStock to onError, which is caught by the consumer's error handler.
    // We need to allow time for the async operations (transaction rollback, logging) to complete.

    // Then
    // Verify InventoryItem quantity remains unchanged due to transactional rollback
    Optional<InventoryItem> updatedItem = inventoryItemRepository.findByProductId(productId);
    assertThat(updatedItem).isPresent();
    assertThat(updatedItem.get().getQuantityAvailable())
        .isEqualTo(initialStock); // Should be rolled back
    assertThat(updatedItem.get().getQuantityReserved()).isEqualTo(0); // Should be rolled back

    // Verify ProcessedOrderEvent was NOT saved due to transactional rollback
    Optional<ProcessedOrderEvent> processedEvent =
        processedOrderEventRepository.findByOrderId(orderId);
    assertThat(processedEvent).isNotPresent();

    // Verify StockReservationFailedEvent was published
    verify(streamBridge, timeout(500).times(1))
        .send(
            eq("stockReservationFailedEventProducer-out-0"),
            any(StockReservationFailedEvent.class));
    verify(streamBridge, never()).send(eq("stockReservedEventProducer-out-0"), any());

    // The consumer's error handling logs the exception, it doesn't re-throw to the test thread
    // directly
    // because the 'accept' method is synchronous but its internal 'subscribe' is async.
    // We've verified side effects.
  }

  @Test
  void orderCreatedEventConsumer_shouldFailStockReservationAndPublishFailedEvent_ProductNotFound()
      throws InterruptedException {
    // Given
    Long nonExistentProductId = 999L; // This product ID does not exist in inventory
    int requestedQuantity = 2;
    Long orderId = 3L;
    Long customerId = 12L;

    // DO NOT initialize stock for this product, simulating not found scenario
    assertThat(inventoryItemRepository.findByProductId(nonExistentProductId)).isNotPresent();

    // Create an OrderCreatedEvent for the non-existent product
    OrderCreatedEvent.OrderItemEvent itemEvent =
        new OrderCreatedEvent.OrderItemEvent(nonExistentProductId, requestedQuantity);
    OrderCreatedEvent orderCreatedEvent =
        new OrderCreatedEvent(
            orderId,
            customerId,
            LocalDateTime.now(),
            BigDecimal.valueOf(requestedQuantity * 10.0),
            Collections.singletonList(itemEvent));

    // When
    inventoryService.orderCreatedEventConsumer().accept(orderCreatedEvent);

    // Then
    // Verify no InventoryItem was created or modified for the non-existent product
    assertThat(inventoryItemRepository.findByProductId(nonExistentProductId)).isNotPresent();

    // Verify ProcessedOrderEvent was NOT saved due to transactional rollback
    Optional<ProcessedOrderEvent> processedEvent =
        processedOrderEventRepository.findByOrderId(orderId);
    assertThat(processedEvent).isNotPresent();

    // Verify StockReservationFailedEvent was published
    verify(streamBridge, timeout(500).times(1))
        .send(
            eq("stockReservationFailedEventProducer-out-0"),
            any(StockReservationFailedEvent.class));
    verify(streamBridge, never()).send(eq("stockReservedEventProducer-out-0"), any());

    // As before, we've verified side effects for the async error.
  }
}
