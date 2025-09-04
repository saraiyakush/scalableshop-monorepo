package com.scalableshop.circuitbreaker;

import lombok.Getter;

import java.util.function.Supplier;

@Getter
public class CircuitBreaker {
    private final int failureThreshold;
    private final long timeoutInMillis;
    private int failureCount = 0;
    private int successCount = 0;
    private CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private long lastFailureTime = 0;

    /**
     * Default constructor with default values: failureThreshold = 3, timeoutInMillis = 30000
     */
    public CircuitBreaker() {
        this(3, 30000);
    }

    /**
     * Constructor with provided failureThreshold and default timeoutInMillis = 30000
     *
     * @param failureThreshold
     */
    public CircuitBreaker(int failureThreshold) {
        this.failureThreshold = failureThreshold;
        this.timeoutInMillis = 30000;
    }

    /**
     * Constructor with provided failureThreshold and timeoutInMillis
     *
     * @param failureThreshold
     * @param timeoutInMillis
     */
    public CircuitBreaker(int failureThreshold, long timeoutInMillis) {
        this.failureThreshold = failureThreshold;
        this.timeoutInMillis = timeoutInMillis;
    }

    public <T> T execute(Supplier<T> operation, T fallback) {
        // Check if circuit should transition from OPEN to HALF_OPEN
        if (state == CircuitBreakerState.OPEN && hasTimeoutElapsedSinceLastFailure()) {
            state = CircuitBreakerState.HALF_OPEN;
        }

        // If circuit is OPEN, return fallback immediately (fast failure)
        if (state == CircuitBreakerState.OPEN) {
            return fallback;
        }

        // Execute the operation (CLOSED or HALF_OPEN state)
        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            return fallback;
        }
    }

    private boolean hasTimeoutElapsedSinceLastFailure() {
        return System.currentTimeMillis() - lastFailureTime >= timeoutInMillis;
    }

    private void onSuccess() {
        successCount++;
        if (state == CircuitBreakerState.HALF_OPEN) {
            // Recovery successful - close the circuit and reset failure count
            state = CircuitBreakerState.CLOSED;
            failureCount = 0;
        }
    }

    private void onFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();

        if (state == CircuitBreakerState.HALF_OPEN) {
            // Test failed - go back to OPEN
            state = CircuitBreakerState.OPEN;
        } else if (failureCount >= failureThreshold) {
            // Threshold exceeded - open the circuit
            state = CircuitBreakerState.OPEN;
        }
    }

    public double getFailureRate() {
        int totalCalls = successCount + failureCount;
        return totalCalls == 0 ? 0.0 : (double) failureCount / totalCalls;
    }

    public int getTotalCalls() {
        return successCount + failureCount;
    }

    public boolean isCallPermitted() {
        return state == CircuitBreakerState.CLOSED ||
                (state == CircuitBreakerState.OPEN && hasTimeoutElapsedSinceLastFailure());
    }

    // For testing - reset the circuit breaker
    public void reset() {
        failureCount = 0;
        successCount = 0;
        state = CircuitBreakerState.CLOSED;
        lastFailureTime = 0;
    }
}
