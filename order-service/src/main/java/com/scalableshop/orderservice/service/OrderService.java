package com.scalableshop.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalableshop.events.event.OrderCreatedEvent;
import com.scalableshop.orderservice.model.Order;
import com.scalableshop.orderservice.model.OrderItem;
import com.scalableshop.orderservice.model.OrderStatus;
import com.scalableshop.orderservice.model.OutboxMessage;
import com.scalableshop.orderservice.repository.OrderRepository;
import com.scalableshop.orderservice.repository.OutboxMessageRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private final OrderRepository orderRepository;
  private final OutboxMessageRepository outboxMessageRepository;
  private final ObjectMapper objectMapper;

  @Autowired
  public OrderService(
      OrderRepository orderRepository,
      OutboxMessageRepository outboxMessageRepository,
      ObjectMapper objectMapper) {
    this.orderRepository = orderRepository;
    this.outboxMessageRepository = outboxMessageRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Mono<Order> createOrder(Long customerId, List<OrderItem> items) {
    return Mono.fromCallable(
        () -> {
          log.info("Creating new order for customerId: {}", customerId);
          Order savedOrder = createOrderWithPendingStatus(customerId, items);
          saveOrderCreatedEventInOutbox(savedOrder);
          return savedOrder;
        });
  }

  @NotNull
  private Order createOrderWithPendingStatus(Long customerId, List<OrderItem> items) {
    Order order = new Order();
    order.setCustomerId(customerId);
    order.setStatus(OrderStatus.PENDING);
    BigDecimal totalAmount = BigDecimal.ZERO;

    for (OrderItem item : items) {
      // Important: In a real scenario, you'd fetch product details (price, name)
      // from Product Catalog Service here to ensure current data and consistency.
      // For now, assume unitPrice and productName are provided in the DTO.
      order.addOrderItem(item);
      totalAmount = totalAmount.add(item.getSubtotal());
    }
    order.setTotalAmount(totalAmount);

    Order savedOrder = orderRepository.save(order);
    log.info("Order created successfully with ID: {}", savedOrder.getId());
    return savedOrder;
  }

  private void saveOrderCreatedEventInOutbox(Order savedOrder) {
    try {
      OrderCreatedEvent event = buildOrderCreatedEvent(savedOrder);
      String eventPayload = objectMapper.writeValueAsString(event);

      OutboxMessage outboxMessage =
          new OutboxMessage(
              "Order", savedOrder.getId().toString(), "OrderCreatedEvent", eventPayload);
      outboxMessageRepository.save(outboxMessage);
      log.info("OrderCreatedEvent added to outbox for Order ID: {}", savedOrder.getId());

    } catch (JsonProcessingException e) {
      log.error(
          "Failed to serialize OrderCreatedEvent for Order ID: {}. Event will not be published.",
          savedOrder.getId(),
          e);
      // For Transactional Outbox, we want this to fail the transaction if serialization
      // fails.
      throw new RuntimeException("Failed to serialize OrderCreatedEvent", e);
    }
  }

  @NotNull
  private static OrderCreatedEvent buildOrderCreatedEvent(Order savedOrder) {
    List<OrderCreatedEvent.OrderItemEvent> eventItems =
        savedOrder.getOrderItems().stream()
            .map(
                orderItem ->
                    new OrderCreatedEvent.OrderItemEvent(
                        orderItem.getProductId(), orderItem.getQuantity()))
            .collect(Collectors.toList());

    return new OrderCreatedEvent(
        savedOrder.getId(),
        savedOrder.getCustomerId(),
        savedOrder.getOrderDate(),
        savedOrder.getTotalAmount(),
        eventItems);
  }

  public Mono<Order> getOrderById(Long orderId) {
    return Mono.fromCallable(
        () -> {
          log.info("Fetching order with ID: {}", orderId);
          return orderRepository
              .findById(orderId)
              .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));
        });
  }
}
