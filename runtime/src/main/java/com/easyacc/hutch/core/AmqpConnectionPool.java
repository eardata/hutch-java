package com.easyacc.hutch.core;

import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.util.RabbitUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** 对 Amqp 的 Connection Pool, 避免在初始化 Consumer 的时候, 一个 Connection 太多的 Channel */
@Slf4j
public class AmqpConnectionPool {

  /** 控制每个 Connection 允许的最多的 channel */
  public static final int MAX_CHANNEL_PER_CONNECTION = 256;

  private Set<HutchConsumer> consumers;
  private HutchConfig config;
  @Getter private int maxConcurrencyNumber;
  /** 一个 Connection 的使用数量 */
  private Map<Connection, AtomicInteger> connections = new HashMap<>();
  /** 用于记录 Consumer 使用的 Connection */
  private Map<String, Connection> consumerConnectionMap = new HashMap<>();

  private Connection defaultConn;

  public AmqpConnectionPool(Set<HutchConsumer> consumers, HutchConfig config, String mode) {
    this.consumers = consumers;
    this.config = config;
    this.maxConcurrencyNumber = consumers.stream().mapToInt(HutchConsumer::concurrency).sum();
    int nums = (this.maxConcurrencyNumber / (MAX_CHANNEL_PER_CONNECTION) + 1);

    for (var i = 0; i < nums; i++) {
      var conn = RabbitUtils.connect(config, String.format("hutch-consumers-%s-%s", mode, i));
      connections.put(conn, new AtomicInteger(0));
      // 初始化一个默认的 conn
      if (defaultConn == null) {
        defaultConn = conn;
      }
    }
    log.info(
        "AmqpConnectionPool 计算有 {} 个 Channel, 所以初始化 {} 个 AmqpConection",
        this.maxConcurrencyNumber,
        nums);
  }

  public Collection<Connection> getConnections() {
    return connections.keySet();
  }

  private Connection pickOne() {
    var op =
        this.connections.entrySet().stream()
            .min(Comparator.comparingInt(value -> value.getValue().get()));
    return op.map(Entry::getKey).orElse(this.defaultConn);
  }

  /** 根据 Connectin 的情况返回 Channel */
  public Channel createChannel(HutchConsumer consumer) throws IOException {
    Connection conn = null;
    try {
      if (consumerConnectionMap.containsKey(consumer.name())) {
        conn = consumerConnectionMap.get(consumer.name());
      } else {
        conn = pickOne();
        consumerConnectionMap.put(consumer.name(), conn);
      }
      return conn.createChannel();
    } finally {
      if (conn != null) {
        var num = connections.get(conn).incrementAndGet();
        log.debug("{} connection have {} channels", conn.getClientProvidedName(), num);
      }
    }
  }
}
