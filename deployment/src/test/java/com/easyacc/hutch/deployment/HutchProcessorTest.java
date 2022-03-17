package com.easyacc.hutch.deployment;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.core.HutchConsumer;
import io.quarkus.test.QuarkusUnitTest;
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
          .overrideConfigKey("quarkus.hutch.virtual-host", "test")
          //                    .overrideConfigKey("quarkus.log.level", "debug")
          .withApplicationRoot(jar -> jar.addClass(AbcConsumer.class).addClass(BbcConsumer.class));

  @Inject AbcConsumer abcConsumer;
  @Inject HutchConfig cfg;

  @Test
  void testHutchConsumerAllInCDI() {
    var hcs = Hutch.consumers();
    assertThat(hcs).hasSize(2);
    hcs.forEach(hc -> assertThat(hc.queueArguments()).isEmpty());
  }

  @Test
  void testLoadBeanFromCDI() {
    var beans = CDI.current().getBeanManager().getBeans(HutchConsumer.class);
    for (var bean : beans) {
      var h = (HutchConsumer) CDI.current().select(bean.getBeanClass()).get();
      assertThat(h.prefetch()).isEqualTo(2);
      assertThat(h.queue()).startsWith("lake_web_");
      System.out.println(h.queue());
    }
  }

  @Test
  void testHutchConfig() throws InterruptedException {
    assertThat(cfg.name).isEqualTo("lake_web");
    assertThat(Hutch.name()).isEqualTo("lake_web");
    new Hutch(cfg).start();
    Thread.sleep(2000);
  }
}
