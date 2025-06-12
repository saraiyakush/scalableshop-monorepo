package com.scalableshop.events.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationFailedEvent {
  private Long orderId;
  private Long customerId; // Useful for order service
  private LocalDateTime eventTimestamp;
  private String reason;
  private List<FailedItem> failedItems; // To specify which items failed reservation

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FailedItem {
    private Long productId;
    private Integer requestedQuantity;
    private Integer availableQuantity; // How much was actually available
  }

  // Constructor to easily create the event
  public StockReservationFailedEvent(
      Long orderId, Long customerId, String reason, List<FailedItem> failedItems) {
    this.orderId = orderId;
    this.customerId = customerId;
    this.eventTimestamp = LocalDateTime.now();
    this.reason = reason;
    this.failedItems = failedItems;
  }
}
