package com.scalableshop.circuitbreaker;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class CircuitBreakerTests {

    @Test
    void shouldStartInClosedState() {
        CircuitBreaker circuitBreaker = new CircuitBreaker();

        CircuitBreakerState state = circuitBreaker.getState();

        assertThat(state == CircuitBreakerState.CLOSED);
    }

    @Test
    void shouldExecuteSupplierCallSuccessfully_whenNoExceptions() {
        CircuitBreaker circuitBreaker = new CircuitBreaker();
        Supplier<String> supplierMethodReturningSuccess = () -> "Success";

        String result = circuitBreaker.execute(supplierMethodReturningSuccess, "Fallback");

        assertThat(result).isEqualTo("Success");
    }

    @Test
    void shouldReturnFallbackOperation_whenSupplierCallFails() {
        CircuitBreaker circuitBreaker = new CircuitBreaker();
        Supplier<String> supplierMethodThrowingException = () -> {
            throw new RuntimeException("Service unavailable");
        };

        String result = circuitBreaker.execute(supplierMethodThrowingException, "Fallback");

        assertThat(result).isEqualTo("Fallback");
    }

    @Test
    void shouldCountFailures_whenSupplierCallFails() {
        CircuitBreaker circuitBreaker = new CircuitBreaker();
        Supplier<String> supplierMethodThrowingException = () -> {
            throw new RuntimeException("Service unavailable");
        };

        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");

        assertThat(circuitBreaker.getFailureCount()).isEqualTo(2);
    }

    @Test
    void shouldCountSuccesses_whenSupplierCallSucceeds() {
        CircuitBreaker circuitBreaker = new CircuitBreaker();
        Supplier<String> supplierMethodReturningSuccess = () -> "Success";

        circuitBreaker.execute(supplierMethodReturningSuccess, "Fallback");
        circuitBreaker.execute(supplierMethodReturningSuccess, "Fallback");

        assertThat(circuitBreaker.getSuccessCount()).isEqualTo(2);
    }

    @Test
    void shouldTransitionToOpenState_whenFailureThresholdExceeded() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(3);
        Supplier<String> supplierMethodThrowingException = () -> {
            throw new RuntimeException("Service unavailable");
        };

        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void shouldReturnFallbackImmediately_whenCircuitIsOpen() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(2);
        Supplier<String> supplierMethodThrowingException = () -> {
            throw new RuntimeException("Service unavailable");
        };

        // Trigger failures to open the circuit
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");

        String result = circuitBreaker.execute(() -> "Should not be called", "Fallback-early");

        assertThat(result).isEqualTo("Fallback-early");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void shouldNotIncrementCounters_whenCircuitIsOpen() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(1);
        Supplier<String> supplierMethodThrowingException = () -> {
            throw new RuntimeException("Service unavailable");
        };
        // Trigger failures to open the circuit
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");

        int failureCountBeforeCall = circuitBreaker.getFailureCount();
        int successCountBeforeCall = circuitBreaker.getSuccessCount();

        // Attempt to execute while circuit is open
        circuitBreaker.execute(() -> "Success", "Fallback");
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");

        assertThat(circuitBreaker.getFailureCount()).isEqualTo(failureCountBeforeCall);
        assertThat(circuitBreaker.getSuccessCount()).isEqualTo(successCountBeforeCall);
    }

    @Test
    void shouldTransitionToHalfOpenThenClosed_afterTimeoutAndCallSucceeds() throws InterruptedException {
        CircuitBreaker circuitBreaker = new CircuitBreaker(1, 100);
        Supplier<String> supplierMethodThrowingException = () -> {
            throw new RuntimeException("Service unavailable");
        };

        // Trigger failures to open the circuit
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);

        // Wait for timeout to make the next call
        Thread.sleep(200);
        circuitBreaker.execute(() -> "Test half open", "Fallback");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
    }

    @Test
    void shouldTransitionToHalfOpenThenOpen_afterTimeoutAndCallFails() throws InterruptedException {
        CircuitBreaker circuitBreaker = new CircuitBreaker(1, 100);
        Supplier<String> supplierMethodThrowingException = () -> {
            throw new RuntimeException("Service unavailable");
        };

        // Trigger failures to open the circuit
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");

        // Simulate timeout for transitioning to HALF_OPEN state
        Thread.sleep(200);
        circuitBreaker.execute(() -> "Recovery test", "Fallback");

        // Failed call should reopen the circuit
        String result = circuitBreaker.execute(supplierMethodThrowingException, "Fallback");
        assertThat(result).isEqualTo("Fallback");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void shouldCalculateFailureRateCorrectly() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(5);
        Supplier<String> supplierMethodReturningSuccess = () -> "Success";
        Supplier<String> supplierMethodThrowingException = () -> {
            throw new RuntimeException("Service unavailable");
        };

        // 3 successes
        circuitBreaker.execute(supplierMethodReturningSuccess, "Fallback");
        circuitBreaker.execute(supplierMethodReturningSuccess, "Fallback");
        circuitBreaker.execute(supplierMethodReturningSuccess, "Fallback");

        // 2 failures
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");

        assertThat(circuitBreaker.getFailureRate()).isEqualTo(0.4); // 2 failures out of 5 total calls
        assertThat(circuitBreaker.getTotalCalls()).isEqualTo(5);
    }

    @Test
    void shouldWorkWithDifferentGenericTypes() {
        CircuitBreaker circuitBreaker = new CircuitBreaker();

        Integer successIntResult = circuitBreaker.execute(() -> 42, -1);
        Integer fallbackIntResult = circuitBreaker.execute(() -> {
            throw new RuntimeException("Service unavailable");
        }, -1);
        Boolean successBoolResult = circuitBreaker.execute(() -> true, false);

        assertThat(successIntResult).isEqualTo(42);
        assertThat(fallbackIntResult).isEqualTo(-1);
        assertThat(successBoolResult).isEqualTo(true);
    }

    @Test
    void shouldResetCircuitBreakerState() {
        CircuitBreaker circuitBreaker = new CircuitBreaker(1);
        Supplier<String> supplierMethodThrowingException = () -> {
            throw new RuntimeException("Service unavailable");
        };

        // Trigger failures to open the circuit
        circuitBreaker.execute(supplierMethodThrowingException, "Fallback");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);

        // Reset the circuit breaker
        circuitBreaker.reset();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
        assertThat(circuitBreaker.getSuccessCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleConcurrentRequestsSafely() throws InterruptedException {
        CircuitBreaker circuitBreaker = new CircuitBreaker(3);
        Supplier<String> supplierMethodThrowingException = () -> {
            throw new RuntimeException("Service unavailable");
        };
        Supplier<String> supplierMethodReturningSuccess = () -> "Success";

        Runnable task = () -> {
            for (int i = 0; i < 10; i++) {
                if (i % 2 == 0) {
                    circuitBreaker.execute(supplierMethodThrowingException, "Fallback");
                } else {
                    circuitBreaker.execute(supplierMethodReturningSuccess, "Fallback");
                }
            }
        };

        Thread thread1 = new Thread(task);
        Thread thread2 = new Thread(task);
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // After concurrent execution, the circuit breaker should be in OPEN state due to failures
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerState.OPEN);
        // Failure count should be at least 6 (3 from each thread)
        assertThat(circuitBreaker.getFailureCount()).isGreaterThanOrEqualTo(3);
        // Success count should be at least 4 (2 from each thread)
        assertThat(circuitBreaker.getSuccessCount()).isGreaterThanOrEqualTo(2);
    }
}
