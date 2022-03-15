package com.easyacc.hutch.core;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.util.HutchUtils;
import java.util.Map;

/**
 * 一个 HutchConsumer, 只消耗一个队列.
 *
 * @see <a href="https://codertw.com/%E7%A8%8B%E5%BC%8F%E8%AA%9E%E8%A8%80/431837/">Java 8 default
 *     method</a>
 */
public interface HutchConsumer {

  /** 每一个 Channel 能够拥有的 prefech, 避免单个 channel 积累太多任务. default: 2 */
  default int prefetch() {
    return 2;
  }

  /** 多少并发线程. default: 1 */
  default int concurrency() {
    return 1;
  }

  /** 绑定的队列名称(down case). default: <Hutch.name>_clazz.simpleName */
  default String queue() {
    var clazz =
        HutchUtils.upCamelToLowerUnderscore(getClass().getSimpleName())
            // 清理, 只留下需要的名字, 去除后缀
            .replace("__subclass", "");
    return Hutch.prefixQueue(clazz);
  }

  /** 最大重试; default: 1 */
  default int maxRetry() {
    return 1;
  }

  /** 当前消息使用的 routing key, 虽然可以使用多个, 但这个先只处理一个的情况. 默认情况下, routing key 与 queue 同名 */
  default String routingKey() {
    return this.queue();
  }

  /** 初始化 Queue 需要的变量 */
  Map<String, Object> queueArguments();

  /** 具体处理消息 */
  void onMessage(Message message);
}
