package com.easyacc.hutch.deployment;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.SimpleConsumer;
import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.error_handlers.NoDelayMaxRetry;
import com.easyacc.hutch.publisher.BodyPublisher;
import com.easyacc.hutch.publisher.JsonPublisher;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** 测试 quorum queue, 测试的内容与 classic queue 基本一致, 但因为 quarkus 的 test 中的 app 初始化方式, 所以暂时先直接复制测试代码 */
class HutchQuorumTest {
  public Hutch hutch(HutchConfig config) {
    config.enable = true;

    // 下面几个值再每一个测试中需要重置
    AbcConsumer.Timers.set(0);
    BbcConsumer.Timers.set(0);
    LongTimeConsumer.Runs.set(0);

    SimpleConsumer.ActiveConsumerCount.set(0);
    return new Hutch(config);
  }

  public static QuarkusUnitTest quarkusApp(String vhost) {
    var app =
        new QuarkusUnitTest()
            .overrideConfigKey("quarkus.application.name", "hutch-app")
            // 默认关闭, 每一个测试需要自己 Hutch 实例
            .overrideConfigKey("quarkus.hutch.enable", "false")
            .overrideConfigKey("quarkus.hutch.name", "lake_web")
            .overrideConfigKey("quarkus.hutch.redis-url", "redis://localhost:6379")
            .overrideConfigKey("quarkus.hutch.worker-pool-size", "300")
            .overrideConfigKey("quarkus.hutch.virtual-host", vhost)
            //                    .overrideConfigKey("quarkus.log.level", "debug")
            .withApplicationRoot(
                jar ->
                    jar.addClass(AbcConsumer.class)
                        .addClass(BbcConsumer.class)
                        .addClass(LongTimeConsumer.class));

    app.overrideConfigKey("quarkus.hutch.quorum", "true");
    return app;
  }

  @RegisterExtension static final QuarkusUnitTest app = quarkusApp("test_quorum");

  @Inject HutchConfig config;
  @Inject AbcConsumer abcConsumer;

  @Test
  void testEnqueue() throws IOException, InterruptedException {
    var h = hutch(config);
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
    var h = hutch(config);
    assertThat(h.isStarted()).isFalse();
    h.start();

    assertThat(h).isEqualTo(Hutch.current());
    BodyPublisher.publish(BbcConsumer.class, "bbc");
    TimeUnit.SECONDS.sleep(6);
    assertThat(BbcConsumer.Timers.get()).isEqualTo(2);
    h.stop();
  }

  @Test
  void testPublishJsonDelayRetry() throws InterruptedException, IOException {
    HutchConfig.getErrorHandlers().clear();
    HutchConfig.getErrorHandlers().add(new NoDelayMaxRetry());
    var h = hutch(config);
    h.start();
    var hc = HutchConsumer.get(AbcConsumer.class);

    JsonPublisher.publishWithDelay(1, AbcConsumer.class, "ccc");
    // 1s 会进入等待 5s 后重试
    TimeUnit.SECONDS.sleep(2);
    assertThat(AbcConsumer.Timers.get()).isEqualTo(0);
    // 等待总共 6s 后, 有一次执行
    TimeUnit.SECONDS.sleep(4);
    assertThat(AbcConsumer.Timers.get()).isEqualTo(1);

    var q = h.getCh().queueDelete(hc.queue());
    assertThat(q.getMessageCount()).isEqualTo(0);
    h.stop();
    assertThat(h.isStarted()).isFalse();
  }
}
