package com.scalableshop.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalableshop.events.event.OrderCreatedEvent;
import com.scalableshop.orderservice.model.Order;
import com.scalableshop.orderservice.model.OutboxMessage;
import com.scalableshop.orderservice.repository.OutboxMessageRepository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderOutboxService {
  private static final Logger log = LoggerFactory.getLogger(OrderOutboxService.class);
  private final OutboxMessageRepository outboxMessageRepository;
  private final ObjectMapper objectMapper;

  public OrderOutboxService(
      OutboxMessageRepository outboxMessageRepository, ObjectMapper objectMapper) {
    this.outboxMessageRepository = outboxMessageRepository;
    this.objectMapper = objectMapper;
  }

  public void saveOrderCreatedEvent(Order order) {
    try {
      OrderCreatedEvent event = this.buildOrderCreatedEvent(order);
      String eventPayload = objectMapper.writeValueAsString(event);

      OutboxMessage outboxMessage =
          new OutboxMessage("Order", order.getId().toString(), "OrderCreatedEvent", eventPayload);
      outboxMessageRepository.save(outboxMessage);
      log.info("OrderCreatedEvent added to outbox for Order ID: {}", order.getId());
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize OrderCreatedEvent for Order ID: {}", order.getId(), e);
      throw new RuntimeException("Failed to serialize OrderCreatedEvent", e);
    }
  }

  @NotNull
  private OrderCreatedEvent buildOrderCreatedEvent(Order savedOrder) {
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
}
