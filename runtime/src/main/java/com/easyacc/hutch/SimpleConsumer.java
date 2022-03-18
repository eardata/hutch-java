package com.easyacc.hutch;

import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Message;
import com.easyacc.hutch.core.MessageProperties;
import com.easyacc.hutch.support.DefaultMessagePropertiesConverter;
import com.easyacc.hutch.support.MessagePropertiesConverter;
import com.easyacc.hutch.util.HutchUtils;
import com.easyacc.hutch.util.RabbitUtils;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/** 实现 RabbitMQ 的 Java SDK 的消费者, 其负责 HutchConsumer 的执行与异常重试处理 */
@Slf4j
class SimpleConsumer extends DefaultConsumer {
  private static final MessagePropertiesConverter MPC = new DefaultMessagePropertiesConverter();
  private final String queue;

  private final HutchConsumer hutchConsumer;

  public SimpleConsumer(Channel channel, String queue, HutchConsumer hc) {
    super(channel);
    this.queue = queue;
    this.hutchConsumer = hc;
  }

  public MessagePropertiesConverter getMessagePropertiesConverter() {
    return SimpleConsumer.MPC;
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
      this.hutchConsumer.onMessage(msg);
      // 暂时不支持手动 ack, 全部由 SimpleConsumer 进行自动 ack, 如果任务正常结束就及时 ack
    } catch (AlreadyClosedException e) {
      log.warn("hutch consumer already closed, ignore message", e);
    } catch (Exception e) {
      // 重试检查
      var props = msg.getMessageProperties();
      var retryCount = getXDeathLatestCount(props);
      if (retryCount < hutchConsumer.maxRetry()) {
        publishWithDelay(backoffDelay(retryCount), msg);
      } else {
        log.info(
            "message retry count ({}) reach max ({}), ignore message",
            retryCount,
            hutchConsumer.maxRetry());
      }
      // 最终的异常要在这里处理掉, 不需要将执行期异常往上抛, 保持 channel 正常
      log.warn("msg error", e);
    } finally {
      try {
        getChannel().basicAck(deliveryTag, false);
      } catch (IOException e) {
        // ack 失败只能记录
        log.error("ack error", e);
      }
    }
  }

  public long backoffDelay(long retryCount) {
    return (long) (Math.pow(3, retryCount) + 2) * 1000;
  }

  public void publishWithDelay(long delayInMs, Message msg) {
    Hutch.internalPublishWithDelay(
        delayInMs, convertToDelayProps(msg.getMessageProperties(), delayInMs), msg.getBody());

    var fixDelay = HutchUtils.fixDealyTime(delayInMs);
    log.debug(
        "publish with delay {} using routing_key {} and origin routing_key: {}",
        fixDelay,
        Hutch.delayRoutingKey(fixDelay),
        this.hutchConsumer.routingKey());
  }

  /** 处理 Delay Message 需要处理的 header 信息等等, 保留原来消息中的 props header 等信息 */
  public BasicProperties convertToDelayProps(MessageProperties props, long delay) {
    var fixDelay = HutchUtils.fixDealyTime(delay);
    props.setExpiration(fixDelay + "");
    props.setHeader("CC", List.of(this.hutchConsumer.routingKey()));
    return getMessagePropertiesConverter().fromMessageProperties(props, "UTF-8");
  }

  /** 获取重试的最大次数 */
  public long getXDeathLatestCount(MessageProperties props) {
    var xDeath = props.getXDeathHeader();
    if (xDeath == null) {
      return 0;
    }
    var c = new AtomicLong(0L);
    xDeath.forEach(
        m -> {
          var cs = m.get("count");
          if (cs instanceof Long) {
            c.set((long) cs);
          }
        });
    return c.get();
  }

  public void cancel() {
    RabbitUtils.cancelConsumer(getChannel(), getConsumerTag());
  }
}
