package com.easyacc.hutch.core;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.util.HutchUtils;
import io.quarkus.arc.Unremovable;
import java.util.Map;

/**
 * 新一个阶段的实现任务:<br>
 * [ ] 1. 寻找 @HutchListener 方法级别注解, 自动生成 Class, 并将 class 放到容器中, 相关参数都可以放到注解上, 并且方法中传递的类自动反序列化(json).
 * 然后交给 Hutch 最终来初始化 <br>
 * [ ] 2. 初始化好 Hutch, 让其自动连接 mq. (类似 {@link io.quarkus.scheduler.runtime.SimpleScheduler})<br>
 * [ ] 3. 增加 RabbitMQ java Sdk 中的 MetricsCollector, 方便用于统计执行数据以及测试<br>
 * [ ] 4. 考虑增加进程级别限速功能, 每分钟, 每秒钟消耗多少任务. (应用场景: 后台任务提交, 但对外的访问拥有 api limit 不能无止境重试)<br>
 * [ ] 5. 考虑增加全局级别限速功能, 每分钟, 每秒钟消耗多少任务. (应用场景: 上面场景到 k8s 中多个实例)<br>
 * <br>
 * 一个 HutchConsumer, 只消耗一个队列.
 *
 * @see <a href="https://codertw.com/%E7%A8%8B%E5%BC%8F%E8%AA%9E%E8%A8%80/431837/">Java 8 default
 *     method</a>
 */
@Unremovable
public interface HutchConsumer {

  /** 静态方法提供 routing key 计算支持 */
  static String rk(Class<? extends HutchConsumer> clazz) {
    return HutchUtils.prefixQueue(queueName(clazz));
  }

  private static String queueName(Class<?> clazz) {
    return HutchUtils.upCamelToLowerUnderscore(clazz.getSimpleName())
        // 清理, 只留下需要的名字, 去除后缀
        .replace("__subclass", "");
  }

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
    return HutchUtils.prefixQueue(queueName(getClass()));
  }

  /** 最大重试; default: 1 */
  default int maxRetry() {
    return 1;
  }

  /** 当前消息使用的 routing key, 虽然可以使用多个, 但这个先只处理一个的情况. 默认情况下, routing key 与 queue 同名 */
  default String routingKey() {
    return this.queue();
  }

  /** 初始化 Queue 需要的变量, 有其他值自己覆写 */
  default Map<String, Object> queueArguments() {
    return Map.of();
  }

  /** 根据当前的 routing key, 发送一个消息 */
  default <T> void enqueue(T t) {
    Hutch.publishJson(this.routingKey(), t);
  }

  /** 根据当前 routing key 以及设置的延迟, 计算固定梯度延迟, 发送一个消息 */
  default <T> void enqueueIn(long delayInMs, T t) {
    Hutch.publishJsonWithDelay(delayInMs, this.routingKey(), t);
  }

  /** 具体处理消息 */
  void onMessage(Message message);
}
