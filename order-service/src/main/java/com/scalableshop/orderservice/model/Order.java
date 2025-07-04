package com.scalableshop.orderservice.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_orders")
@Data
@ToString(exclude = "orderItems")
@NoArgsConstructor
@AllArgsConstructor
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long customerId; // Placeholder for customer ID
  private LocalDateTime orderDate;

  @Enumerated(EnumType.STRING)
  private OrderStatus status;

  private BigDecimal totalAmount;

  @OneToMany(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @JsonManagedReference
  private List<OrderItem> orderItems = new ArrayList<>();

  public void addOrderItem(OrderItem item) {
    this.orderItems.add(item);
    item.setOrder(this);
  }

  @PrePersist
  protected void onCreate() {
    if (orderDate == null) {
      orderDate = LocalDateTime.now();
    }
    if (status == null) {
      status = OrderStatus.PENDING; // Default status
    }
  }
}
