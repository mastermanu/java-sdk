/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.common;

import com.google.common.base.Defaults;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.failure.ChildWorkflowFailure;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class RetryOptions {

  private static final double DEFAULT_BACKOFF_COEFFICIENT = 2.0;
  private static final int DEFAULT_MAXIMUM_MULTIPLIER = 100;

  public static Builder newBuilder() {
    return new Builder(null);
  }

  /**
   * Creates builder with fields pre-populated from passed options.
   *
   * @param options can be null
   */
  public static Builder newBuilder(RetryOptions options) {
    return new Builder(options);
  }

  public static RetryOptions getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final RetryOptions DEFAULT_INSTANCE;

  static {
    DEFAULT_INSTANCE = RetryOptions.newBuilder().build();
  }

  /**
   * Merges annotation with explicitly provided RetryOptions. If there is conflict RetryOptions
   * takes precedence.
   */
  public static RetryOptions merge(MethodRetry r, RetryOptions o) {
    if (r == null) {
      if (o == null) {
        return null;
      }
      return RetryOptions.newBuilder(o).validateBuildWithDefaults();
    }
    if (o == null) {
      o = RetryOptions.newBuilder().build();
    }
    Duration initial = merge(r.initialIntervalSeconds(), o.getInitialInterval());
    RetryOptions.Builder builder = RetryOptions.newBuilder();
    if (initial != null) {
      builder.setInitialInterval(initial);
    }
    Duration maximum = merge(r.maximumIntervalSeconds(), o.getMaximumInterval());
    if (maximum != null) {
      builder.setMaximumInterval(maximum);
    }
    double coefficient = merge(r.backoffCoefficient(), o.getBackoffCoefficient(), double.class);
    if (coefficient != 0d) {
      builder.setBackoffCoefficient(coefficient);
    } else {
      builder.setBackoffCoefficient(DEFAULT_BACKOFF_COEFFICIENT);
    }
    return builder
        .setMaximumAttempts(merge(r.maximumAttempts(), o.getMaximumAttempts(), int.class))
        .setDoNotRetry(merge(r.doNotRetry(), o.getDoNotRetry()))
        .validateBuildWithDefaults();
  }

  /** The parameter options takes precedence. */
  public RetryOptions merge(RetryOptions o) {
    if (o == null) {
      return this;
    }
    return RetryOptions.newBuilder()
        .setInitialInterval(merge(getInitialInterval(), o.getInitialInterval(), Duration.class))
        .setMaximumInterval(merge(getMaximumInterval(), o.getMaximumInterval(), Duration.class))
        .setBackoffCoefficient(
            merge(getBackoffCoefficient(), o.getBackoffCoefficient(), double.class))
        .setMaximumAttempts(merge(getMaximumAttempts(), o.getMaximumAttempts(), int.class))
        .setDoNotRetry(merge(getDoNotRetry(), o.getDoNotRetry()))
        .validateBuildWithDefaults();
  }

  @SafeVarargs
  public final RetryOptions addDoNotRetry(String... doNotRetry) {
    if (doNotRetry == null) {
      return this;
    }

    double backoffCoefficient = getBackoffCoefficient();
    if (backoffCoefficient == 0) {
      backoffCoefficient = DEFAULT_BACKOFF_COEFFICIENT;
    }

    RetryOptions.Builder builder =
        RetryOptions.newBuilder()
            .setInitialInterval(getInitialInterval())
            .setMaximumInterval(getMaximumInterval())
            .setBackoffCoefficient(backoffCoefficient)
            .setDoNotRetry(merge(doNotRetry, getDoNotRetry()));

    if (getMaximumAttempts() > 0) {
      builder.setMaximumAttempts(getMaximumAttempts());
    }
    return builder.validateBuildWithDefaults();
  }

  public static final class Builder {

    private static final Duration DEFAULT_INITIAL_INTERVAL = Duration.ofSeconds(1);

    private Duration initialInterval;

    private double backoffCoefficient;

    private int maximumAttempts;

    private Duration maximumInterval;

    private String[] doNotRetry;

    private Builder(RetryOptions options) {
      if (options == null) {
        return;
      }
      this.backoffCoefficient = options.getBackoffCoefficient();
      this.maximumAttempts = options.getMaximumAttempts();
      this.initialInterval = options.getInitialInterval();
      this.maximumInterval = options.getMaximumInterval();
      this.doNotRetry = options.getDoNotRetry();
    }

    /**
     * Interval of the first retry. If coefficient is 1.0 then it is used for all retries. Required.
     */
    public Builder setInitialInterval(Duration initialInterval) {
      Objects.requireNonNull(initialInterval);
      if (initialInterval.isNegative() || initialInterval.isZero()) {
        throw new IllegalArgumentException("Invalid interval: " + initialInterval);
      }
      this.initialInterval = initialInterval;
      return this;
    }

    /**
     * Coefficient used to calculate the next retry interval. The next retry interval is previous
     * interval multiplied by this coefficient. Must be 1 or larger. Default is 2.0.
     */
    public Builder setBackoffCoefficient(double backoffCoefficient) {
      if (backoffCoefficient < 1d) {
        throw new IllegalArgumentException("coefficient less than 1.0: " + backoffCoefficient);
      }
      this.backoffCoefficient = backoffCoefficient;
      return this;
    }

    /**
     * Maximum number of attempts. When exceeded the retries stop even if not expired yet. Must be 1
     * or bigger. Default is unlimited.
     */
    public Builder setMaximumAttempts(int maximumAttempts) {
      if (maximumAttempts < 1) {
        throw new IllegalArgumentException("Invalid maximumAttempts: " + maximumAttempts);
      }
      this.maximumAttempts = maximumAttempts;
      return this;
    }

    /**
     * Maximum interval between retries. Exponential backoff leads to interval increase. This value
     * is the cap of the increase. Default is 100x of initial interval.
     */
    public Builder setMaximumInterval(Duration maximumInterval) {
      Objects.requireNonNull(maximumInterval);
      if (maximumInterval.isNegative() || maximumInterval.isZero()) {
        throw new IllegalArgumentException("Invalid interval: " + maximumInterval);
      }
      this.maximumInterval = maximumInterval;
      return this;
    }

    /**
     * List of exceptions application failures types to retry. Application failures are converted to
     * {@link ApplicationFailure#getType()}.
     *
     * <p>{@link Error} and {@link CanceledFailure} are never retried and are not even passed to
     * this filter.
     */
    @SafeVarargs
    public final Builder setDoNotRetry(String... doNotRetry) {
      if (doNotRetry != null) {
        this.doNotRetry = doNotRetry;
      }
      return this;
    }

    /**
     * Build RetryOptions without performing validation as validation should be done after merging
     * with {@link MethodRetry}.
     */
    public RetryOptions build() {
      return new RetryOptions(
          initialInterval, backoffCoefficient, maximumAttempts, maximumInterval, doNotRetry);
    }

    /** Validates property values and builds RetryOptions with default values. */
    public RetryOptions validateBuildWithDefaults() {
      double backoff = backoffCoefficient;
      if (backoff == 0d) {
        backoff = DEFAULT_BACKOFF_COEFFICIENT;
      }
      return new RetryOptions(
          initialInterval == null || initialInterval.isZero()
              ? DEFAULT_INITIAL_INTERVAL
              : initialInterval,
          backoff,
          maximumAttempts,
          maximumInterval,
          doNotRetry == null ? new String[0] : doNotRetry);
    }
  }

  private final Duration initialInterval;

  private final double backoffCoefficient;

  private final int maximumAttempts;

  private final Duration maximumInterval;

  private final String[] doNotRetry;

  private RetryOptions(
      Duration initialInterval,
      double backoffCoefficient,
      int maximumAttempts,
      Duration maximumInterval,
      String[] doNotRetry) {
    this.initialInterval = initialInterval;
    this.backoffCoefficient = backoffCoefficient;
    this.maximumAttempts = maximumAttempts;
    this.maximumInterval = maximumInterval;
    this.doNotRetry = doNotRetry;
  }

  public Duration getInitialInterval() {
    return initialInterval;
  }

  public double getBackoffCoefficient() {
    return backoffCoefficient;
  }

  public int getMaximumAttempts() {
    return maximumAttempts;
  }

  public Duration getMaximumInterval() {
    return maximumInterval;
  }

  /**
   * @return null if not configured. When merging with annotation it makes a difference. null means
   *     use values from an annotation. Empty list means do not retry on anything.
   */
  public String[] getDoNotRetry() {
    return doNotRetry;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public String toString() {
    return "RetryOptions{"
        + "initialInterval="
        + initialInterval
        + ", backoffCoefficient="
        + backoffCoefficient
        + ", maximumAttempts="
        + maximumAttempts
        + ", maximumInterval="
        + maximumInterval
        + ", doNotRetry="
        + Arrays.toString(doNotRetry)
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RetryOptions that = (RetryOptions) o;
    return Double.compare(that.backoffCoefficient, backoffCoefficient) == 0
        && maximumAttempts == that.maximumAttempts
        && Objects.equals(initialInterval, that.initialInterval)
        && Objects.equals(maximumInterval, that.maximumInterval)
        && Arrays.equals(doNotRetry, that.doNotRetry);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        initialInterval,
        backoffCoefficient,
        maximumAttempts,
        maximumInterval,
        Arrays.hashCode(doNotRetry));
  }

  private static <G> G merge(G annotation, G options, Class<G> type) {
    if (!Defaults.defaultValue(type).equals(options)) {
      return options;
    }
    return annotation;
  }

  private static Duration merge(long aSeconds, Duration o) {
    if (o != null) {
      return o;
    }
    return aSeconds == 0 ? null : Duration.ofSeconds(aSeconds);
  }

  private static String[] merge(String[] fromAnnotation, String[] fromOptions) {
    if (fromOptions != null) {
      return fromOptions;
    }
    return fromAnnotation;
  }

  public long calculateSleepTime(long attempt) {
    double coefficient =
        backoffCoefficient == 0d ? DEFAULT_BACKOFF_COEFFICIENT : backoffCoefficient;
    double sleepMillis = Math.pow(coefficient, attempt - 1) * initialInterval.toMillis();
    if (maximumInterval == null) {
      return (long) Math.min(sleepMillis, initialInterval.toMillis() * DEFAULT_MAXIMUM_MULTIPLIER);
    }
    return Math.min((long) sleepMillis, maximumInterval.toMillis());
  }

  public boolean shouldRethrow(
      Throwable e, Optional<Duration> expiration, long attempt, long elapsed, long sleepTime) {
    String type;
    if (e instanceof ActivityFailure || e instanceof ChildWorkflowFailure) {
      e = e.getCause();
    }
    if (e instanceof ApplicationFailure) {
      type = ((ApplicationFailure) e).getType();
    } else {
      type = e.getClass().getName();
    }
    if (doNotRetry != null) {
      for (String doNotRetry : doNotRetry) {
        if (doNotRetry.equals(type)) {
          return true;
        }
      }
    }
    // Attempt that failed.
    if (maximumAttempts != 0 && attempt >= maximumAttempts) {
      return true;
    }
    return expiration.isPresent() && elapsed + sleepTime >= expiration.get().toMillis();
  }
}
