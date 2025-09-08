package com.scalableshop.orderservice.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetails {
  private Long productId;
  private String name;
  private BigDecimal price;
  private boolean available;
  private boolean fallbackUsed;
}
