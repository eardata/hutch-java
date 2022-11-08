package com.easyacc.hutch;

import static com.easyacc.hutch.Hutch.getMessagePropertiesConverter;

import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Message;
import com.easyacc.hutch.util.RabbitUtils;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/** 实现 RabbitMQ 的 Java SDK 的消费者, 其负责 HutchConsumer 的执行与异常重试处理 */
@Slf4j
class SimpleConsumer extends DefaultConsumer {
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
    try {
      if (this.hutchConsumer.isLogTime()) {
        var name = this.hutchConsumer.name();
        var tid = UUID.randomUUID().toString().replaceAll("-", "");
        var begin = System.currentTimeMillis();
        try {
          log.info("{} TID - {} start", name, tid);
          this.hutchConsumer.onMessage(msg);
        } finally {
          log.info("{} TID - {} done: {} ms", name, tid, System.currentTimeMillis() - begin);
        }
      } else {
        this.hutchConsumer.onMessage(msg);
      } // 暂时不支持手动 ack, 全部由 SimpleConsumer 进行自动 ack, 如果任务正常结束就及时 ack
    } catch (Exception e) {
      for (var eh : HutchConfig.getErrorHandlers()) {
        try {
          eh.handle(this.hutchConsumer, msg, e);
        } catch (Exception e1) {
          // ignore error handler exception
          log.error("error handler " + eh.getClass().getName() + " error", e1);
        }
      }
      // 最终的异常要在这里处理掉, 不需要将执行期异常往上抛, 保持 channel 正常
      log.warn(String.format("%s consumer error", this.hutchConsumer.name()), e);
    } finally {
      try {
        // 开启状态才 ack, 避免停止 Hutch 之后, 但任务在执行无法 stop, 最终也无法 ack 报错
        if (getChannel().isOpen()) {
          getChannel().basicAck(deliveryTag, false);
        }
      } catch (IOException e) {
        // ack 失败只能记录
        log.error("ack error", e);
      }
    }
  }

  public void cancel() {
    RabbitUtils.cancelConsumer(getChannel(), getConsumerTag());
  }

  public void close() {
    RabbitUtils.closeChannel(getChannel());
  }
}
