package com.easyacc.hutch.deployment;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.config.HutchConfig;
import io.quarkus.test.QuarkusUnitTest;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/10/13 Time: 00:33 */
public class HutchConfigTest {
  @RegisterExtension
  static final QuarkusUnitTest app =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.application.name", "hutch-app")
          .overrideConfigKey("quarkus.hutch.name", "lake_web")
          .overrideConfigKey("quarkus.hutch.enable", "false")
          .overrideConfigKey("quarkus.hutch.virtual-host", "test")
          .overrideConfigKey("quarkus.hutch.redis-url", "redis://localhost:6379")
          // .overrideConfigKey("quarkus.log.level", "debug")
          .withApplicationRoot(jar -> jar.addClass(AbcConsumer.class).addClass(BbcConsumer.class));

  @Inject HutchConfig config;

  /** 测试 HutchConfig */
  @Test
  void testHutchConfig() {
    var cfg = CDI.current().select(HutchConfig.class).get();
    assertThat(cfg).isNotNull();
    assertThat(cfg.schdulePoolSize).isEqualTo(6);
    assertThat(cfg.enable).isFalse();

    assertThat(cfg.name).isEqualTo("lake_web");
    assertThat(Hutch.name()).isEqualTo("lake_web");
    var h = new Hutch(cfg).start();
    assertThat(h.isStarted()).isFalse();

    // 强制开启
    cfg.enable = true;
    h = new Hutch(cfg).start();
    assertThat(h.isStarted()).isTrue();
    h.stop();
    assertThat(h.isStarted()).isFalse();
  }

  @Test
  void testThresoldInstance() {
    var c = new AbcConsumer();
    var t1 = c.threshold();
    var t2 = c.threshold();
    assertThat(t1).isNotEqualTo(t2);
  }

  @Test
  void testCachedThreshold() {
    var c = new AbcConsumer();
    var c2 = new BbcConsumer();

    var t1 = Hutch.threshold(c);
    var t2 = Hutch.threshold(c);
    assertThat(t1).isEqualTo(t2);

    var t3 = Hutch.threshold(c2);
    assertThat(t3).isNull();
  }
}
