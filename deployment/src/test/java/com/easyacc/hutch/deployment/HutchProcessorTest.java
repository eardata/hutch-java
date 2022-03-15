package com.easyacc.hutch.deployment;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.core.HutchConsumer;
import io.quarkus.test.QuarkusUnitTest;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 16:44 */
class HutchProcessorTest {
  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.application.name", "hutch-app")
          //          .overrideConfigKey("quarkus.log.level", "debug")
          .withApplicationRoot(jar -> jar.addClass(AbcConsumer.class).addClass(BbcConsumer.class));

  @Inject AbcConsumer abcConsumer;

  @Inject BeanManager beanManager;

  @Test
  void testHutchConsumerAllInCDI() {
    var beans = beanManager.getBeans(HutchConsumer.class);
    assertThat(beans).hasSize(2);
  }
}
