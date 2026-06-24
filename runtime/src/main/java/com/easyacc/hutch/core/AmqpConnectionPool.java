package com.easyacc.hutch.core;

import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.util.RabbitUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
  private final Map<Connection, AtomicInteger> connections = new LinkedHashMap<>();

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

  public synchronized Collection<Connection> getConnections() {
    return List.copyOf(connections.keySet());
  }

  private Connection pickOne() throws IOException {
    var op =
        this.connections.entrySet().stream()
            .filter(value -> value.getValue().get() < MAX_CHANNEL_PER_CONNECTION)
            .min(Comparator.comparingInt(value -> value.getValue().get()));
    if (op.isEmpty()) {
      throw new IOException(
          String.format(
              "No available RabbitMQ connection, every connection already has %s channels",
              MAX_CHANNEL_PER_CONNECTION));
    }
    return op.map(Entry::getKey).orElse(this.defaultConn);
  }

  /** 根据 Connection 的情况返回 Channel */
  public synchronized Channel createChannel(HutchConsumer consumer) throws IOException {
    var conn = pickOne();
    var ch = conn.createChannel();
    var num = connections.get(conn).incrementAndGet();
    log.debug(
        "{} connection have {} channels, consumer: {}",
        conn.getClientProvidedName(),
        num,
        consumer.name());
    return ch;
  }
}
