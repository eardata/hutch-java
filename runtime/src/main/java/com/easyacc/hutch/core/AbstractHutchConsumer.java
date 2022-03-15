package com.easyacc.hutch.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/2/25 Time: 14:19 */
@Slf4j
public class AbstractHutchConsumer implements HutchConsumer {
  @Getter private static final Map<String, HutchConsumer> Options = new HashMap<>();

  private final Map<String, Object> args;

  /**
   * TODO: 可以考虑迁移到 Quarkus extension, 可以在 compile 阶段:<br>
   * [x] 1. 自动扫描 class 级别的 @HutchConsumer 注解, 自动将 Bean 放到容器中<br>
   * [ ] 2. 寻找 @HutchConsumer 方法级别注解, 自动生成 Class, 并将 class 放到容器中, 相关参数都可以放到注解上. 然后交给 Hutch 最终来初始化
   * <br>
   * [ ] 3. 设置好 Hutch 实例的 APP_NAME<br>
   * [ ] 4. 初始化好 Hutch, 让其自动连接 mq. (类似 {@link io.quarkus.scheduler.runtime.SimpleScheduler})<br>
   * [ ] 5. 将 Hutch 的配置直接集成到 quarkus 的配置中, 或者考虑将 RabbitMQ Client 由自己的插件解决<br>
   */
  public AbstractHutchConsumer() {
    var queue = this.queue();
    getOptions().put(queue, this);
    this.args = new HashMap<>();
    log.debug("Register HutchConsumer for queue {}", queue);
  }

  public static Set<String> queues() {
    return getOptions().keySet();
  }

  @Override
  public Map<String, Object> queueArguments() {
    return this.args;
  }

  @Override
  public void onMessage(Message message) {
    throw new UnsupportedOperationException();
  }

  // arguments
  public void setClassicQueue() {
    this.args.put("x-queue-type", "classic");
  }

  /** 分布式的队列, 给到任务队列 ok, 但无法给到 Schedule 队列, 因为不支持 message ttl, 无法实现 rabbitmq 侧的自动 dlx */
  public void setQuorumQueue() {
    this.args.put("x-queue-type", "quorum");
  }

  public void setMaxLength(int maxLength) {
    this.args.put("x-max-length", maxLength);
  }

  public void setMaxLengthBytes(long maxLengthBytes) {
    this.args.put("x-max-length-bytes", maxLengthBytes);
  }
}
