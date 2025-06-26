package com.scalableshop.productinventoryservice;

import com.scalableshop.events.event.OrderCreatedEvent;
import com.scalableshop.events.event.StockReservationFailedEvent;
import com.scalableshop.events.event.StockReservedEvent;
import com.scalableshop.productinventoryservice.model.InventoryItem;
import com.scalableshop.productinventoryservice.model.ProcessedOrderEvent;
import com.scalableshop.productinventoryservice.repository.InventoryItemRepository;
import com.scalableshop.productinventoryservice.repository.ProcessedOrderEventRepository;
import com.scalableshop.productinventoryservice.service.InventoryService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class ProductInventoryServiceIntegrationTest {

  @Autowired private InventoryService inventoryService; // The main service being tested

  @Autowired private InventoryItemRepository inventoryItemRepository;

  @Autowired private ProcessedOrderEventRepository processedOrderEventRepository;

  @MockBean // Mock StreamBridge to verify published events without real broker interaction
  private StreamBridge streamBridge;

  @BeforeEach
  public void setup() {
    // Clean up repositories before each test to ensure a clean state
    inventoryItemRepository.deleteAll();
    processedOrderEventRepository.deleteAll();
    // Reset mock interactions before each test
    reset(streamBridge);
  }

  @Test
  void orderCreatedEventConsumer_shouldReserveStockAndPublishEvent_orderCreatedEvent() {
    // Arrange
    Long productId = 101L;
    int initialStock = 10;
    int requestedQuantity = 5;
    Long orderId = 1L;
    Long customerId = 10L;

    initializeStock(productId, initialStock);

    OrderCreatedEvent orderCreatedEventFromOrderService =
        buildOrderCreatedEventReceivedFromOrderService(
            productId, requestedQuantity, orderId, customerId);

    // Act
    inventoryService.orderCreatedEventConsumer().accept(orderCreatedEventFromOrderService);

    // Assert
    verifyInventoryWasUpdated(productId, initialStock, requestedQuantity);
    verifyOrderWasSavedInProcessedOrderTable(orderId);

    verifyStockReservedEventWasPublished();
    verifyStockReservationFailedEventWasNotPublished();
  }

  @Test
  void orderCreatedEventConsumer_shouldNotReserveStockAndPublishEvent_DupOrderCreatedEvent() {
    // Arrange
    Long productId = 101L;
    int initialStock = 10;
    int requestedQuantity = 5;
    Long orderId = 1L;
    Long customerId = 10L;

    initializeStock(productId, initialStock);

    OrderCreatedEvent orderCreatedEventFromOrderService =
        buildOrderCreatedEventReceivedFromOrderService(
            productId, requestedQuantity, orderId, customerId);

    prepareTheOrderIsProcessedBefore(orderId);

    // Act
    inventoryService.orderCreatedEventConsumer().accept(orderCreatedEventFromOrderService);

    // Assert
    verifyInventoryWasNotUpdated(productId, initialStock);

    verifyStockReservedEventWasNotPublished();
    verifyStockReservationFailedEventWasNotPublished();
  }

  @Test
  void
      orderCreatedEventConsumer_shouldFailStockReservationAndPublishFailedEvent_InsufficientStock() {
    // Given
    Long productId = 102L;
    int initialStock = 3;
    int requestedQuantity = 5; // Requesting more than available
    Long orderId = 2L;
    Long customerId = 11L;

    // Initialize stock for the product
    initializeStock(productId, initialStock);

    // Create an OrderCreatedEvent that the consumer will process
    OrderCreatedEvent orderCreatedEventFromOrderService =
        buildOrderCreatedEventReceivedFromOrderService(
            productId, requestedQuantity, orderId, customerId);

    // When
    // Manually call the consumer bean. The consumer's subscribe will hit the error block.
    inventoryService.orderCreatedEventConsumer().accept(orderCreatedEventFromOrderService);

    // Verify InventoryItem quantity remains unchanged due to transactional rollback
    verifyInventoryWasNotUpdated(productId, initialStock);

    // Verify ProcessedOrderEvent was NOT saved due to transactional rollback
    Optional<ProcessedOrderEvent> processedEvent =
        processedOrderEventRepository.findByOrderId(orderId);
    assertThat(processedEvent).isNotPresent();

    // Verify StockReservationFailedEvent was published
    verify(streamBridge, timeout(500).times(1))
        .send(
            eq("stockReservationFailedEventProducer-out-0"),
            any(StockReservationFailedEvent.class));
    verifyStockReservedEventWasNotPublished();
  }

  @Test
  void orderCreatedEventConsumer_shouldFailStockReservationAndPublishFailedEvent_ProductNotFound() {
    // Given
    Long nonExistentProductId = 999L; // This product ID does not exist in inventory
    int requestedQuantity = 2;
    Long orderId = 3L;
    Long customerId = 12L;

    // DO NOT initialize stock for this product, simulating not found scenario
    assertThat(inventoryItemRepository.findByProductId(nonExistentProductId)).isNotPresent();

    // Create an OrderCreatedEvent for the non-existent product
    OrderCreatedEvent orderCreatedEventFromOrderService =
        buildOrderCreatedEventReceivedFromOrderService(
            nonExistentProductId, requestedQuantity, orderId, customerId);

    // When
    inventoryService.orderCreatedEventConsumer().accept(orderCreatedEventFromOrderService);

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
    verifyStockReservedEventWasNotPublished();
  }

  private void initializeStock(Long productId, int initialStock) {
    inventoryService.initializeStock(productId, initialStock).block();
    assertThat(inventoryItemRepository.findByProductId(productId)).isPresent();
    assertThat(inventoryItemRepository.findByProductId(productId).get().getQuantityAvailable())
        .isEqualTo(initialStock);
  }

  @NotNull
  private OrderCreatedEvent buildOrderCreatedEventReceivedFromOrderService(
      Long productId, int requestedQuantity, Long orderId, Long customerId) {
    OrderCreatedEvent.OrderItemEvent itemEvent =
        new OrderCreatedEvent.OrderItemEvent(productId, requestedQuantity);
    OrderCreatedEvent orderCreatedEvent =
        new OrderCreatedEvent(
            orderId,
            customerId,
            LocalDateTime.now(),
            BigDecimal.valueOf(requestedQuantity * 10.0),
            Collections.singletonList(itemEvent));
    return orderCreatedEvent;
  }

  private void verifyInventoryWasUpdated(Long productId, int initialStock, int requestedQuantity) {
    Optional<InventoryItem> updatedItem = inventoryItemRepository.findByProductId(productId);
    assertThat(updatedItem).isPresent();
    assertThat(updatedItem.get().getQuantityAvailable())
        .isEqualTo(initialStock - requestedQuantity);
    assertThat(updatedItem.get().getQuantityReserved()).isEqualTo(requestedQuantity);
  }

  private void verifyOrderWasSavedInProcessedOrderTable(Long orderId) {
    Optional<ProcessedOrderEvent> processedEvent =
        processedOrderEventRepository.findByOrderId(orderId);
    assertThat(processedEvent).isPresent();
    assertThat(processedEvent.get().getOrderId()).isEqualTo(orderId);
  }

  private void verifyStockReservationFailedEventWasNotPublished() {
    // We use timeout because the consumer might be slightly asynchronous or use reactive
    // subscribers
    verify(streamBridge, never()).send(eq("stockReservationFailedEventProducer-out-0"), any());
  }

  private void verifyStockReservedEventWasPublished() {
    verify(streamBridge, timeout(500).times(1))
        .send(eq("stockReservedEventProducer-out-0"), any(StockReservedEvent.class));
  }

  private void prepareTheOrderIsProcessedBefore(Long orderId) {
    processedOrderEventRepository.save(new ProcessedOrderEvent(orderId, LocalDateTime.now()));

    // Verify ProcessedOrderEvent was saved
    Optional<ProcessedOrderEvent> processedEvent =
        processedOrderEventRepository.findByOrderId(orderId);
    assertThat(processedEvent).isPresent();
    assertThat(processedEvent.get().getOrderId()).isEqualTo(orderId);
  }

  private void verifyInventoryWasNotUpdated(Long productId, int initialStock) {
    Optional<InventoryItem> updatedItem = inventoryItemRepository.findByProductId(productId);
    assertThat(updatedItem).isPresent();
    assertThat(updatedItem.get().getQuantityAvailable()).isEqualTo(initialStock);
    assertThat(updatedItem.get().getQuantityReserved()).isEqualTo(0);
  }

  private void verifyStockReservedEventWasNotPublished() {
    verify(streamBridge, never()).send(eq("stockReservedEventProducer-out-0"), any());
  }
}
