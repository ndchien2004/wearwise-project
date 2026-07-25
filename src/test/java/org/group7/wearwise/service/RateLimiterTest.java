package org.group7.wearwise.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    void allowsUpToCapacityThenBlocks() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.checkAndConsume("a", 5, Duration.ofMinutes(1)))
                    .as("lượt thứ %d phải được cho qua", i + 1)
                    .isZero();
        }

        assertThat(rateLimiter.checkAndConsume("a", 5, Duration.ofMinutes(1))).isPositive();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            rateLimiter.checkAndConsume("user:alice", 3, Duration.ofMinutes(1));
        }

        assertThat(rateLimiter.checkAndConsume("user:alice", 3, Duration.ofMinutes(1))).isPositive();
        assertThat(rateLimiter.checkAndConsume("user:bob", 3, Duration.ofMinutes(1))).isZero();
    }

    /** Cửa sổ rất ngắn để bucket kịp nạp lại trong lúc test chạy. */
    @Test
    void refillsOverTime() throws InterruptedException {
        assertThat(rateLimiter.checkAndConsume("c", 1, Duration.ofMillis(150))).isZero();
        assertThat(rateLimiter.checkAndConsume("c", 1, Duration.ofMillis(150))).isPositive();

        Thread.sleep(200);

        assertThat(rateLimiter.checkAndConsume("c", 1, Duration.ofMillis(150))).isZero();
    }

    @Test
    void capacityZeroDisablesTheLimit() {
        for (int i = 0; i < 100; i++) {
            assertThat(rateLimiter.checkAndConsume("d", 0, Duration.ofMinutes(1))).isZero();
        }
    }

    /**
     * Nhiều luồng cùng tiêu thụ một khóa không được vượt quá hạn mức — nếu không, kẻ tấn công
     * chỉ cần bắn song song là lách được giới hạn.
     */
    @Test
    void doesNotOverGrantUnderConcurrency() throws InterruptedException {
        int capacity = 50;
        int threads = 16;
        int attemptsPerThread = 20;

        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (rateLimiter.checkAndConsume("hot", capacity, Duration.ofHours(1)) == 0) {
                            allowed.incrementAndGet();
                        }
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Cửa sổ 1 giờ nên phần nạp lại trong vài mili giây là không đáng kể.
        assertThat(allowed.get()).isEqualTo(capacity);
    }
}
