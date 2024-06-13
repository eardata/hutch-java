package com.easyacc.hutch.config;

import com.easyacc.hutch.core.ErrorHandler;
import com.easyacc.hutch.error_handlers.MaxRetry;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.annotations.StaticInitSafe;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import lombok.ToString;

/** 提供配置 Hutch 的配置. 需要是 StaticInitSafe 状态, 能够在 static 的时候就开始处理 */
@StaticInitSafe
@ConfigRoot(name = "hutch", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@ToString
public class HutchConfig {
  private static List<ErrorHandler> errorHandlers;

  /** 是否默认启动 */
  @ConfigItem(defaultValue = "true")
  public boolean enable;

  /** Hutch 的 prefix 前缀名称 */
  @ConfigItem(defaultValue = "hutch")
  public String name;

  /** rabbitmq 的 virtualHost */
  @ConfigItem(defaultValue = "/")
  public String virtualHost;

  /** 用户名 */
  @ConfigItem(defaultValue = "guest")
  public String username;

  /** 密码 */
  @ConfigItem(defaultValue = "guest")
  public String password;

  /** 连接服务器 */
  @ConfigItem(defaultValue = "127.0.0.1")
  public String hostname;

  /** 是否使用 rabbitmq 的 quorum queue */
  @ConfigItem(defaultValue = "false")
  public boolean quorum;

  /** 连接端口 */
  @ConfigItem(defaultValue = "5672")
  public int port;

  /** 用于 schedule 的 Redis URL */
  @ConfigItem(defaultValue = "redis://localhost:6379")
  public String redisUrl;

  /** 默认的 scheduleExecutor 的 thread pool 数量. 如果只有 1 个, 那么 scheudle 的任务执行时间过长, 会阻塞 */
  @ConfigItem(defaultValue = "6")
  public int schdulePoolSize;

  /** 具体一个 Consumer 执行任务的时候的 ThreadPool 的大小. 如果 <=0, 则表示不限制线程数量 */
  @ConfigItem(defaultValue = "40")
  public int workerPoolSize;

  /**
   * 构建 Hutch 需要的 SharedExecutorService
   *
   * @param size 并发的数量. 如果不设限制, 那么则 < 0, 否则 100 表示 100 并发线程数
   * @return
   */
  public static ExecutorService buildSharedExecutor(int size) {
    // 这里使用 newCachedThreadPool 与 使用 VirtualThread 很类似了, 可以无止境的根据需要创建新的 Thread
    if (size <= 0) {
      return Executors.newCachedThreadPool();
    } else {
      return Executors.newFixedThreadPool(size);
    }
  }

  /**
   * 构建 Schedule 定时器需要的 ScheduledExecutorService
   *
   * @param size
   * @return
   */
  public static ScheduledExecutorService buildSharedScheduledExecutor(int size) {
    return Executors.newScheduledThreadPool(size);
  }

  /**
   * 获取默认的 ErrorHandlers, 如果没有初始化, 默认添加 MaxRetry. 如果需要额外自定义 ErrorHandler, 那么
   *
   * <ul>
   *   <li>实现 ErrorHandler 接口
   *   <li>在 Quarkus 应用初始化的时候, 通过 HutchConfig.getErrorHandlers 获取实例, 将对应的 ErrorHandler 实例添加到有序队列中
   * </ul>
   */
  public static List<ErrorHandler> getErrorHandlers() {
    if (errorHandlers == null) {
      errorHandlers = new ArrayList<>();
      errorHandlers.add(new MaxRetry());
    }
    return errorHandlers;
  }

  /** 获取 RabbitMQ 的 uri, 暂时不支持 tls */
  public String getUri() {
    var vh = virtualHost;
    if (virtualHost.equals("/")) {
      vh = "";
    }
    return String.format("amqp://%s:*@%s:%d/%s", username, hostname, port, vh);
  }
}
