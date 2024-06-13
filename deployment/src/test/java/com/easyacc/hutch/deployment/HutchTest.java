package com.easyacc.hutch.deployment;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.Hutch;
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
          .overrideConfigKey("quarkus.hutch.name", "lake_web")
          .overrideConfigKey("quarkus.hutch.virtual-host", "test")
          .overrideConfigKey("quarkus.hutch.redis-url", "redis://localhost:6379")
          // .overrideConfigKey("quarkus.log.level", "debug")
          .withApplicationRoot(
              jar ->
                  jar.addClass(AbcConsumer.class)
                      .addClass(BbcConsumer.class)
                      .addClass(LongTimeConsumer.class));

  @Inject HutchConfig config;
  @Inject AbcConsumer abcConsumer;

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
  void hutchInIOC() {
    // 测试提供一个 Hutch 在 IOC 里面
    var h = CDI.current().select(Hutch.class).get();
    assertThat(h).isNotNull();
    assertThat(h.isStarted()).isFalse();
  }

  @Test
  void testEnqueue() throws IOException, InterruptedException {
    //    var h = CDI.current().select(Hutch.class).get();
    var h = new Hutch(config);
    // 需要确保 queue 都存在, 需要调用 start 进行 declare
    h.start();
    assertThat(h.isStarted()).isTrue();
    assertThat(h.getConnPoolForConsumer().getMaxConcurrencyNumber()).isEqualTo(257);
    assertThat(h.getConnPoolForConsumer().getConnections().size()).isEqualTo(2);

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
    var c = Hutch.current();
    var h = CDI.current().select(Hutch.class).get();
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
    assertThat(h.isStarted()).isFalse();
  }

  @Test
  void testPublishWithSchedule() throws InterruptedException {
    HutchConfig.getErrorHandlers().clear();
    HutchConfig.getErrorHandlers().add(new NoDelayMaxRetry());

    var h = CDI.current().select(Hutch.class).get();
    h.start();
    LimitPublisher.publish(AbcConsumer.class, "ccc");

    var a = AbcConsumer.Timers.get();
    assertThat(AbcConsumer.Timers.get()).isEqualTo(a);

    TimeUnit.SECONDS.sleep(2);
    assertThat(AbcConsumer.Timers.get()).isEqualTo(a + 1);
    h.stop();
    assertThat(h.isStarted()).isFalse();
  }

  @Test
  void testClearScheduleQueue() {
    var h = CDI.current().select(Hutch.class).get();
    h.start();
    assertThat(h.clearScheduleQueues()).isTrue();
    assertThat(h.clearHutchConsumerQueues()).isTrue();
  }

  @Test
  void testCloseHutch() throws InterruptedException, IOException {
    var h = CDI.current().select(Hutch.class).get();
    h.start();
    BodyPublisher.publish(LongTimeConsumer.class, "body");
    // 等待 1s 让消息去往 ms 然后再回到 hutch 开始执行
    Thread.sleep(500);

    h.stop();
    assertThat(LongTimeConsumer.Runs.get()).isEqualTo(0);

    // 等待 mq 将消息重新放回队列可以进行消费
    Thread.sleep(3000);
    // 确认在 mq 中的消息, 仍然没有被消费
    h.connect();
    var ok = h.getCh().queueDelete(HutchConsumer.get(LongTimeConsumer.class).queue());
    assertThat(ok.getMessageCount()).isEqualTo(1);
  }
}
