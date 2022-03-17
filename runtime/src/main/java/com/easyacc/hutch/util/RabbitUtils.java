package com.easyacc.hutch.util;

import com.easyacc.hutch.config.HutchConfig;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.UUID;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/2/27 Time: 11:11 */
@Slf4j
public class RabbitUtils {
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
    var cf = new ConnectionFactory();
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

  public static void closeConnection(Connection conn) {
    if (conn != null) {
      try {
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
