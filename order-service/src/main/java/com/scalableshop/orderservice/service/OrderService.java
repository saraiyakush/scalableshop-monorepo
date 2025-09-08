package com.scalableshop.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalableshop.events.event.OrderCreatedEvent;
import com.scalableshop.events.event.StockReservationFailedEvent;
import com.scalableshop.events.event.StockReservedEvent;
import com.scalableshop.orderservice.client.ProductCatalogServiceClient;
import com.scalableshop.orderservice.client.ProductDetails;
import com.scalableshop.orderservice.model.*;
import com.scalableshop.orderservice.repository.OrderRepository;
import com.scalableshop.orderservice.repository.OutboxMessageRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private final OrderRepository orderRepository;
  private final OutboxMessageRepository outboxMessageRepository;
  private final OrderHelper orderHelper;
  private final ObjectMapper objectMapper;
  private final OrderOutboxService orderOutboxService;
  private final ProductCatalogServiceClient productCatalogServiceClient;

  @Autowired
  public OrderService(
      OrderRepository orderRepository,
      OutboxMessageRepository outboxMessageRepository,
      OrderHelper orderHelper,
      ObjectMapper objectMapper,
      OrderOutboxService orderOutboxService,
      ProductCatalogServiceClient productCatalogServiceClient) {
    this.orderRepository = orderRepository;
    this.outboxMessageRepository = outboxMessageRepository;
    this.orderHelper = orderHelper;
    this.objectMapper = objectMapper;
    this.orderOutboxService = orderOutboxService;
    this.productCatalogServiceClient = productCatalogServiceClient;
  }

  @Transactional
  public Mono<Order> createOrder(Long customerId, List<OrderItem> items) {
    return Mono.fromCallable(
        () -> {
          log.info("Creating new order for customerId: {}", customerId);

          updateItemsWithLatestPrice(items);

          Order savedOrder = createOrderWithPendingStatus(customerId, items);
          orderOutboxService.saveOrderCreatedEvent(savedOrder);
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

  private void updateItemsWithLatestPrice(List<OrderItem> items) {
    for (OrderItem item : items) {
      ProductDetails productDetails =
          productCatalogServiceClient.getProductDetails(String.valueOf(item.getProductId()));

      if (productDetails.isFallbackUsed()) {
        // Circuit breaker fallback was used, meaning the product service is down
        // Depending on business requirements, you might want to:
        // 1. Reject the order creation
        // 2. Allow order creation but mark it for manual review
        // Here, we'll log a warning and proceed with a note
        log.warn(
            "Product service is currently unavailable. Proceeding with fallback for productId: {}",
            item.getProductId());
      }
    }
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

  @Bean
  public Consumer<StockReservedEvent> stockReservedEventConsumer() {
    return event -> {
      String eventId = "StockReservedEvent-" + event.getOrderId();
      log.info(
          "Received StockReservedEvent for Order ID: {} with event ID: {}",
          event.getOrderId(),
          eventId);

      try {
        orderHelper.confirmOrder(event, eventId);
        log.info(
            "OrderService Consumer: StockReservedEvent for Order ID: {} processed successfully by helper.",
            event.getOrderId());
      } catch (DataIntegrityViolationException e) {
        log.warn(
            "OrderService Consumer: StockReservedEvent for Order ID: {} (Event ID: {}) has already been processed or is being processed concurrently. Skipping.",
            event.getOrderId(),
            eventId);
      } catch (Exception e) {
        log.error(
            "OrderService Consumer: Error processing StockReservedEvent for Order ID: {}: {}",
            event.getOrderId(),
            e.getMessage(),
            e);
      }
    };
  }

  @Bean
  public Consumer<StockReservationFailedEvent> stockReservationFailedEventConsumer() {
    return event -> {
      String eventId = "StockReservationFailedEvent-" + event.getOrderId();
      log.info(
          "Received StockReservationFailedEvent for Order ID: {} with event ID: {}. Reason: {}",
          event.getOrderId(),
          eventId,
          event.getReason());

      try {
        orderHelper.failOrder(event, eventId); // Delegated call, renamed method
        log.info(
            "OrderService Consumer: StockReservationFailedEvent for Order ID: {} processed successfully by helper.",
            event.getOrderId());
      } catch (DataIntegrityViolationException e) {
        log.warn(
            "OrderService Consumer: StockReservationFailedEvent for Order ID: {} (Event ID: {}) has already been processed or is being processed concurrently. Skipping.",
            event.getOrderId(),
            eventId);
      } catch (Exception e) {
        log.error(
            "OrderService Consumer: Error processing StockReservationFailedEvent for Order ID: {}: {}",
            event.getOrderId(),
            e.getMessage(),
            e);
      }
    };
  }
}
