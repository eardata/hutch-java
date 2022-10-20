package com.easyacc.hutch.publisher;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.util.HutchUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.BasicProperties.Builder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/** 单独将 Json 的消息的 Publisher 独立出来 */
public interface JsonPublisher {

  /** 提供默认 json 的参数 */
  static Builder amqpBuilder() {
    return new BasicProperties().builder().contentType("application/json").contentEncoding("UTF-8");
  }

  /** 利用 HutchConsumer 将 Object 转为 json 发送消息 */
  static void publish(Class<? extends HutchConsumer> consumer, Object msg) {
    publish(HutchConsumer.rk(consumer), msg);
  }

  /** 指定 routing-key 将 msg 发送消息 */
  static void publish(String routingKey, String msg) {
    Hutch.publish(routingKey, amqpBuilder().build(), msg.getBytes(StandardCharsets.UTF_8));
  }

  /** 指定 routing-key 将 Object 转为 json 发送消息 */
  static void publish(String routingKey, Object msg) {
    byte[] body = new byte[0];
    try {
      body = Hutch.om().writeValueAsBytes(msg);
    } catch (JsonProcessingException e) {
      Hutch.log().error("JsonPublisher.publish error", e);
    }
    Hutch.publish(routingKey, amqpBuilder().build(), body);
  }

  /**
   * 利用 HutchConsumer, 延迟一定时间, 将 Object 转为 json 发送消息
   *
   * @param delayInSec 梯度延迟的时间, 单位 s
   */
  static void publishWithDelay(
      int delayInSec, Class<? extends HutchConsumer> consumer, Object msg) {
    publishWithDelay(delayInSec, HutchConsumer.rk(consumer), msg);
  }

  /**
   * 指定 routing-key, 延迟一定时间, 将 Object 转为 json 发送消息
   *
   * @param delayInSec 梯度延迟的时间, 单位 s
   * @param routingKey 消息的 routing-key
   * @param msg 具体的 json 格式的消息体
   */
  static void publishWithDelay(int delayInSec, String routingKey, Object msg) {
    var delayInMs = delayInSec * 1000L;

    var props =
        amqpBuilder()
            .expiration(HutchUtils.fixDealyTime(delayInMs) + "")
            .headers(Collections.singletonMap("CC", List.of(routingKey)));
    try {
      var body = Hutch.om().writeValueAsBytes(msg);
      Hutch.publishWithDelay(delayInMs, props.build(), body);
    } catch (JsonProcessingException e) {
      Hutch.log().error("JsonPublisher.publishWithDelay error", e);
    }
  }
}
