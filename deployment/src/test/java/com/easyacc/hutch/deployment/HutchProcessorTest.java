package com.easyacc.hutch.deployment;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.core.HutchConsumer;
import io.quarkus.test.QuarkusUnitTest;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 16:44 */
class HutchProcessorTest {
  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.application.name", "hutch-app")
          .overrideConfigKey("quarkus.hutch.name", "lake_web")
          //                    .overrideConfigKey("quarkus.log.level", "debug")
          .withApplicationRoot(jar -> jar.addClass(AbcConsumer.class).addClass(BbcConsumer.class));

  @Inject AbcConsumer abcConsumer;
  @Inject HutchConfig cfg;

  @Inject BeanManager beanManager;

  @Test
  void testHutchConsumerAllInCDI() {
    var beans = beanManager.getBeans(HutchConsumer.class);
    assertThat(beans).hasSize(2);
  }

  @Test
  void testLoadBeanFromCDI() {
    var beans = beanManager.getBeans(HutchConsumer.class);
    for (var bean : beans) {
      var h = (HutchConsumer) CDI.current().select(bean.getBeanClass()).get();
      assertThat(h.prefetch()).isEqualTo(2);
      assertThat(h.queue()).startsWith("hutch_");
      System.out.println(h.queue());
    }
  }

  @Test
  void testHutchConfig() {
    var c = CDI.current().select(HutchConfig.class).get();
    assertThat(c.name).isEqualTo("lake_web");
    assertThat(Hutch.name()).isEqualTo("lake_web");
  }
}
