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
    this(failureThreshold, 30000);
  }

  public CircuitBreaker(int failureThreshold, long openStateTimeoutInMillis) {
    this.failureThreshold = failureThreshold;
    this.openStateTimeoutInMillis = openStateTimeoutInMillis;
  }

  public <T> T execute(Supplier<T> operation, T fallback) {
    checkAndTransitionToHalfOpen();

    if (isCircuitOpen()) {
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
    if (isCircuitOpen() && hasTimeoutElapsedSinceLastFailure()) {
      transitionToHalfOpen();
    }
  }

  private boolean hasTimeoutElapsedSinceLastFailure() {
    return System.currentTimeMillis() - lastFailureTimestamp >= openStateTimeoutInMillis;
  }

  private void onSuccess() {
    successCount++;
    if (isCircuitHalfOpen()) {
      transitionToClosed();
    }
  }

  private void onFailure() {
    failureCount++;

    if (isCircuitHalfOpen() || failureCount >= failureThreshold) {
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

  private boolean isCircuitOpen() {
    return state == CircuitBreakerState.OPEN;
  }

  private boolean isCircuitHalfOpen() {
    return state == CircuitBreakerState.HALF_OPEN;
  }

  private boolean isCircuitClosed() {
    return state == CircuitBreakerState.CLOSED;
  }

  public double getFailureRate() {
    int totalCalls = successCount + failureCount;
    return totalCalls == 0 ? 0.0 : (double) failureCount / totalCalls;
  }

  public int getTotalCalls() {
    return successCount + failureCount;
  }

  public boolean isCallPermitted() {
    return isCircuitClosed()
        || (isCircuitOpen() && hasTimeoutElapsedSinceLastFailure());
  }

  // For testing - reset the circuit breaker
  public void reset() {
    failureCount = 0;
    successCount = 0;
    state = CircuitBreakerState.CLOSED;
    lastFailureTimestamp = 0;
  }
}
