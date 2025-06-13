package com.scalableshop.productinventoryservice.repository;

import com.scalableshop.productinventoryservice.model.ProcessedOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedOrderEventRepository extends JpaRepository<ProcessedOrderEvent, Long> {
  // We might not directly use findByOrderId, but it's good practice for potential future needs.
  Optional<ProcessedOrderEvent> findByOrderId(Long orderId);
}
