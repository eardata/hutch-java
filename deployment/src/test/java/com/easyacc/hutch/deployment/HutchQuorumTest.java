package com.easyacc.hutch.deployment;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.error_handlers.NoDelayMaxRetry;
import com.easyacc.hutch.publisher.BodyPublisher;
import com.easyacc.hutch.publisher.JsonPublisher;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** 测试 quorum queue, 测试的内容与 classic queue 基本一致, 但因为 quarkus 的 test 中的 app 初始化方式, 所以暂时先直接复制测试代码 */
class HutchQuorumTest {
  @RegisterExtension
  static final QuarkusUnitTest app =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.application.name", "hutch-app")
          .overrideConfigKey("quarkus.hutch.name", "lake_web")
          .overrideConfigKey("quarkus.hutch.quorum", "true")
          .overrideConfigKey("quarkus.hutch.virtual-host", "test_quorum")
          //                    .overrideConfigKey("quarkus.log.level", "debug")
          .withApplicationRoot(jar -> jar.addClass(AbcConsumer.class).addClass(BbcConsumer.class));

  @Inject HutchConfig config;
  @Inject AbcConsumer abcConsumer;

  @Test
  void testEnqueue() throws IOException, InterruptedException {
    //    var h = CDI.current().select(Hutch.class).get();
    var h = new Hutch(config);
    // 需要确保 queue 都存在, 需要调用 start 进行 declare
    h.start();
    assertThat(h.isStarted()).isTrue();

    assertThat(h).isEqualTo(Hutch.current());

    var q = h.getCh().queueDeclarePassive(abcConsumer.queue());

    abcConsumer.enqueue("abc");
    BodyPublisher.publish(AbcConsumer.class, "ccc");

    Thread.sleep(100);
    q = h.getCh().queueDeclarePassive(abcConsumer.queue());
    // 消息被消费了
    assertThat(q.getMessageCount()).isEqualTo(0);
    assertThat(AbcConsumer.Timers.get()).isEqualTo(2);
    h.stop();
    assertThat(h.isStarted()).isFalse();
  }

  @Test
  void testMaxRetry() throws InterruptedException {
    HutchConfig.getErrorHandlers().clear();
    HutchConfig.getErrorHandlers().add(new NoDelayMaxRetry());
    var c = Hutch.current();
    var h = CDI.current().select(Hutch.class).get();
    assertThat(h.isStarted()).isFalse();
    h.start();
    assertThat(h).isEqualTo(Hutch.current());
    BodyPublisher.publish(BbcConsumer.class, "bbc");
    TimeUnit.SECONDS.sleep(6);
    assertThat(BbcConsumer.Timers.get()).isEqualTo(2);
    h.stop();
  }

  @Test
  void testPublishJsonDelayRetry() throws InterruptedException {
    HutchConfig.getErrorHandlers().clear();
    HutchConfig.getErrorHandlers().add(new NoDelayMaxRetry());
    var h = CDI.current().select(Hutch.class).get();
    h.start();
    JsonPublisher.publishWithDelay(1, AbcConsumer.class, "ccc");
    var a = AbcConsumer.Timers.get();
    // 等待在 5s 以内
    TimeUnit.SECONDS.sleep(2);
    assertThat(AbcConsumer.Timers.get()).isEqualTo(a);
    TimeUnit.SECONDS.sleep(6);
    assertThat(AbcConsumer.Timers.get()).isEqualTo(a + 1);
    h.stop();
  }
}
