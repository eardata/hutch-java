package com.easyacc.hutch.publisher;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.util.HutchUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.AMQP.BasicProperties;
import java.util.Collections;
import java.util.List;

/** 单独将 Json 的消息的 Publisher 独立出来 */
public interface JsonPublisher {

  /** 利用 HutchConsumer 将 Object 转为 json 发送消息 */
  static void publish(Class<? extends HutchConsumer> consumer, Object msg) {
    publish(HutchConsumer.rk(consumer), msg);
  }

  /** 指定 routing-key 将 Object 转为 json 发送消息 */
  static void publish(String routingKey, Object msg) {
    var props =
        new BasicProperties().builder().contentType("application/json").contentEncoding("UTF-8");
    byte[] body = new byte[0];
    try {
      body = Hutch.om().writeValueAsBytes(msg);
    } catch (JsonProcessingException e) {
      Hutch.log().error("publishJson error", e);
    }
    Hutch.publish(routingKey, props.build(), body);
  }

  /** 利用 HutchConsumer, 延迟一定时间, 将 Object 转为 json 发送消息 */
  static void publishWithDelay(int delay, Class<? extends HutchConsumer> consumer, Object msg) {
    publishWithDelay(delay, HutchConsumer.rk(consumer), msg);
  }

  /**
   * 指定 routing-key, 延迟一定时间, 将 Object 转为 json 发送消息
   *
   * @param delay 梯度延迟的时间, 单位 s
   * @param routingKey 消息的 routing-key
   * @param msg 具体的 json 格式的消息体
   */
  static void publishWithDelay(int delay, String routingKey, Object msg) {
    var delayInMs = delay * 1000L;

    var props =
        new BasicProperties()
            .builder()
            .contentType("application/json")
            .expiration(HutchUtils.fixDealyTime(delayInMs) + "")
            .headers(Collections.singletonMap("CC", List.of(routingKey)))
            .contentEncoding("UTF-8");
    byte[] body;
    try {
      body = Hutch.om().writeValueAsBytes(msg);
      Hutch.publishWithDelay(delayInMs, props.build(), body);
    } catch (JsonProcessingException e) {
      Hutch.log().error("publishJson error", e);
    }
  }
}
