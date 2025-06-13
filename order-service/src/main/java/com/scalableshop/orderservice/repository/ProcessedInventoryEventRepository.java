package com.scalableshop.orderservice.repository;

import com.scalableshop.orderservice.model.ProcessedInventoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedInventoryEventRepository extends JpaRepository<ProcessedInventoryEvent, String> {}
