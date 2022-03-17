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

  //  @Test
  void hutchInIOC() {
    // 测试提供一个 Hutch 在 IOC 里面
    var h = CDI.current().select(Hutch.class).get();
    assertThat(h).isNotNull();
  }

  @Test
  void testHutchConfig() throws InterruptedException {
    var config = CDI.current().select(HutchConfig.class).get();
    assertThat(config).isNotNull();

    assertThat(cfg.name).isEqualTo("lake_web");
    assertThat(Hutch.name()).isEqualTo("lake_web");
    var h = new Hutch(cfg).start();
    assertThat(h.isStarted()).isTrue();
    h.stop();
    assertThat(h.isStarted()).isFalse();
  }
}
