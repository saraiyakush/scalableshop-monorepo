package com.scalableshop.productinventorymonolith.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

  @GetMapping("/hello")
  public String hello() {
    return "Hello from the (now minimal) Monolith!";
  }
}
