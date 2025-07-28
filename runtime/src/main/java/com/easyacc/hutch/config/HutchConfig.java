package com.easyacc.hutch.config;

import com.easyacc.hutch.core.ErrorHandler;
import com.easyacc.hutch.error_handlers.MaxRetry;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/** 提供配置 Hutch 的配置. 需要是 StaticInitSafe 状态, 能够在 static 的时候就开始处理 */
@StaticInitSafe
@ConfigMapping(prefix = "quarkus.hutch")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface HutchConfig {
  List<ErrorHandler> ERROR_HANDLERS = null;

  /** 是否默认启动 */
  @WithDefault("true")
  boolean enable();

  /** Hutch 的 prefix 前缀名称 */
  @WithDefault("hutch")
  String name();

  /** rabbitmq 的 virtualHost */
  @WithDefault("/")
  String virtualHost();

  /** 用户名 */
  @WithDefault("guest")
  String username();

  /** 密码 */
  @WithDefault("guest")
  String password();

  /** 连接服务器 */
  @WithDefault("127.0.0.1")
  String hostname();

  /** 是否使用 rabbitmq 的 quorum queue */
  @WithDefault("false")
  boolean quorum();

  /** 连接端口 */
  @WithDefault("5672")
  int port();

  /** 用于 schedule 的 Redis URL */
  @WithDefault("redis://localhost:6379")
  String redisUrl();

  /** 默认的 scheduleExecutor 的 thread pool 数量. 如果只有 1 个, 那么 scheudle 的任务执行时间过长, 会阻塞 */
  @WithDefault("6")
  int schdulePoolSize();

  /** 具体一个 Consumer 执行任务的时候的 ThreadPool 的大小. 如果 <=0, 则表示不限制线程数量 */
  @WithDefault("40")
  int workerPoolSize();

  /**
   * 构建 Hutch 需要的 SharedExecutorService
   *
   * @param size 并发的数量. 如果不设限制, 那么则 < 0, 否则 100 表示 100 并发线程数
   * @return
   */
  static ThreadPoolExecutor buildSharedExecutor(int size) {
    // 这里使用 newCachedThreadPool 与 使用 VirtualThread 很类似了, 可以无止境的根据需要创建新的 Thread
    if (size <= 0) {
      return (ThreadPoolExecutor) Executors.newCachedThreadPool();
    } else {
      return (ThreadPoolExecutor) Executors.newFixedThreadPool(size);
    }
  }

  /**
   * 构建 Schedule 定时器需要的 ScheduledExecutorService
   *
   * @param size
   * @return
   */
  static ScheduledExecutorService buildSharedScheduledExecutor(int size) {
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
  static List<ErrorHandler> getErrorHandlers() {
    return Carrier.getErrorHandlers();
  }

  /** 获取 RabbitMQ 的 uri, 暂时不支持 tls */
  default String getUri() {
    var vh = this.virtualHost();
    if (this.virtualHost().equals("/")) {
      vh = "";
    }
    return String.format("amqp://%s:*@%s:%d/%s", this.username(), this.hostname(), this.port(), vh);
  }

  /** 由于 interface 不支持非 final 的属性, 这里单独为 errorHandlers 准备一个容器 */
  class Carrier {
    private static List<ErrorHandler> errorHandlers;

    /** 获取默认的 ErrorHandlers */
    public static List<ErrorHandler> getErrorHandlers() {
      if (errorHandlers == null) {
        errorHandlers = new ArrayList<>();
        errorHandlers.add(new MaxRetry());
      }
      return errorHandlers;
    }
  }
}
