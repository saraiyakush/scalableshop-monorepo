package com.scalableshop.productinventoryservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "processed_order_events") // Table to store processed event IDs
public class ProcessedOrderEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // This unique constraint is crucial for idempotency
  @Column(unique = true, nullable = false)
  private Long orderId; // The ID of the order from the OrderCreatedEvent

  @Column(nullable = false)
  private LocalDateTime processedAt;

  // Custom constructor if needed, Lombok's @AllArgsConstructor covers basic needs
  public ProcessedOrderEvent(Long orderId, LocalDateTime processedAt) {
    this.orderId = orderId;
    this.processedAt = processedAt;
  }
}
