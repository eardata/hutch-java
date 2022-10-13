package com.easyacc.hutch.core;

import com.easyacc.hutch.Hutch;
import java.util.List;
import java.util.function.Consumer;
import lombok.SneakyThrows;

/**
 * 利用 redis 做主动式的 ratelimit. 一般被动式的 ratelimit 为任务到达准备执行的时候判断是否需要执行, 而主动式 ratelimit
 * 将判断点提前到系统主动获取需要执行的任务的时间点, 这样可以避免任务与 rabbitmq 之间的频繁依赖. 所以
 */
public interface Threshold {
  /** 将 redis 中的 msg 通过 jackson 读取成为 clazz 的实例 */
  @SneakyThrows
  @Deprecated(since = "Threshold 接口不应该负责 toType 的问题, 应该交给其他类来解决这个问题")
  default <T> T toType(Class<T> clazz, String msg) {
    return Hutch.om().readerFor(clazz).readValue(msg);
  }

  /** 每次执行加载出来的数量 */
  default int rate() {
    return 1;
  }

  /** 每次执行的间隔时间(单位: s, 最小 1s) */
  default int interval() {
    return 1;
  }

  /**
   * 通过 msg 来计算 redis 队列的 suffix. 提供一个根据业务 key 区分不同部分数据但相同 threshold 的能力. 例如拥有多组 api key 同步信息, 每组
   * api key 的 ratelimit 是一样的, 但业务逻辑一样, 需要区分开.
   *
   * <pre>
   *   1. 如果整个队列队列中所有任务保持一个速度, 不需要复写
   *   2. 如果有上述场景, 自定义此 key
   * </pre>
   *
   * @param msg 具体一个 HutchConsumer 的 message body
   */
  default String key(String msg) {
    return "";
  }

  /**
   * 自定义需要进行 batch 消息发送
   *
   * @return 如果为 null(默认), 则不需要使用默认的, 如果有存在值则使用当前方法进行 batch publish
   */
  default Consumer<List<String>> batch() {
    return null;
  }
}
