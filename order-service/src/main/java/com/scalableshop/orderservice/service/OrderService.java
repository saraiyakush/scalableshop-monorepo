package com.scalableshop.orderservice.service;

import com.scalableshop.events.event.OrderCreatedEvent;
import com.scalableshop.orderservice.model.Order;
import com.scalableshop.orderservice.model.OrderItem;
import com.scalableshop.orderservice.model.OrderStatus;
import com.scalableshop.orderservice.repository.OrderRepository;
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
  private final StreamBridge streamBridge;

  @Autowired
  public OrderService(OrderRepository orderRepository, StreamBridge streamBridge) {
    this.orderRepository = orderRepository;
    this.streamBridge = streamBridge;
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
          publishOrderCreatedEvent(savedOrder);
          return savedOrder;
        });
  }

  private void publishOrderCreatedEvent(Order order) {
    // Map OrderItem entities to OrderItemEvent DTOs
    List<OrderCreatedEvent.OrderItemEvent> itemEvents =
        order.getOrderItems().stream()
            .map(
                item ->
                    new OrderCreatedEvent.OrderItemEvent(item.getProductId(), item.getQuantity()))
            .collect(Collectors.toList());

    OrderCreatedEvent event =
        new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getOrderDate(),
            order.getTotalAmount(),
            itemEvents);

    // Send the event using StreamBridge to the 'orderCreatedEventProducer' binding
    streamBridge.send(
        "orderCreatedEventProducer-out-0", event); // Use the binding name configured in properties
    log.info("Published OrderCreatedEvent for Order ID: {}", order.getId());
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
