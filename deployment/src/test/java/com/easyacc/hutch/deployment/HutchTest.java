package com.easyacc.hutch.deployment;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.SimpleConsumer;
import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.error_handlers.NoDelayMaxRetry;
import com.easyacc.hutch.publisher.BodyPublisher;
import com.easyacc.hutch.publisher.JsonPublisher;
import com.easyacc.hutch.publisher.LimitPublisher;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HutchTest {
  @RegisterExtension
  static final QuarkusUnitTest app =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.application.name", "hutch-app")
          // 默认关闭, 每一个测试需要自己 Hutch 实例
          .overrideConfigKey("quarkus.hutch.enable", "false")
          .overrideConfigKey("quarkus.hutch.name", "lake_web")
          .overrideConfigKey("quarkus.hutch.virtual-host", "test")
          .overrideConfigKey("quarkus.hutch.redis-url", "redis://localhost:6379")
          .overrideConfigKey("quarkus.hutch.worker-pool-size", "300")
          // .overrideConfigKey("quarkus.log.level", "debug")
          .withApplicationRoot(
              jar ->
                  jar.addClass(AbcConsumer.class)
                      .addClass(BbcConsumer.class)
                      .addClass(LongTimeConsumer.class));

  @Inject HutchConfig config;
  @Inject AbcConsumer abcConsumer;

  // 每一个测试都需要自己的 Hutch 实例
  Hutch newHutch() {
    config.enable = true;

    // 下面几个值再每一个测试中需要重置
    AbcConsumer.Timers.set(0);
    BbcConsumer.Timers.set(0);
    LongTimeConsumer.Runs.set(0);
    SimpleConsumer.ActiveConsumerCount.set(0);
    return new Hutch(config);
  }

  @Test
  void testHutchConsumerAllInCDI() {
    var hcs = Hutch.consumers();
    assertThat(hcs).hasSize(3);
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
  void hutchInIOC() {
    // 测试提供一个 Hutch 在 IOC 里面
    var h = CDI.current().select(Hutch.class).get();
    assertThat(h).isNotNull();
    assertThat(h.isStarted()).isFalse();
  }

  @Test
  void testEnqueue() throws IOException, InterruptedException {
    //    var h = CDI.current().select(Hutch.class).get();
    var h = newHutch();
    // 需要确保 queue 都存在, 需要调用 start 进行 declare
    h.start();

    assertThat(h.isStarted()).isTrue();
    assertThat(h.getConnPoolForConsumer().getMaxConcurrencyNumber()).isEqualTo(557);
    assertThat(h.getConnPoolForConsumer().getConnections().size()).isEqualTo(3);

    assertThat(h).isEqualTo(Hutch.current());

    abcConsumer.enqueue("abc");
    BodyPublisher.publish(AbcConsumer.class, "ccc");

    Thread.sleep(2000);
    var q = h.getCh().queueDelete(abcConsumer.queue());
    // 消息被消费了
    assertThat(q.getMessageCount()).isEqualTo(0);
    assertThat(AbcConsumer.Timers.get()).isEqualTo(2);

    h.stop();
    assertThat(h.isStarted()).isFalse();
  }

  @Test
  void testAdditionalBean() {
    var s = CDI.current().select(Hutch.class).get();
    assertThat(s).isNotNull();
    assertThat(s.isStarted()).isFalse();
    assertThat(s.getConfig()).isEqualTo(config);
  }

  @Test
  void testMaxRetry() throws InterruptedException {
    HutchConfig.getErrorHandlers().clear();
    HutchConfig.getErrorHandlers().add(new NoDelayMaxRetry());
    var h = newHutch();
    assertThat(h.isStarted()).isFalse();
    h.start();
    assertThat(h).isEqualTo(Hutch.current());
    BodyPublisher.publish(BbcConsumer.class, "bbc");
    TimeUnit.SECONDS.sleep(6);
    assertThat(BbcConsumer.Timers.get()).isEqualTo(2);
    h.stop();
    assertThat(h.isStarted()).isFalse();
  }

  @Test
  void testPublishJsonDelayRetry() throws InterruptedException, IOException {
    HutchConfig.getErrorHandlers().clear();
    HutchConfig.getErrorHandlers().add(new NoDelayMaxRetry());
    var h = newHutch();
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

  @Test
  void testPublishWithSchedule() throws InterruptedException, IOException {
    HutchConfig.getErrorHandlers().clear();
    HutchConfig.getErrorHandlers().add(new NoDelayMaxRetry());

    var h = newHutch();
    h.start();
    var hc = HutchConsumer.get(AbcConsumer.class);
    LimitPublisher.publish(AbcConsumer.class, "ccc");

    assertThat(AbcConsumer.Timers.get()).isEqualTo(0);

    TimeUnit.SECONDS.sleep(2);
    assertThat(AbcConsumer.Timers.get()).isEqualTo(1);

    var q = h.getCh().queueDelete(hc.queue());
    assertThat(q.getMessageCount()).isEqualTo(0);
    h.stop();
    assertThat(h.isStarted()).isFalse();
  }

  @Test
  void testClearScheduleQueue() {
    var h = newHutch();
    h.start();
    assertThat(h.clearScheduleQueues()).isTrue();
    assertThat(h.clearHutchConsumerQueues()).isTrue();
    h.stop();
  }

  @Test
  void testCloseHutch() throws InterruptedException, IOException {
    var h = newHutch();
    h.start();

    // 推送 1000 个任务, 等待 3s, 完成一批次 300 个并发任务,其他的没有完成的任务全部 nack 回去,只完成 300 个任务
    var msg = 1000;
    for (var i = 0; i < msg; i++) {
      BodyPublisher.publish(LongTimeConsumer.class, "body");
    }

    // 等待 2s 让消息去往 mq,  然后再回到 hutch, 能够第一批次全部处理完
    Thread.sleep(3000);

    h.stop();
    assertThat(LongTimeConsumer.Runs.get()).isEqualTo(0);

    // 等待 mq 将消息重新放回队列可以进行消费
    Thread.sleep(500);
    // 确认在 mq 中的消息, 仍然没有被消费
    h.connect();
    var ok = h.getCh().queueDelete(HutchConsumer.get(LongTimeConsumer.class).queue());
    assertThat(ok.getMessageCount()).isEqualTo(700);
  }
}
