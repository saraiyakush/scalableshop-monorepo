package com.scalableshop.events.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
  private Long orderId;
  private Long customerId;
  private LocalDateTime orderDate; // Snapshot of when the order was created
  private BigDecimal totalAmount;
  private List<OrderItemEvent> orderItems; // Details of items in the order

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OrderItemEvent {
    private Long productId;
    private Integer quantity;
    // Potentially add unitPrice here if inventory needs to verify against it,
    // but for simple stock reservation, productId and quantity are sufficient.
  }
}
