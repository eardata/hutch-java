package com.easyacc.hutch.util;

import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.core.AmqpConnectionPool;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/2/27 Time: 11:11 */
@Slf4j
public class RabbitUtils {

  /**
   * 默认的 ConnectionFactory 为 RabbitClient 的 ConnectionFactory, 如果需要快速测试, 可以直接通过 static 代码块改变这个为
   * MockConnectionFactory, 避免连接 RabbitMQ instance. 但需要注意 MockConnectionFactory 的一些测试情况:
   *
   * <ul>
   *   <li>其通过内置的 MockNode 在内存中缓存数据
   *   <li>每一次 newConnection 都会创建一个全新的 MockNode, 这样不会像真实 mq 一样有服务器端同一份数据缓存
   *   <li>其内部没有使用 DeadLetter 的机制, 所以这些特性无法实现.
   * </ul>
   *
   * 使用 MockConnectionFactory 的建议场景: 仅仅用于测试简单的 Queue 等的 Declare 与同 Connection 的 consumer
   */
  public static Supplier<ConnectionFactory> buildConnectionFactory = ConnectionFactory::new;

  /**
   * 需要的是 Java SDK 的 RabbitMQ Client, 暂时不能使用 smallrye-reactive-messaging 所支持的 RabbitMQ 的 Connection,
   * 因为 sm-ra-ms 走的是 Reactive 的异步响应模式, 相比与 Java SDK 提供的同步线程模式有很大的区别, 其提供的 API 也非常不一样, 在弄明白如如何与现有
   * Thread Pool 方式进行整合之前, 不太合适借用 sm-ra-ms 的 SDK
   */
  public static Connection connect(HutchConfig config) {
    return connect(config, UUID.randomUUID().toString());
  }

  @SneakyThrows
  public static Connection connect(HutchConfig config, String name) {
    var cf = buildConnectionFactory.get();
    cf.setHost(config.hostname);
    cf.setPort(config.port);
    cf.setUsername(config.username);
    cf.setPassword(config.password);
    cf.setVirtualHost(config.virtualHost);
    return cf.newConnection(HutchConfig.getSharedExecutor(), name);
  }

  public static void closeChannel(Channel ch) {
    if (ch != null) {
      try {
        log.debug(
            "close channel: {}/{}",
            ch.getConnection().getClientProvidedName(),
            ch.getChannelNumber());
        ch.close();
      } catch (AlreadyClosedException ace) {
        // empty
      } catch (IOException ex) {
        log.debug("Could not close RabbitMQ Channel", ex);
      } catch (Exception ex) {
        log.debug("Unexpected exception on closing RabbitMQ Channel", ex);
      }
    }
  }

  public static void closeConnection(AmqpConnectionPool pool) {
    if (pool != null) {
      for (var conn : pool.getConnections()) {
        RabbitUtils.closeConnection(conn);
      }
    }
  }

  public static void closeConnection(Connection conn) {
    if (conn != null) {
      try {
        log.debug("close connection: {}", conn.getClientProvidedName());
        conn.close();
      } catch (AlreadyClosedException ace) {
        // empty
      } catch (Exception ex) {
        log.debug(
            "Ignoring Connection exception - assuming already closed: " + ex.getMessage(), ex);
      }
    }
  }

  public static void cancelConsumer(Channel ch, String consumerTag) {
    if (ch != null) {
      try {
        ch.basicCancel(consumerTag);
      } catch (Exception ex) {
        log.debug("Unexpected exception on cancelling RabbitMQ consumer", ex);
      }
    }
  }
}
