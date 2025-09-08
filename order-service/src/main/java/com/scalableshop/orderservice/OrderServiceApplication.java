package com.scalableshop.orderservice;

import com.scalableshop.circuitbreaker.CircuitBreaker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableJpaRepositories
@EnableScheduling
public class OrderServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderServiceApplication.class, args);
  }

  @Bean
  public CircuitBreaker productCircuitBreaker() {
    return new CircuitBreaker(3, 30000); // Example: 5 failures, 2 seconds timeout
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
