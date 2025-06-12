package com.scalableshop.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalableshop.events.event.OrderCreatedEvent;
import com.scalableshop.orderservice.model.OutboxMessage;
import com.scalableshop.orderservice.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxMessageRelayer {

  private static final Logger log = LoggerFactory.getLogger(OutboxMessageRelayer.class);

  private final OutboxMessageRepository outboxMessageRepository;
  private final StreamBridge streamBridge;
  private final ObjectMapper objectMapper;

  public OutboxMessageRelayer(
      OutboxMessageRepository outboxMessageRepository,
      StreamBridge streamBridge,
      ObjectMapper objectMapper) {
    this.outboxMessageRepository = outboxMessageRepository;
    this.streamBridge = streamBridge;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelay = 5000) // Run every 5 seconds
  @Transactional // Ensure delete is transactional
  public void processOutboxMessages() {
    // Fetch a batch of messages to process.
    // For a production system, consider adding a lock or using SELECT FOR UPDATE SKIP LOCKED
    // to prevent multiple relayer instances from processing the same message.
    List<OutboxMessage> messages = outboxMessageRepository.findTop100ByOrderByCreatedAtAsc();

    if (messages.isEmpty()) {
      // log.debug("No outbox messages to process."); // Uncomment for more verbose logging
      return;
    }

    log.info("Processing {} outbox messages...", messages.size());

    for (OutboxMessage message : messages) {
      try {
        // Deserialize the payload into the specific event type
        // Here we assume all messages in this outbox are OrderCreatedEvent.
        // In a multi-event outbox, you'd use a switch/if-else based on message.getEventType()
        OrderCreatedEvent event =
            objectMapper.readValue(message.getPayload(), OrderCreatedEvent.class);

        // Publish the message to RabbitMQ
        // The 'orderCreatedEventProducer-out-0' binding should be configured in
        // application.properties
        streamBridge.send("orderCreatedEventProducer-out-0", event);
        log.info(
            "Successfully published event '{}' for aggregateId '{}' to RabbitMQ.",
            message.getEventType(),
            message.getAggregateId());

        // Delete the message from the outbox after successful publication
        outboxMessageRepository.delete(message);
        log.debug("Deleted outbox message with ID: {}", message.getId());

      } catch (JsonProcessingException e) {
        log.error(
            "Failed to deserialize outbox message payload for ID: {}. Skipping.",
            message.getId(),
            e);
        // Consider moving to a dead-letter outbox table or marking as failed to avoid infinite
        // retries on malformed data.
      } catch (Exception e) {
        // Catch broader exceptions during sending (e.g., RabbitMQ connectivity issues)
        log.error(
            "Failed to send outbox message with ID: {}. Will retry later.", message.getId(), e);
        // The message remains in the outbox to be retried in the next scheduled run.
      }
    }
  }
}
