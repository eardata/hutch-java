package com.easyacc.hutch.publisher;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.core.HutchConsumer;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 将 Hutch 需要对三方 api 进行 ratelimite 的操作独立出来, 不与标准的 Hutch.publish 共用.
 *
 * <pre>
 *   核心的设计思路为:
 *   1. 用户通过统一的 HutchLimiter 的入口对需要进行 ratelimit 的任务进行提交
 *   2. 提交的任务会进入 redis 中进行 buffer 与存储, 等待额外的 driver job 根据设定的 limit 去获取并发送任务到 mq
 *   3. 为每一个使用了 Threshold 的 Consumer 注册一个新的 Driver Job, 根据设置的频率与任务量进行获取与调度
 * </pre>
 */
public interface LimitPublisher {

  /** 利用 HutchConsumer 将 Object 转为 json 发送消息 */
  static void publish(Class<? extends HutchConsumer> consumer, Object msg) {
    // TODO
  }

  /**
   * 使用 msg 计算出 key 作为 redis key 的 suffix
   *
   * @param hc {@link HutchConsumer}
   * @param msg HutchConsumer 的消息的 String 格式
   * @return 作为 redis zset 的 key
   */
  static String zsetKey(HutchConsumer hc, String msg) {
    Objects.requireNonNull(hc, "HutchConsumer 实例不能为空");
    Objects.requireNonNull(hc.threshold(), "HutchConsumer 的 threshold 参数不能为空");

    return Stream.of(hc.queue(), Hutch.threshold(hc).key(msg))
        .filter(Objects::nonNull)
        .filter(Predicate.not(String::isBlank))
        .collect(Collectors.joining("."));
  }
}
