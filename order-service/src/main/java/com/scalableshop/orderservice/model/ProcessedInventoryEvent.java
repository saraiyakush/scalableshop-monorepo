package com.scalableshop.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "processed_inventory_events",
    uniqueConstraints = {
      @UniqueConstraint(
          columnNames = {"orderId", "eventType"},
          name = "UK_order_event_type")
    })
public class ProcessedInventoryEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long orderId;

  @Column(nullable = false)
  private String eventType;

  @Column(nullable = false)
  private LocalDateTime processedAt;

  public ProcessedInventoryEvent(Long orderId, String eventType, LocalDateTime processedAt) {
    this.orderId = orderId;
    this.eventType = eventType;
    this.processedAt = processedAt;
  }
}
