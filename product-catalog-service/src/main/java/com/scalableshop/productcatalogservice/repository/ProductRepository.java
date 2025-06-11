package com.scalableshop.productcatalogservice.repository;

import com.scalableshop.productcatalogservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {}
