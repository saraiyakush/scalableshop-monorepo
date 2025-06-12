package com.scalableshop.events.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockReservedEvent {
  private Long orderId;
  private Long customerId; // Useful for order service to identify the order
  private LocalDateTime eventTimestamp;
  private List<ReservedItem> reservedItems;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ReservedItem {
    private Long productId;
    private Integer quantityReserved;
  }

  // Constructor to easily create the event
  public StockReservedEvent(Long orderId, Long customerId, List<ReservedItem> reservedItems) {
    this.orderId = orderId;
    this.customerId = customerId;
    this.eventTimestamp = LocalDateTime.now();
    this.reservedItems = reservedItems;
  }
}
