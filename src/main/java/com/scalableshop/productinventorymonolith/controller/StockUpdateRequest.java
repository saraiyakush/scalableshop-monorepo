package com.scalableshop.productinventorymonolith.controller;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdateRequest {
  private Integer quantityChange;
}
