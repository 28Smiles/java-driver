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
package com.datastax.oss.driver.api.netrics.microprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.metrics.DefaultNodeMetric;
import com.datastax.oss.driver.api.core.metrics.DefaultSessionMetric;
import com.datastax.oss.driver.api.core.metrics.NodeMetric;
import com.datastax.oss.driver.api.core.metrics.SessionMetric;
import com.datastax.oss.driver.api.metrics.microprofile.MicroProfileMetricsSessionBuilder;
import com.datastax.oss.driver.api.testinfra.ccm.CcmRule;
import com.datastax.oss.driver.api.testinfra.session.SessionUtils;
import com.datastax.oss.driver.categories.ParallelizableTests;
import com.datastax.oss.driver.internal.metrics.microprofile.MicroProfileMetricUpdater;
import io.smallrye.metrics.MetricsRegistryImpl;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.assertj.core.api.Condition;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ParallelizableTests.class)
public class MicroProfileMetricsTest {

  @ClassRule public static final CcmRule CCM_RULE = CcmRule.getInstance();
  private static final MetricRegistry METRIC_REGISTRY = new MetricsRegistryImpl();

  @Test
  public void should_expose_metrics() {
    // Driver config from test-resources application.conf
    DriverConfigLoader loader = SessionUtils.configLoaderBuilder().build();
    MicroProfileMetricsSessionBuilder builder =
        new MicroProfileMetricsSessionBuilder()
            .addContactEndPoints(CCM_RULE.getContactPoints())
            .withMetricRegistry(METRIC_REGISTRY);

    try (CqlSession session = builder.withConfigLoader(loader).build()) {
      for (int i = 0; i < 10; i++) {
        session.execute("SELECT release_version FROM system.local");
      }

      assertThat(METRIC_REGISTRY.getMetrics()).hasSize(41);

      // Should have 10 requests, check within 5 seconds as metric increments after
      // caller is notified.
      await()
          .pollInterval(500, TimeUnit.MILLISECONDS)
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  assertThat(METRIC_REGISTRY.getMetrics())
                      .hasEntrySatisfying(
                          buildTimerCondition(
                              "CQL_REQUESTS should be a SESSION Timer with count 10",
                              buildSessionMetricPattern(DefaultSessionMetric.CQL_REQUESTS, session),
                              a -> a == 10))
                      .hasEntrySatisfying(
                          buildGaugeCondition(
                              "CONNECTED_NODES should be a SESSION Gauge with count 1",
                              buildSessionMetricPattern(
                                  DefaultSessionMetric.CONNECTED_NODES, session),
                              a -> a == 1))
                      .hasEntrySatisfying(
                          buildMeterCondition(
                              "BYTES_SENT should be a SESSION Meter with count > 0",
                              buildSessionMetricPattern(DefaultSessionMetric.BYTES_SENT, session),
                              a -> a > 0))
                      .hasEntrySatisfying(
                          buildMeterCondition(
                              "BYTES_SENT should be a SESSION Meter with count > 0",
                              buildNodeMetricPattern(DefaultNodeMetric.BYTES_SENT, session),
                              a -> a > 0))
                      .hasEntrySatisfying(
                          buildMeterCondition(
                              "BYTES_RECEIVED should be a SESSION Meter with count > 0",
                              buildSessionMetricPattern(
                                  DefaultSessionMetric.BYTES_RECEIVED, session),
                              a -> a > 0))
                      .hasEntrySatisfying(
                          buildMeterCondition(
                              "BYTES_RECEIVED should be a NODE Meter with count > 0",
                              buildNodeMetricPattern(DefaultNodeMetric.BYTES_RECEIVED, session),
                              a -> a > 0))
                      .hasEntrySatisfying(
                          buildTimerCondition(
                              "CQL_MESSAGESS should be a NODE Timer with count 10",
                              buildNodeMetricPattern(DefaultNodeMetric.CQL_MESSAGES, session),
                              a -> a == 10))
                      .hasEntrySatisfying(
                          buildGaugeCondition(
                              "AVAILABLE_STREAMS should be a NODE Gauge with count 1024",
                              buildNodeMetricPattern(DefaultNodeMetric.AVAILABLE_STREAMS, session),
                              a -> a == 1024))
                      .hasEntrySatisfying(
                          buildCounterCondition(
                              "RETRIES should be a NODE Counter with count 0",
                              buildNodeMetricPattern(DefaultNodeMetric.RETRIES, session),
                              a -> a == 0)));
    }
  }

  private Condition<Entry<MetricID, Metric>> buildTimerCondition(
      String description, String metricPattern, Function<Long, Boolean> verifyFunction) {
    return new Condition<Entry<MetricID, Metric>>(description) {
      @Override
      public boolean matches(Entry<MetricID, Metric> metric) {
        if (!(metric.getValue() instanceof Timer)) {
          // Metric is not a Timer
          return false;
        }
        final Timer timer = (Timer) metric.getValue();
        final MetricID id = metric.getKey();
        return verifyFunction.apply(timer.getCount())
            && Pattern.matches(metricPattern, id.getName());
      }
    };
  }

  private Condition<Entry<MetricID, Metric>> buildCounterCondition(
      String description, String metricPattern, Function<Long, Boolean> verifyFunction) {
    return new Condition<Entry<MetricID, Metric>>(description) {
      @Override
      public boolean matches(Entry<MetricID, Metric> metric) {
        if (!(metric.getValue() instanceof Counter)) {
          // Metric is not a Counter
          return false;
        }
        final Counter counter = (Counter) metric.getValue();
        final MetricID id = metric.getKey();
        return verifyFunction.apply(counter.getCount())
            && Pattern.matches(metricPattern, id.getName());
      }
    };
  }

  private Condition<Entry<MetricID, Metric>> buildMeterCondition(
      String description, String metricPattern, Function<Long, Boolean> verifyFunction) {
    return new Condition<Entry<MetricID, Metric>>(description) {
      @Override
      public boolean matches(Entry<MetricID, Metric> metric) {
        if (!(metric.getValue() instanceof Meter)) {
          // Metric is not a Meter
          return false;
        }
        final Meter meter = (Meter) metric.getValue();
        final MetricID id = metric.getKey();
        return verifyFunction.apply(meter.getCount())
            && Pattern.matches(metricPattern, id.getName());
      }
    };
  }

  private Condition<Entry<MetricID, Metric>> buildGaugeCondition(
      String description, String metricPattern, Function<Double, Boolean> verifyFunction) {
    return new Condition<Entry<MetricID, Metric>>(description) {
      @Override
      public boolean matches(Entry<MetricID, Metric> metric) {
        if (!(metric.getValue() instanceof Gauge)) {
          // Metric is not a Gauge
          return false;
        }
        final Gauge<?> gauge = (Gauge<?>) metric.getValue();
        final Number gaugeValue = (Number) gauge.getValue();
        final MetricID id = metric.getKey();
        return verifyFunction.apply(gaugeValue.doubleValue())
            && Pattern.matches(metricPattern, id.getName());
      }
    };
  }

  private String buildSessionMetricPattern(SessionMetric metric, CqlSession s) {
    return MicroProfileMetricUpdater.CASSANDRA_METRICS_PREFIX
        + "\\."
        + s.getContext().getSessionName()
        + "\\."
        + metric.getPath();
  }

  private String buildNodeMetricPattern(NodeMetric metric, CqlSession s) {
    return MicroProfileMetricUpdater.CASSANDRA_METRICS_PREFIX
        + "\\."
        + s.getContext().getSessionName()
        + "\\.nodes\\.\\S*\\."
        + metric.getPath();
  }
}
