package com.scalableshop.orderservice.service;

import com.scalableshop.orderservice.model.Order;
import com.scalableshop.orderservice.model.OrderItem;
import com.scalableshop.orderservice.model.OrderStatus;
import com.scalableshop.orderservice.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private final OrderRepository orderRepository;

  @Autowired
  public OrderService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
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
