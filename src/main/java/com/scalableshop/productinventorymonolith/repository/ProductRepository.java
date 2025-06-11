package com.scalableshop.productinventorymonolith.repository;

import com.scalableshop.productinventorymonolith.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {}
