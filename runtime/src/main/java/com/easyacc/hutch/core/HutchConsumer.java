package com.easyacc.hutch.core;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.util.HutchUtils;
import io.quarkus.arc.Unremovable;
import java.util.Map;

/**
 * TODO: 可以考虑迁移到 Quarkus extension, 可以在 compile 阶段:<br>
 * [x] 1. 自动扫描 class 级别的 @HutchConsumer 注解, 自动将 Bean 放到容器中<br>
 * [ ] 2. 寻找 @HutchConsumer 方法级别注解, 自动生成 Class, 并将 class 放到容器中, 相关参数都可以放到注解上, 并且方法中传递的类自动反序列化(json).
 * 然后交给 Hutch 最终来初始化 <br>
 * [x] 3. 设置好 Hutch 实例的 APP_NAME<br>
 * [x] 4. 将 Hutch 的配置直接集成到 quarkus 的配置中.<br>
 * 插件<br>
 * [ ] 5. 初始化好 Hutch, 让其自动连接 mq. (类似 {@link io.quarkus.scheduler.runtime.SimpleScheduler})<br>
 * [ ] 6. 考虑将 RabbitMQ Client 由自己的插件解决, 而不需要 rabbitmq-client<br>
 * [x] 7. 取消掉 AbstractHutchConsumer 类<br>
 * <br>
 * 一个 HutchConsumer, 只消耗一个队列.
 *
 * @see <a href="https://codertw.com/%E7%A8%8B%E5%BC%8F%E8%AA%9E%E8%A8%80/431837/">Java 8 default
 *     method</a>
 */
@Unremovable
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

  /** 初始化 Queue 需要的变量, 有其他值自己覆写 */
  default Map<String, Object> queueArguments() {
    return Map.of();
  }

  /** 具体处理消息 */
  void onMessage(Message message);
}
