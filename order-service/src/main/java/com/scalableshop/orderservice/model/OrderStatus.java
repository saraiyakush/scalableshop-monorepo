package com.scalableshop.orderservice.model;

public enum OrderStatus {
  PENDING, // Order just created, awaiting stock check/payment
  CONFIRMED, // Stock reserved, payment successful
  SHIPPED, // Order has been shipped
  DELIVERED, // Order delivered to customer
  CANCELLED, // Order cancelled by customer or system
  FAILED // Order failed due to stock, payment, etc.
}
