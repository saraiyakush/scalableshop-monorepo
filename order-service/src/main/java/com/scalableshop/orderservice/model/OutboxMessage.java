package com.scalableshop.orderservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_messages")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID) // Using UUID for robust unique IDs
  private UUID id;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType; // e.g., "Order", "Payment"

  @Column(name = "aggregate_id", nullable = false)
  private String aggregateId; // ID of the business entity (e.g., order ID)

  @Column(name = "event_type", nullable = false)
  private String eventType; // e.g., "OrderCreatedEvent", "PaymentProcessedEvent"

  @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
  private String payload; // JSON string of the event data

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now(); // Initialized at field level

  public OutboxMessage(String aggregateType, String aggregateId, String eventType, String payload) {
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
  }
}
