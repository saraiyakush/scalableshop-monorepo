package com.scalableshop.circuitbreaker;

import lombok.Getter;

import java.util.function.Supplier;

@Getter
public class CircuitBreaker {
  private final int failureThreshold;
  private final long openStateTimeoutInMillis;
  private int failureCount = 0;
  private int successCount = 0;
  private CircuitBreakerState state = CircuitBreakerState.CLOSED;
  private long lastFailureTimestamp = 0;

  /**
   * CircuitBreaker with configurable failure threshold and open-state timeout. Defaults:
   * failureThreshold = 3, openStateTimeoutInMillis = 30000
   */
  public CircuitBreaker() {
    this(3, 30000);
  }

  public CircuitBreaker(int failureThreshold) {
    this.failureThreshold = failureThreshold;
    this.openStateTimeoutInMillis = 30000;
  }

  public CircuitBreaker(int failureThreshold, long openStateTimeoutInMillis) {
    this.failureThreshold = failureThreshold;
    this.openStateTimeoutInMillis = openStateTimeoutInMillis;
  }

  public <T> T execute(Supplier<T> operation, T fallback) {
    checkAndTransitionToHalfOpen();

    if (state == CircuitBreakerState.OPEN) {
      return fallback;
    }

    try {
      T result = operation.get();
      onSuccess();
      return result;
    } catch (Exception e) {
      onFailure();
      return fallback;
    }
  }

  private void checkAndTransitionToHalfOpen() {
    if (state == CircuitBreakerState.OPEN && hasTimeoutElapsedSinceLastFailure()) {
      transitionToHalfOpen();
    }
  }

  private boolean hasTimeoutElapsedSinceLastFailure() {
    return System.currentTimeMillis() - lastFailureTimestamp >= openStateTimeoutInMillis;
  }

  private void onSuccess() {
    successCount++;
    if (state == CircuitBreakerState.HALF_OPEN) {
      transitionToClosed();
    }
  }

  private void onFailure() {
    failureCount++;

    if (state == CircuitBreakerState.HALF_OPEN) {
      transitionToOpen();
    } else if (failureCount >= failureThreshold) {
      transitionToOpen();
    }
  }

  private void transitionToClosed() {
    state = CircuitBreakerState.CLOSED;
    failureCount = 0;
  }

  private void transitionToOpen() {
    lastFailureTimestamp = System.currentTimeMillis();
    state = CircuitBreakerState.OPEN;
  }

  private void transitionToHalfOpen() {
    state = CircuitBreakerState.HALF_OPEN;
  }

  public double getFailureRate() {
    int totalCalls = successCount + failureCount;
    return totalCalls == 0 ? 0.0 : (double) failureCount / totalCalls;
  }

  public int getTotalCalls() {
    return successCount + failureCount;
  }

  public boolean isCallPermitted() {
    return state == CircuitBreakerState.CLOSED
        || (state == CircuitBreakerState.OPEN && hasTimeoutElapsedSinceLastFailure());
  }

  // For testing - reset the circuit breaker
  public void reset() {
    failureCount = 0;
    successCount = 0;
    state = CircuitBreakerState.CLOSED;
    lastFailureTimestamp = 0;
  }
}
