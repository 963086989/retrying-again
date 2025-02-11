/*
 * Copyright 2012-2015 Ray Holder
 * Modifications copyright 2017-2018 Robert Huffman
 * Modifications copyright 2020-2021 Kiwi Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

class WaitStrategiesTest {

    @Test
    void testNoWait() {
        var noWait = WaitStrategies.noWait();
        assertThat(noWait.computeSleepTime(failedAttempt(18, 9879L)))
                .isZero();
    }

    @Test
    void testFixedWait() {
        var fixedWait = WaitStrategies.fixedWait(1000L, TimeUnit.MILLISECONDS);
        assertThat(fixedWait.computeSleepTime(failedAttempt(12, 6546L)))
                .isEqualTo(1000L);
    }

    @Test
    void testFixedWait_ShouldNotAllowNegativeSleepTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WaitStrategies.fixedWait(-500L, TimeUnit.MILLISECONDS))
                .withMessage("sleepTime must be >= 0 but is -500");
    }

    @ParameterizedTest
    @CsvSource({
            "1, 500",
            "2, 600",
            "3, 700",
    })
    void testIncrementingWait(int attemptNumber, long expectedSleepTime) {
        var incrementingWait = WaitStrategies.incrementingWait(500L, TimeUnit.MILLISECONDS, 100L, TimeUnit.MILLISECONDS);
        assertThat(incrementingWait.computeSleepTime(failedAttempt(attemptNumber, 6546L)))
                .isEqualTo(expectedSleepTime);
    }

    @Test
    void testIncrementingWait_ShouldNotAllowNegativeInitialSleepTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WaitStrategies.incrementingWait(-500L, TimeUnit.MILLISECONDS, 100L, TimeUnit.MILLISECONDS))
                .withMessage("initialSleepTime must be >= 0 but is -500");
    }

    @Test
    void testRandomWait() {
        var randomWait = WaitStrategies.randomWait(1000L, TimeUnit.MILLISECONDS, 2000L, TimeUnit.MILLISECONDS);
        var times = new HashSet<Long>();
        times.add(randomWait.computeSleepTime(failedAttempt(1, 6546L)));
        times.add(randomWait.computeSleepTime(failedAttempt(1, 6546L)));
        times.add(randomWait.computeSleepTime(failedAttempt(1, 6546L)));
        times.add(randomWait.computeSleepTime(failedAttempt(1, 6546L)));

        assertThat(times).hasSizeGreaterThan(1);
        times.forEach(time -> assertThat(time).isBetween(1000L, 2000L));
    }

    @Test
    void testRandomWaitWithoutMinimum() {
        var randomWait = WaitStrategies.randomWait(2000L, TimeUnit.MILLISECONDS);
        var times = new HashSet<Long>();
        times.add(randomWait.computeSleepTime(failedAttempt(1, 6546L)));
        times.add(randomWait.computeSleepTime(failedAttempt(1, 6546L)));
        times.add(randomWait.computeSleepTime(failedAttempt(1, 6546L)));
        times.add(randomWait.computeSleepTime(failedAttempt(1, 6546L)));

        assertThat(times).hasSizeGreaterThan(1);
        times.forEach(time -> assertThat(time).isBetween(0L, 2000L));
    }

    @Test
    void testRandomWait_ShouldNotAllowNegativeMinimumTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WaitStrategies.randomWait(-250, TimeUnit.MILLISECONDS, 1000, TimeUnit.MILLISECONDS))
                .withMessage("minimum must be >= 0 but is -250");
    }

    @Test
    void testRandomWait_ShouldRequireMaximumTime_ToBeHigherThanMinimumTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WaitStrategies.randomWait(500, TimeUnit.MILLISECONDS, 499, TimeUnit.MILLISECONDS))
                .withMessage("maximum must be > minimum but maximum is 499 and minimum is 500");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void testExponential(int attemptNumber) {
        var exponentialWait = WaitStrategies.exponentialWait();
        assertThat(exponentialWait.computeSleepTime(failedAttempt(attemptNumber, 0)))
                .isEqualTo((long) Math.pow(2, attemptNumber));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void testExponentialWithMaximumWait(int attemptNumber) {
        var maximumTime = 40;
        var exponentialWait = WaitStrategies.exponentialWait(maximumTime, TimeUnit.MILLISECONDS);

        var unadjustedWait = (long) Math.pow(2, attemptNumber);
        var expectedWait = Math.min(unadjustedWait, maximumTime);

        assertThat(exponentialWait.computeSleepTime(failedAttempt(attemptNumber, 0)))
                .isEqualTo(expectedWait);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void testExponentialWithMultiplierAndMaximumWait(int attemptNumber) {
        var multiplier = 1000;
        var maximumTime = 50000;
        var exponentialWait = WaitStrategies.exponentialWait(multiplier, maximumTime, TimeUnit.MILLISECONDS);

        var unadjustedWait = multiplier * (long) Math.pow(2, attemptNumber);
        var expectedWait = Math.min(unadjustedWait, maximumTime);

        assertThat(exponentialWait.computeSleepTime(failedAttempt(attemptNumber, 0)))
                .isEqualTo(expectedWait);
    }

    @ParameterizedTest
    @ValueSource(longs = {-5, -1, 0})
    void testExponentialWait_ShouldRequirePositiveMultiplier(long multiplier) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WaitStrategies.exponentialWait(multiplier, 10_000, TimeUnit.MILLISECONDS))
                .withMessage("multiplier must be > 0 but is %d", multiplier);
    }

    @ParameterizedTest
    @ValueSource(longs = {-5000, -1000, -1})
    void testExponentialWait_ShouldRequireZeroOrPositiveMaximumWait(long maximumWait) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WaitStrategies.exponentialWait(1000, maximumWait, TimeUnit.MILLISECONDS))
                .withMessage("maximumWait must be >= 0 but is %d", maximumWait);
    }

    @ParameterizedTest
    @CsvSource({
            "1000, 999",
            "500, 400",
            "250, 249"
    })
    void testExponentialWait_ShouldRequireMultiplier_ToBeLessThanMaximumWait(long multiplier, long maximumWait) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WaitStrategies.exponentialWait(multiplier, maximumWait, TimeUnit.MILLISECONDS))
                .withMessage("multiplier must be < maximumWait (%d) but is %d", maximumWait, multiplier);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1",
            "2, 1",
            "3, 2",
            "4, 3",
            "5, 5",
            "6, 8"
    })
    void testFibonacci(int attemptNumber, long expectedSleep) {
        var fibonacciWait = WaitStrategies.fibonacciWait();

        assertThat(fibonacciWait.computeSleepTime(failedAttempt(attemptNumber, 0L)))
                .isEqualTo(expectedSleep);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1",
            "2, 1",
            "3, 2",
            "4, 3",
            "5, 5",
            "6, 8",
            "7, 10",
            "8, 10",
    })
    void testFibonacciWithMaximumWait(int attemptNumber, long expectedSleep) {
        var fibonacciWait = WaitStrategies.fibonacciWait(10L, TimeUnit.MILLISECONDS);

        assertThat(fibonacciWait.computeSleepTime(failedAttempt(attemptNumber, 0L)))
                .isEqualTo(expectedSleep);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1000",
            "2, 1000",
            "3, 2000",
            "4, 3000",
            "5, 5000",
            "6, 8000",
            "7, 13000",
            "8, 21000",
            "9, 34000",
            "10, 50000",
            "11, 50000",
            "12, 50000",
    })
    void testFibonacciWithMultiplierAndMaximumWait(int attemptNumber, long expectedSleep) {
        var fibonacciWait = WaitStrategies.fibonacciWait(1000L, 50000L, TimeUnit.MILLISECONDS);

        assertThat(fibonacciWait.computeSleepTime(failedAttempt(attemptNumber, 0L)))
                .isEqualTo(expectedSleep);
    }

    @ParameterizedTest
    @ValueSource(longs = {-5, -1, 0})
    void testFibonacciWait_ShouldRequirePositiveMultiplier(long multiplier) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WaitStrategies.fibonacciWait(multiplier, 10_000, TimeUnit.MILLISECONDS))
                .withMessage("multiplier must be > 0 but is %d", multiplier);
    }

    @ParameterizedTest
    @ValueSource(longs = {-5000, -1000, -1})
    void testFibonacciWait_ShouldRequireZeroOrPositiveMaximumWait(long maximumWait) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WaitStrategies.fibonacciWait(1000, maximumWait, TimeUnit.MILLISECONDS))
                .withMessage("maximumWait must be >= 0 but is %d", maximumWait);
    }

    @ParameterizedTest
    @CsvSource({
            "1000, 999",
            "500, 400",
            "250, 249"
    })
    void testFibonacciWait_ShouldRequireMultiplier_ToBeLessThanMaximumWait(long multiplier, long maximumWait) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WaitStrategies.fibonacciWait(multiplier, maximumWait, TimeUnit.MILLISECONDS))
                .withMessage("multiplier must be < maximumWait (%d) but is %d", maximumWait, multiplier);
    }

    @Test
    void testExceptionWait() {
        var failedAttempt = failedAttempt(42, 7227);
        var exceptionWait = WaitStrategies.exceptionWait(RuntimeException.class, zeroSleepFunction());
        assertThat(exceptionWait.computeSleepTime(failedAttempt)).isZero();

        var oneMinuteWait = WaitStrategies.exceptionWait(RuntimeException.class, oneMinuteSleepFunction());
        assertThat(oneMinuteWait.computeSleepTime(failedAttempt)).isEqualTo(3600 * 1000L);

        var noMatchRetryAfterWait = WaitStrategies.exceptionWait(RetryAfterException.class, customSleepFunction());
        assertThat(noMatchRetryAfterWait.computeSleepTime(failedAttempt)).isZero();

        var retryAfterWait = WaitStrategies.exceptionWait(RetryAfterException.class, customSleepFunction());
        var failedRetryAfterAttempt = Attempt.<Boolean>newExceptionAttempt(new RetryAfterException(), 42, 7227L);
        assertThat(retryAfterWait.computeSleepTime(failedRetryAfterAttempt)).isEqualTo(RetryAfterException.SLEEP_TIME);
    }

    @Test
    void testJoin_ShouldRequireAtLeastOneWaitStrategy() {
        assertThatIllegalStateException()
                .isThrownBy(WaitStrategies::join)
                .withMessage("Must have at least one wait strategy");
    }

    @Test
    void testJoin_ShouldNotPermitNullWaitStrategy() {
        assertThatIllegalStateException()
                .isThrownBy(() -> WaitStrategies.join(
                        WaitStrategies.fibonacciWait(),
                        null,
                        WaitStrategies.fixedWait(100L, TimeUnit.MILLISECONDS)))
                .withMessage("Cannot have a null wait strategy");
    }

    private Attempt<Boolean> failedAttempt(int attemptNumber, long delaySinceFirstAttempt) {
        return Attempt.newExceptionAttempt(new RuntimeException(), attemptNumber, delaySinceFirstAttempt);
    }

    private Function<RuntimeException, Long> zeroSleepFunction() {
        return input -> 0L;
    }

    private Function<RuntimeException, Long> oneMinuteSleepFunction() {
        return input -> 3600 * 1000L;
    }

    private Function<RetryAfterException, Long> customSleepFunction() {
        return RetryAfterException::getRetryAfter;
    }

    public static class RetryAfterException extends RuntimeException {

        static final long SLEEP_TIME = 29L;

        long getRetryAfter() {
            return SLEEP_TIME;
        }
    }
}
