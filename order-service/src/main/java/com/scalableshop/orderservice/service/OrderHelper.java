package com.scalableshop.orderservice.service;

import com.scalableshop.events.event.StockReservationFailedEvent;
import com.scalableshop.events.event.StockReservedEvent;
import com.scalableshop.orderservice.model.Order;
import com.scalableshop.orderservice.model.OrderStatus;
import com.scalableshop.orderservice.model.ProcessedInventoryEvent;
import com.scalableshop.orderservice.repository.OrderRepository;
import com.scalableshop.orderservice.repository.ProcessedInventoryEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class OrderHelper {

  private static final Logger log = LoggerFactory.getLogger(OrderHelper.class);

  private final OrderRepository orderRepository;
  private final ProcessedInventoryEventRepository processedInventoryEventRepository;

  @Autowired
  public OrderHelper(
      OrderRepository orderRepository,
      ProcessedInventoryEventRepository processedInventoryEventRepository) {
    this.orderRepository = orderRepository;
    this.processedInventoryEventRepository = processedInventoryEventRepository;
  }

  @Transactional
  public void confirmOrder(StockReservedEvent event, String eventId) {
    Long orderId = event.getOrderId();
    String eventType = "StockReservedEvent";
    log.info(
        "OrderHelper: Attempting to confirm order for Order ID: {} with event ID: {}",
        orderId,
        eventId);

    try {
      // Idempotency check: Record this event as processed
      // DataIntegrityViolationException will occur if (orderId, eventType) already exists
      processedInventoryEventRepository.save(
          new ProcessedInventoryEvent(orderId, eventType, LocalDateTime.now()));
      log.debug(
          "OrderHelper: ProcessedInventoryEvent recorded for Order ID: {}, Event Type: {}",
          orderId,
          eventType);

      // Find the order and update its status
      Optional<Order> optionalOrder = orderRepository.findById(orderId);
      if (optionalOrder.isPresent()) {
        Order order = optionalOrder.get();
        // Update only if current status is PENDING
        if (order.getStatus() == OrderStatus.PENDING) {
          order.setStatus(OrderStatus.CONFIRMED);
          orderRepository.save(order);
          log.info("OrderHelper: Order ID: {} status updated to CONFIRMED.", orderId);
          // TODO: Publish OrderConfirmedEvent or PaymentInitiatedEvent
        } else {
          log.warn(
              "OrderHelper: Order ID: {} is not in PENDING status (current: {}). Skipping status update.",
              orderId,
              order.getStatus());
        }
      } else {
        log.warn(
            "OrderHelper: Order ID: {} not found in order service. Cannot update status after stock reservation.",
            orderId);
      }
    } catch (DataIntegrityViolationException e) {
      log.warn(
          "OrderHelper: StockReservedEvent for Order ID: {} (Event Type: {}) has already been processed or is being processed concurrently. Skipping.",
          orderId,
          eventType);
      // No need to re-process or re-publish in case of duplicate event
    } catch (Exception e) {
      log.error(
          "OrderHelper: An unexpected error occurred while confirming order for Order ID: {}: {}",
          orderId,
          e.getMessage(),
          e);
      // Handle other exceptions if necessary
    }
  }

  @Transactional
  public void failOrder(StockReservationFailedEvent event, String eventId) {
    Long orderId = event.getOrderId();
    String eventType = "StockReservationFailedEvent";
    log.info(
        "OrderHelper: Attempting to fail order for Order ID: {} with event ID: {}. Reason: {}",
        event.getOrderId(),
        eventId,
        event.getReason());

    try {
      // Idempotency check: Record this event as processed
      // DataIntegrityViolationException will occur if (orderId, eventType) already exists
      processedInventoryEventRepository.save(
          new ProcessedInventoryEvent(orderId, eventType, LocalDateTime.now()));
      log.debug(
          "OrderHelper: ProcessedInventoryEvent recorded for Order ID: {}, Event Type: {}",
          orderId,
          eventType);

      // Find the order and update its status
      Optional<Order> optionalOrder = orderRepository.findById(orderId);
      if (optionalOrder.isPresent()) {
        Order order = optionalOrder.get();
        // Update only if current status is PENDING
        if (order.getStatus() == OrderStatus.PENDING) {
          order.setStatus(OrderStatus.FAILED);
          orderRepository.save(order);
          log.info("OrderHelper: Order ID: {} status updated to FAILED.", orderId);
          // TODO: Publish OrderCancelledEvent or PaymentRefundInitiatedEvent
        } else {
          log.warn(
              "OrderHelper: Order ID: {} is not in PENDING status (current: {}). Skipping status update.",
              orderId,
              order.getStatus());
        }
      } else {
        log.warn(
            "OrderHelper: Order ID: {} not found in order service. Cannot update status after stock reservation failure.",
            orderId);
      }
    } catch (DataIntegrityViolationException e) {
      log.warn(
          "OrderHelper: StockReservationFailedEvent for Order ID: {} (Event Type: {}) has already been processed or is being processed concurrently. Skipping.",
          orderId,
          eventType);
      // No need to re-process or re-publish in case of duplicate event
    } catch (Exception e) {
      log.error(
          "OrderHelper: An unexpected error occurred while failing order for Order ID: {}: {}",
          orderId,
          e.getMessage(),
          e);
      // Handle other exceptions if necessary
    }
  }
}
