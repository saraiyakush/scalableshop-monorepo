// scalableshop-monorepo/order-service/src/main/java/com/scalableshop/orderservice/model/OrderItem.java
package com.scalableshop.orderservice.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long productId; // The ID of the product from the Catalog Service

  // We could fetch actual product details from Catalog Service
  // For simplicity, we'll store name and price at the time of order
  // This is a common pattern for "snapshotting" data for historical accuracy
  private String productName;
  private BigDecimal unitPrice;

  private Integer quantity;
  private BigDecimal subtotal;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id")
  @JsonBackReference
  private Order order;

  @PrePersist
  @PreUpdate
  protected void calculateSubtotal() {
    if (unitPrice != null && quantity != null) {
      this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
  }
}
