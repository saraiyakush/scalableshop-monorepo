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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
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
  private final StreamBridge streamBridge;
  private final ObjectMapper objectMapper;

  @Autowired
  public OrderService(
      OrderRepository orderRepository,
      StreamBridge streamBridge,
      OutboxMessageRepository outboxMessageRepository,
      ObjectMapper objectMapper) {
    this.orderRepository = orderRepository;
    this.streamBridge = streamBridge;
    this.outboxMessageRepository = outboxMessageRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Mono<Order> createOrder(Long customerId, List<OrderItem> items) {
    return Mono.fromCallable(
        () -> {
          log.info("Creating new order for customerId: {}", customerId);
          Order order = new Order();
          order.setCustomerId(customerId);
          order.setStatus(OrderStatus.PENDING); // Initial status
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

          try {
            // Map the internal OrderItems to the event-specific OrderCreatedEvent.OrderItemEvent
            // structure
            List<OrderCreatedEvent.OrderItemEvent> eventItems =
                savedOrder.getOrderItems().stream()
                    .map(
                        orderItem ->
                            new OrderCreatedEvent.OrderItemEvent(
                                orderItem.getProductId(), orderItem.getQuantity()))
                    .collect(Collectors.toList());

            // Create the OrderCreatedEvent object
            OrderCreatedEvent event =
                new OrderCreatedEvent(
                    savedOrder.getId(),
                    savedOrder.getCustomerId(),
                    savedOrder.getOrderDate(),
                    savedOrder.getTotalAmount(),
                    eventItems // Pass the converted list of event items
                    );
            // Serialize the event to JSON
            String eventPayload = objectMapper.writeValueAsString(event);

            // Create and save the OutboxMessage within the same transaction
            OutboxMessage outboxMessage =
                new OutboxMessage(
                    "Order", // aggregateType
                    savedOrder.getId().toString(), // aggregateId (convert Long to String)
                    "OrderCreatedEvent", // eventType
                    eventPayload // payload
                    );
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

          return savedOrder;
        });
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
