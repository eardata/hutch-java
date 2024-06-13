package com.easyacc.hutch;

import static com.easyacc.hutch.Hutch.getMessagePropertiesConverter;

import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.core.ConsumeContext;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Message;
import com.easyacc.hutch.devmode.ConsumerHotReplaceSetup;
import com.easyacc.hutch.util.RabbitUtils;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/** 实现 RabbitMQ 的 Java SDK 的消费者, 其负责 HutchConsumer 的执行与异常重试处理 */
@Slf4j
public class SimpleConsumer extends DefaultConsumer {

  /** 所有不同的 SimpleConsumer 正在同时并发运行的数量 */
  public static final AtomicInteger ActiveConsumerCount = new AtomicInteger(0);

  private final String queue;

  private final HutchConsumer hutchConsumer;

  public SimpleConsumer(Channel channel, HutchConsumer hc) {
    super(channel);
    this.queue = hc.queue();
    this.hutchConsumer = hc;
  }

  /**
   * 做开始 Consume 之前的动作
   *
   * @throws IOException basicQos, basicConsume, queueDeclarePassive 操作失败的异常
   */
  public void consume() throws IOException {
    var autoAck = false;
    var ch = this.getChannel();
    ch.basicQos(this.hutchConsumer.prefetch());
    ch.basicConsume(this.hutchConsumer.queue(), autoAck, this);
    ch.queueDeclarePassive(this.hutchConsumer.queue());
  }

  @Override
  public void handleDelivery(
      String consumerTag, Envelope envelope, BasicProperties properties, byte[] body) {
    var messageProperties =
        getMessagePropertiesConverter().toMessageProperties(properties, envelope, "UTF-8");
    messageProperties.setConsumerTag(consumerTag);
    messageProperties.setConsumerQueue(this.queue);
    Message message = new Message(body, messageProperties);
    long deliveryTag = envelope.getDeliveryTag();
    callHutchConsumer(message, deliveryTag);
  }

  /** 具体调用 HutchConsumer 实例类的 onMessage 方法以及错误相关的处理入口 */
  private void callHutchConsumer(Message msg, long deliveryTag) {
    ActiveConsumerCount.incrementAndGet();
    var cc = ConsumeContext.ofConsumer(hutchConsumer);

    try {
      // 如果 Hutch 正在停止中, 那么已经到客户端的消息, 将他们全部 nack 回到 mq 中
      if (Hutch.current().isStopping()) {
        nack(deliveryTag);
        return;
      }

      Hutch.setContext(cc);

      if (this.hutchConsumer.isLogTime()) {
        cc.info("start");
      }

      this.hutchConsumer.onMessage(msg);
    } catch (Exception e) {
      catchByHandlers(msg, e);
      // 最终的异常要在这里处理掉, 不需要将执行期异常往上抛, 保持 channel 正常
      cc.warn(String.format("%s consumer error", this.hutchConsumer.name()), e);
    } finally {
      // 在 Dev 环境下, 每一次消息都进行一次是否需要 hot reload 的 check
      ConsumerHotReplaceSetup.reloadCheck();

      // 如果任务在 Stopping 的过程中结束, 是能够标记为完成的
      ack(deliveryTag);

      // 在整个任务的最后清理所有事情. 这样避免检查日志的时候, 异常的日志在任务结束之后出现.
      if (this.hutchConsumer.isLogTime()) {
        cc.info("done: {} ms", cc.tik());
      }

      Hutch.removeContext();
      ActiveConsumerCount.decrementAndGet();
    }
  }

  /**
   * 针对 HutchConfig 配置的 ErrorHandlers 进行处理
   *
   * @param msg
   * @param e
   */
  public void catchByHandlers(Message msg, Exception e) {
    // 如果没有启动, 则不进入 error Handler 机制
    if (!Hutch.current().isStarted()) {
      return;
    }

    for (var eh : HutchConfig.getErrorHandlers()) {
      try {
        eh.handle(this.hutchConsumer, msg, e);
      } catch (Exception e1) {
        // ignore error handler exception
        Hutch.log().error("error handler " + eh.getClass().getName() + " error", e1);
      }
    }
  }

  public void ack(long deliveryTag) {
    // 开启状态才 ack, 避免停止 Hutch 之后, 但任务在执行无法 stop, 最终也无法 ack 报错
    if (getChannel().isOpen()) {
      try {
        getChannel().basicAck(deliveryTag, false);
      } catch (IOException e) {
        Hutch.log().error("ack error", e);
      }
    }
  }

  /**
   * 撤回消息给 MQ
   *
   * @param deliveryTag
   */
  public void nack(long deliveryTag) {
    if (getChannel().isOpen()) {
      try {
        getChannel().basicNack(deliveryTag, false, true);
      } catch (IOException e) {
        Hutch.log().error("nack error", e);
      }
    }
  }

  public void close() {
    RabbitUtils.cancelConsumer(getChannel(), getConsumerTag());
    RabbitUtils.closeChannel(getChannel());
  }
}
