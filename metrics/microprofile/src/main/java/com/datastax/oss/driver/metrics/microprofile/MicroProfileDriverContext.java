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

import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.session.ProgrammaticArguments;
import com.datastax.oss.driver.internal.core.context.DefaultDriverContext;
import com.datastax.oss.driver.internal.core.metrics.MetricsFactory;
import org.eclipse.microprofile.metrics.MetricRegistry;

public class MicroProfileDriverContext extends DefaultDriverContext {

  private final MetricRegistry registry;

  public MicroProfileDriverContext(
      DriverConfigLoader configLoader,
      ProgrammaticArguments programmaticArguments,
      MetricRegistry registry) {
    super(configLoader, programmaticArguments);
    this.registry = registry;
  }

  @Override
  protected MetricsFactory buildMetricsFactory() {
    return new MicroProfileMetricsFactory(this, registry);
  }
}