package com.easyacc.hutch.publisher;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.core.HutchConsumer;
import com.rabbitmq.client.AMQP.BasicProperties;

/** 单独将 String/Bytes 的消息的 Publisher 独立出来 */
public interface BodyPublisher {
  /** 发送字符串 - HutchConsumer */
  static void publish(Class<? extends HutchConsumer> consumer, String msg) {
    publish(HutchConsumer.rk(consumer), msg);
  }

  /** 最原始的发送 bytes - HutchConsumer */
  static void publish(Class<? extends HutchConsumer> consumer, BasicProperties props, byte[] body) {
    Hutch.publish(HutchConsumer.rk(consumer), props, body);
  }

  /** 发送字符串 */
  static void publish(String routingKey, String msg) {
    var props = new BasicProperties().builder().contentType("text/plain").contentEncoding("UTF-8");
    Hutch.publish(routingKey, props.build(), msg.getBytes());
  }
}
