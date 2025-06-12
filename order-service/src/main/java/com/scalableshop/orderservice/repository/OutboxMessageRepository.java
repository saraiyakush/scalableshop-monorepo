package com.scalableshop.orderservice.repository;

import com.scalableshop.orderservice.model.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    // Method to find a batch of messages to publish.
    // We'll typically order by createdAt to maintain message order and limit for batching.
    // This simple query will fetch all, but for a real system, you'd add status/limit/pagination.
    List<OutboxMessage> findTop100ByOrderByCreatedAtAsc(); // Example: fetch top 100 oldest messages
}