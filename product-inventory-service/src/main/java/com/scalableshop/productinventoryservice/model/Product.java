package com.scalableshop.productinventoryservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

  private Long id;

  private String name;

  private String description;

  private Double price;

  private Integer stock;
}
