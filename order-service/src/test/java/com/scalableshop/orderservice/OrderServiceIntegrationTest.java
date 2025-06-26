package com.scalableshop.orderservice;

import com.scalableshop.orderservice.model.OrderStatus;
import com.scalableshop.orderservice.model.Order;
import com.scalableshop.orderservice.model.OrderItem;
import com.scalableshop.orderservice.model.OutboxMessage;
import com.scalableshop.orderservice.repository.OrderRepository;
import com.scalableshop.orderservice.repository.OutboxMessageRepository;
import com.scalableshop.orderservice.service.OrderService;
import com.scalableshop.orderservice.service.OutboxMessageRelayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class OrderServiceIntegrationTest {

  @Autowired private OrderService orderService; // The actual service bean we are testing

  @Autowired private OrderRepository orderRepository;

  @Autowired private OutboxMessageRepository outboxMessageRepository;

  @MockBean // This creates a mock for StreamBridge throughout the Spring context for tests
  private StreamBridge streamBridge;

  @Autowired private OutboxMessageRelayer outboxMessageRelayer;

  @BeforeEach
  public void setup() {
    // Clean up repositories before each test to ensure a clean state
    orderRepository.deleteAll();
    outboxMessageRepository.deleteAll();
    // Reset mock interactions before each test to prevent interference between tests
    reset(streamBridge);
  }

  @Test
  @Transactional // Ensures this test runs within a transaction that rolls back after completion
  void createOrder_shouldSaveOrderAndOutboxMessageAtomically() {
    // Arrange
    Long customerId = 1L;
    OrderItem item = new OrderItem();
    item.setProductId(101L);
    item.setQuantity(2);
    item.setUnitPrice(BigDecimal.valueOf(10.00));
    item.setSubtotal(BigDecimal.valueOf(20.00)); // Ensure subtotal is calculated or set

    List<OrderItem> items = Collections.singletonList(item);

    // Act
    Order createdOrder = orderService.createOrder(customerId, items).block();

    // Then
    verifyOrderIsCreatedInOrdersTable(createdOrder, customerId);
    verifyOrderCreatedEventIsSavedInOutboxTableOnlyOnce(createdOrder, customerId, item);

    verifyOrderCreatedEventIsNotPublished();
  }

  @Test
  void createOrder_shouldNotSaveOrderAndOutboxMessage_whenException() {}

  @Test
  void outboxMessageRelayer_shouldPublishAndClearMessage() {
    // Act
    Long customerId = 2L;
    OrderItem item = new OrderItem();
    item.setProductId(202L);
    item.setQuantity(1);
    item.setUnitPrice(BigDecimal.valueOf(50.00));
    item.setSubtotal(BigDecimal.valueOf(50.00)); // Ensure subtotal is calculated or set
    List<OrderItem> items = Collections.singletonList(item);

    prepareOutboxTableHasMessage(customerId, items);

    // Act
    outboxMessageRelayer.processOutboxMessages();

    // Assert
    verifyOrderCreatedEventIsPublished();

    verifyOutboxTableHasNoMessages();
  }

  private void prepareOutboxTableHasMessage(Long customerId, List<OrderItem> items) {
    Order createdOrder = orderService.createOrder(customerId, items).block();
    assertThat(outboxMessageRepository.findAll()).hasSize(1); // Confirm message is in outbox
  }

  private void verifyOutboxTableHasNoMessages() {
    assertThat(outboxMessageRepository.findAll()).isEmpty();
  }

  private void verifyOrderCreatedEventIsPublished() {
    // 'timeout(500)' gives a small buffer for the mock interaction to register, though
    // for a direct method call like this, it's typically immediate.
    verify(streamBridge, timeout(500).times(1)).send(eq("orderCreatedEventProducer-out-0"), any());
  }

  @Test
  void outboxMessageRelayer_shouldNotDeleteMessageOnPublishFailure() {
    // Given
    Long customerId = 4L; // Using a different customer ID for this test case
    OrderItem item = new OrderItem();
    item.setProductId(404L);
    item.setQuantity(1);
    item.setUnitPrice(BigDecimal.valueOf(30.00));
    item.setSubtotal(BigDecimal.valueOf(30.00));
    List<OrderItem> items = Collections.singletonList(item);

    // First, create an order. This will place a message into the outbox table.
    // Since we are not mocking ObjectMapper, the actual ObjectMapper bean will serialize the event.
    Order createdOrder = orderService.createOrder(customerId, items).block();

    assertNotNull(createdOrder);
    assertThat(outboxMessageRepository.findAll())
        .hasSize(1); // Confirm the message is in the outbox

    // When
    // Simulate StreamBridge.send() throwing an exception, as if the message broker is unavailable.
    // This makes the 'send' call within the relayer fail.
    doThrow(new RuntimeException("Simulated RabbitMQ connection error"))
        .when(streamBridge)
        .send(eq("orderCreatedEventProducer-out-0"), any());

    // Manually trigger the outbox message relayer.
    // In a real application, this would be periodically triggered by @Scheduled.
    outboxMessageRelayer.processOutboxMessages();

    // Then
    // Verify that StreamBridge.send was attempted exactly once, even though it failed.
    // 'timeout(500)' gives a small buffer for the mock interaction to register.
    verifyOrderCreatedEventIsPublished();

    // Crucially: Verify that the message was NOT deleted from the outbox table.
    // If the publishing failed, the message should remain in the outbox
    // so it can be picked up and retried by the relayer in its next run.
    assertThat(outboxMessageRepository.findAll()).hasSize(1);

    // Optional: Verify the message is in the outbox
    List<OutboxMessage> remainingMessages = outboxMessageRepository.findAll();
    assertTrue(
        remainingMessages.stream()
            .anyMatch(
                remainingMessage ->
                    remainingMessage
                        .getAggregateId()
                        .equalsIgnoreCase(createdOrder.getId().toString())));
  }

  @Test
  void stockReservedEventConsumer_shouldConfirmOrder_StockReservedEvent() {}

  @Test
  void stockReservedEventConsumer_shouldConfirmOrderOnce_DuplicateStockReservedEvent() {}

  @Test
  void stockReservedEventConsumer_shouldNotConfirmOrder_AlreadyConfirmedOrder() {}

  @Test
  void stockReservedEventConsumer_shouldNotDoAnything_OrderNotFound() {}

  @Test
  void stockReservationFailedEventConsumer_shouldFailOrder_StockReservationFailedEvent() {}

  @Test
  void stockReservationFailedEventConsumer_shouldFailOrderOnce_DupStockReservationFailedEvent() {}

  @Test
  void stockReservationFailedEventConsumer_shouldNotFailOrder_AlreadyConfirmedOrder() {}

  @Test
  void stockReservationFailedEventConsumer_shouldNotFailOrder_AlreadyFailedOrder() {}

  @Test
  void stockReservationFailedEventConsumer_shouldNotDoAnything_OrderNotFound() {}

  private void verifyOrderCreatedEventIsNotPublished() {
    verify(streamBridge, never()).send(any(), any());
  }

  private void verifyOrderCreatedEventIsSavedInOutboxTableOnlyOnce(
      Order createdOrder, Long customerId, OrderItem item) {
    List<OutboxMessage> outboxMessages = outboxMessageRepository.findAll();
    assertThat(outboxMessages).hasSize(1);
    assertThat(outboxMessages.get(0).getAggregateType()).isEqualTo("Order");
    assertThat(outboxMessages.get(0).getAggregateId()).isEqualTo(createdOrder.getId().toString());
    assertThat(outboxMessages.get(0).getEventType()).isEqualTo("OrderCreatedEvent");
    // Verify a part of the payload to ensure the correct event data is stored
    assertThat(outboxMessages.get(0).getPayload()).contains("\"orderId\":" + createdOrder.getId());
    assertThat(outboxMessages.get(0).getPayload()).contains("\"customerId\":" + customerId);
    assertThat(outboxMessages.get(0).getPayload()).contains("\"productId\":" + item.getProductId());
  }

  private void verifyOrderIsCreatedInOrdersTable(Order createdOrder, Long customerId) {
    assertThat(createdOrder).isNotNull();
    assertThat(createdOrder.getId()).isNotNull();
    assertThat(createdOrder.getCustomerId()).isEqualTo(customerId);
    assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(createdOrder.getOrderItems()).hasSize(1);
    assertThat(createdOrder.getTotalAmount()).isEqualTo(BigDecimal.valueOf(20.00));

    // Verify the order was actually saved in the database
    assertThat(orderRepository.findById(createdOrder.getId())).isPresent();
  }
}
