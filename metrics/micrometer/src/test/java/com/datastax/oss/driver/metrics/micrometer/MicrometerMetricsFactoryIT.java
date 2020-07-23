/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.driver.metrics.micrometer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.metrics.DefaultNodeMetric;
import com.datastax.oss.driver.api.core.metrics.DefaultSessionMetric;
import com.datastax.oss.driver.api.core.metrics.NodeMetric;
import com.datastax.oss.driver.api.core.metrics.SessionMetric;
import com.datastax.oss.driver.api.testinfra.ccm.CcmRule;
import com.datastax.oss.driver.api.testinfra.session.SessionUtils;
import com.datastax.oss.driver.categories.ParallelizableTests;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.assertj.core.api.Condition;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ParallelizableTests.class)
public class MicrometerMetricsFactoryIT {

  @ClassRule public static final CcmRule CCM_RULE = CcmRule.getInstance();
  private static final MeterRegistry METER_REGISTRY = new SimpleMeterRegistry();

  @Test
  public void should_expose_metrics() {
    // Driver config from test-resources application.conf
    DriverConfigLoader loader = SessionUtils.configLoaderBuilder().build();
    MicrometerMetricsSessionBuilder builder =
        new MicrometerMetricsSessionBuilder()
            .addContactEndPoints(CCM_RULE.getContactPoints())
            .withMeterRegistry(METER_REGISTRY);

    try (CqlSession session = builder.withConfigLoader(loader).build()) {
      for (int i = 0; i < 10; i++) {
        session.execute("SELECT release_version FROM system.local");
      }

      assertThat(METER_REGISTRY.getMeters()).hasSize(41);

      // Should have 10 requests, check within 5 seconds as metric increments after
      // caller is notified.
      await()
          .pollInterval(500, TimeUnit.MILLISECONDS)
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  assertThat(METER_REGISTRY.getMeters())
                      .haveExactly(
                          1,
                          buildTimerCondition(
                              "CQL_REQUESTS should be a SESSION Timer with count 10",
                              buildSessionMetricPattern(DefaultSessionMetric.CQL_REQUESTS, session),
                              a -> a == 10))
                      .haveExactly(
                          1,
                          buildTimerCondition(
                              "CQL_MESSAGESS should be a NODE Timer with count 10",
                              buildNodeMetricPattern(DefaultNodeMetric.CQL_MESSAGES, session),
                              a -> a == 10))
                      .haveExactly(
                          1,
                          buildGaugeCondition(
                              "CONNECTED_NODES should be a SESSION Gauge with count 1",
                              buildSessionMetricPattern(
                                  DefaultSessionMetric.CONNECTED_NODES, session),
                              a -> a == 1))
                      .haveExactly(
                          1,
                          buildCounterCondition(
                              "RETRIES should be a NODE Counter with count 0",
                              buildNodeMetricPattern(DefaultNodeMetric.RETRIES, session),
                              a -> a == 0))
                      .haveExactly(
                          1,
                          buildCounterCondition(
                              "BYTES_SENT should be a SESSION Counter with count > 0",
                              buildSessionMetricPattern(DefaultSessionMetric.BYTES_SENT, session),
                              a -> a > 0))
                      .haveExactly(
                          1,
                          buildCounterCondition(
                              "BYTES_SENT should be a SESSION Counter with count > 0",
                              buildNodeMetricPattern(DefaultNodeMetric.BYTES_SENT, session),
                              a -> a > 0))
                      .haveExactly(
                          1,
                          buildCounterCondition(
                              "BYTES_RECEIVED should be a SESSION Counter with count > 0",
                              buildSessionMetricPattern(
                                  DefaultSessionMetric.BYTES_RECEIVED, session),
                              a -> a > 0))
                      .haveExactly(
                          1,
                          buildGaugeCondition(
                              "AVAILABLE_STREAMS should be a NODE Gauge with count 1024",
                              buildNodeMetricPattern(DefaultNodeMetric.AVAILABLE_STREAMS, session),
                              a -> a == 1024))
                      .haveExactly(
                          1,
                          buildCounterCondition(
                              "BYTES_RECEIVED should be a NODE Counter with count > 0",
                              buildNodeMetricPattern(DefaultNodeMetric.BYTES_RECEIVED, session),
                              a -> a > 0)));
    }
  }

  private Condition buildTimerCondition(
      String description, String metricPattern, Function<Long, Boolean> verifyFunction) {
    return new Condition(description) {
      @Override
      public boolean matches(Object obj) {
        if (!(obj instanceof Timer)) {
          return false;
        }
        Timer timer = (Timer) obj;
        return Pattern.matches(metricPattern, timer.getId().getName())
            && verifyFunction.apply(timer.count());
      }
    };
  }

  private Condition buildCounterCondition(
      String description, String metricPattern, Function<Double, Boolean> verifyFunction) {
    return new Condition(description) {
      @Override
      public boolean matches(Object obj) {
        if (!(obj instanceof Counter)) {
          return false;
        }
        Counter counter = (Counter) obj;
        return Pattern.matches(metricPattern, counter.getId().getName())
            && verifyFunction.apply(counter.count());
      }
    };
  }

  private Condition buildGaugeCondition(
      String description, String metricPattern, Function<Double, Boolean> verifyFunction) {
    return new Condition(description) {
      @Override
      public boolean matches(Object obj) {
        if (!(obj instanceof Gauge)) {
          return false;
        }
        Gauge gauge = (Gauge) obj;
        return Pattern.matches(metricPattern, gauge.getId().getName())
            && verifyFunction.apply(gauge.value());
      }
    };
  }

  private String buildSessionMetricPattern(SessionMetric metric, CqlSession s) {
    return MicrometerMetricUpdater.CASSANDRA_METRICS_PREFIX
        + "\\."
        + s.getContext().getSessionName()
        + "\\."
        + metric.getPath();
  }

  private String buildNodeMetricPattern(NodeMetric metric, CqlSession s) {
    return MicrometerMetricUpdater.CASSANDRA_METRICS_PREFIX
        + "\\."
        + s.getContext().getSessionName()
        + "\\.nodes\\.\\S*\\."
        + metric.getPath();
  }
}
