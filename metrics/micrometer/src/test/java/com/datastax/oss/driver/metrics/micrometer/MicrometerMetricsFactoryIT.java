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
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.metrics.DefaultSessionMetric;
import com.datastax.oss.driver.api.testinfra.ccm.CcmRule;
import com.datastax.oss.driver.api.testinfra.session.SessionUtils;
import com.datastax.oss.driver.categories.ParallelizableTests;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
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
    DriverConfigLoader loader =
        SessionUtils.configLoaderBuilder()
            .withStringList(
                DefaultDriverOption.METRICS_SESSION_ENABLED,
                Collections.singletonList("cql-requests"))
            .build();
    MicrometerMetricsSessionBuilder builder =
        new MicrometerMetricsSessionBuilder()
            .addContactEndPoints(CCM_RULE.getContactPoints())
            .withMeterRegistry(METER_REGISTRY);

    try (CqlSession session = builder.withConfigLoader(loader).build()) {
      for (int i = 0; i < 10; i++) {
        session.execute("SELECT release_version FROM system.local");
      }

      // Should have 10 requests, check within 5 seconds as metric increments after
      // caller is notified.
      await()
          .pollInterval(500, TimeUnit.MILLISECONDS)
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(
              () ->
                  assertThat(METER_REGISTRY.getMeters())
                      .hasOnlyOneElementSatisfying(
                          meter ->
                              assertThat(meter)
                                  .has(
                                      new Condition("Meter should be a Timer with count 10") {
                                        @Override
                                        public boolean matches(Object obj) {
                                          if (!(obj instanceof Timer)) {
                                            return false;
                                          }
                                          final Timer timer = (Timer) obj;
                                          return timer
                                                  .getId()
                                                  .getName()
                                                  .contains(
                                                      DefaultSessionMetric.CQL_REQUESTS.getPath())
                                              && timer.count() == 10;
                                        }
                                      })));
    }
  }
}
