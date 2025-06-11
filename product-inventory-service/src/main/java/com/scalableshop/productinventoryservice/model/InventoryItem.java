package com.scalableshop.productinventoryservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name =
        "inventory_items") // Renamed from 'inventory' to avoid conflict with potential 'inventory'
// concept
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // Internal ID for this inventory record

  private Long
      productId; // The ID of the product this inventory item refers to (from Catalog Service)
  private Integer quantityAvailable;
  private Integer quantityReserved; // For future order processing

  // Constructor for initial creation, without internal ID
  public InventoryItem(Long productId, Integer quantityAvailable, Integer quantityReserved) {
    this.productId = productId;
    this.quantityAvailable = quantityAvailable;
    this.quantityReserved = quantityReserved;
  }
}
