package com.scalableshop.productcatalogservice.controller;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdateRequest {
  private Integer quantityChange;
}
