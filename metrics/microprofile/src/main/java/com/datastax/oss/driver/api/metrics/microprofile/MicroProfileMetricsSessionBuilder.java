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
package com.datastax.oss.driver.api.metrics.microprofile;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.session.ProgrammaticArguments;
import com.datastax.oss.driver.api.core.session.SessionBuilder;
import com.datastax.oss.driver.internal.metrics.microprofile.MicroProfileDriverContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.eclipse.microprofile.metrics.MetricRegistry;

public class MicroProfileMetricsSessionBuilder
    extends SessionBuilder<MicroProfileMetricsSessionBuilder, CqlSession> {

  private MetricRegistry registry;

  @NonNull
  public MicroProfileMetricsSessionBuilder withMetricRegistry(@NonNull MetricRegistry registry) {
    this.registry = registry;
    return this;
  }

  @NonNull
  @Override
  protected CqlSession wrap(@NonNull CqlSession defaultSession) {
    return defaultSession;
  }

  @NonNull
  @Override
  protected DriverContext buildContext(
      @NonNull DriverConfigLoader configLoader,
      @NonNull ProgrammaticArguments programmaticArguments) {
    return new MicroProfileDriverContext(configLoader, programmaticArguments, registry);
  }
}
