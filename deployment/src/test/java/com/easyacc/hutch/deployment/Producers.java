package com.easyacc.hutch.deployment;

import com.easyacc.hutch.config.HutchConfig;
import io.quarkus.test.Mock;
import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.Config;
import org.mockito.Mockito;

/** Created by IntelliJ IDEA. User: kenyon Date: 2025/7/28 Time: 16:52 */
public class Producers {
  @Mock
  @Produces
  @ApplicationScoped
  public HutchConfig config(Config config) {
    var hc = config.unwrap(SmallRyeConfig.class).getConfigMapping(HutchConfig.class);
    return Mockito.spy(hc);
  }
}
