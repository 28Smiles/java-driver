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
package com.datastax.oss.driver.metrics.microprofile;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.session.ProgrammaticArguments;
import com.datastax.oss.driver.api.core.session.SessionBuilder;
import org.eclipse.microprofile.metrics.MetricRegistry;

public class MicroProfileMetricsSessionBuilder
    extends SessionBuilder<MicroProfileMetricsSessionBuilder, CqlSession> {

  private MetricRegistry registry;

  public MicroProfileMetricsSessionBuilder withMetricRegistry(MetricRegistry registry) {
    this.registry = registry;
    return this;
  }

  @Override
  protected CqlSession wrap(CqlSession defaultSession) {
    return defaultSession;
  }

  @Override
  protected DriverContext buildContext(
      DriverConfigLoader configLoader, ProgrammaticArguments programmaticArguments) {
    return new MicroProfileDriverContext(configLoader, programmaticArguments, registry);
  }
}
