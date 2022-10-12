package com.easyacc.hutch.publisher;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.util.HutchUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.BasicProperties.Builder;
import java.util.Collections;
import java.util.List;

/** 单独将 String/Bytes 的消息的 Publisher 独立出来 */
public interface BodyPublisher {
  /** 提供默认 text 的参数 */
  static Builder amqpBuilder() {
    return new BasicProperties().builder().contentType("text/plain").contentEncoding("UTF-8");
  }
  /** 发送字符串 - HutchConsumer */
  static void publish(Class<? extends HutchConsumer> consumer, String msg) {
    publish(HutchConsumer.rk(consumer), msg);
  }

  /** 发送字符串 */
  static void publish(String routingKey, String msg) {
    Hutch.publish(routingKey, amqpBuilder().build(), msg.getBytes());
  }

  /**
   * 利用 HutchConsumer, 延迟一定时间, 将 Object 转为 json 发送消息
   *
   * @param delayInSec 梯度延迟的时间, 单位 s
   */
  static void publishWithDelay(
      int delayInSec, Class<? extends HutchConsumer> consumer, String msg) {
    publishWithDelay(delayInSec, HutchConsumer.rk(consumer), msg);
  }

  /**
   * 指定 routing-key, 延迟一定时间, 将 Object 转为 json 发送消息
   *
   * @param delayInSec 梯度延迟的时间, 单位 s
   * @param routingKey 消息的 routing-key
   * @param msg 具体的 json 格式的消息体
   */
  static void publishWithDelay(int delayInSec, String routingKey, String msg) {
    var delayInMs = delayInSec * 1000L;

    var props =
        amqpBuilder()
            .expiration(HutchUtils.fixDealyTime(delayInMs) + "")
            .headers(Collections.singletonMap("CC", List.of(routingKey)));
    try {
      var body = Hutch.om().writeValueAsBytes(msg);
      Hutch.publishWithDelay(delayInMs, props.build(), body);
    } catch (JsonProcessingException e) {
      Hutch.log().error("publishJson error", e);
    }
  }
}
