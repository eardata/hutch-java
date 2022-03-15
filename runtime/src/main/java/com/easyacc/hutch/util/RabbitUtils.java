package com.easyacc.hutch.util;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/2/27 Time: 11:11 */
@Slf4j
public class RabbitUtils {

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
