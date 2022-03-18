package com.easyacc.hutch;

import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.util.HutchUtils;
import com.easyacc.hutch.util.HutchUtils.Gradient;
import com.easyacc.hutch.util.RabbitUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.enterprise.inject.spi.CDI;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 直接利用 RabbitMQ Java Client 的 Buffer 和 Thread Pool 来解决 MQ 的队列问题.<br>
 * 1. 拥有自定义的 MessageListener, 负责自定义的消息响应<br>
 * 2. 映射 Queue 与 MessageListener 之间的对应关系<br>
 * 3. 通过多个 QueueConsumer 来控制并发<br>
 *
 * <pre>
 *   delay:
 *   # fixed delay levels
 *   # seconds(4): 5s, 10s, 20s, 30s
 *   # minutes(14): 1m, 2m, 3m, 4m, 5m, 6m, 7m, 8m, 9m, 10m, 20m, 30m, 40m, 50m
 *   # hours(3): 1h, 2h, 3h
 *   DELAY_QUEUES = %w(5s 10s 20s 30s 60s 120s 180s 240s 300s 360s 420s 480s 540s 600s 1200s 1800s 2400s 3000s 3600s 7200s 10800s)
 * </pre>
 */
@Slf4j
public class Hutch implements IHutch {
  public static final String HUTCH_EXCHANGE = "hutch";
  public static final String HUTCH_SCHEDULE_EXCHANGE = "hutch.schedule";

  /** 用于 queue 前缀的应用名, 因为 Quarkus 的 CDI 的机制, 现在需要在 HutchConsumer 初始化之前就设置好, 例如 static {} 代码块中 */
  public static String APP_NAME = "hutch";
  /** 用于方便进行 static 方法进行调用 */
  private static volatile Hutch currentHutch;

  @Setter private static ObjectMapper objectMapper;

  private final Map<String, List<SimpleConsumer>> hutchConsumers;

  private final HutchConfig config;

  /** Hutch 默认的 Channel, 主要用于消息发送 */
  @Getter private Channel ch;

  private Connection conn;

  @Getter private boolean isStarted = false;

  public Hutch(HutchConfig config) {
    this.config = config;
    this.hutchConsumers = new HashMap<>();
  }

  public static String name() {
    return APP_NAME;
  }

  /** 使用 fixedDelay (ms) 的 routing_key. ex: hutch.exchange.5s */
  public static String delayRoutingKey(long fixedDelay) {
    return String.format(
        "%s.%ss",
        HUTCH_SCHEDULE_EXCHANGE, TimeUnit.SECONDS.convert(fixedDelay, TimeUnit.MILLISECONDS));
  }

  /** 发送字符串 - HutchConsumer */
  public static void publish(Class<? extends HutchConsumer> consumer, String msg) {
    publish(HutchConsumer.rk(consumer), msg);
  }

  /** 发送字符串 */
  public static void publish(String routingKey, String msg) {
    var props = new BasicProperties().builder().contentType("text/plain").contentEncoding("UTF-8");
    publish(routingKey, props.build(), msg.getBytes());
  }

  /** 最原始的发送 bytes - HutchConsumer */
  public static void publish(
      Class<? extends HutchConsumer> consumer, BasicProperties props, byte[] body) {
    publish(HutchConsumer.rk(consumer), props, body);
  }

  /** 最原始的发送 bytes */
  public static void publish(String routingKey, BasicProperties props, byte[] body) {
    publish(HUTCH_EXCHANGE, routingKey, props, body);
  }

  public static void publish(
      String exchange, String routingKey, BasicProperties props, byte[] body) {
    if (currentHutch == null) {
      throw new IllegalStateException("Hutch is not started");
    }
    try {
      currentHutch.ch.basicPublish(exchange, routingKey, props, body);
    } catch (IOException e) {
      if (HUTCH_SCHEDULE_EXCHANGE.equals(exchange)) {
        log.error("publish with delay error", e);
      } else {
        log.error("publish error", e);
      }
    }
  }

  /** 直接当做 JSON 发送 - HutchConsumer */
  public static void publishJson(Class<? extends HutchConsumer> consumer, Object msg) {
    publishJson(HutchConsumer.rk(consumer), msg);
  }
  /** 直接当做 JSON 发送 */
  public static void publishJson(String routingKey, Object msg) {
    var props =
        new BasicProperties().builder().contentType("application/json").contentEncoding("UTF-8");
    byte[] body = new byte[0];
    try {
      body = om().writeValueAsBytes(msg);
    } catch (JsonProcessingException e) {
      log.error("publishJson error", e);
    }
    publish(routingKey, props.build(), body);
  }

  /** 向 routing-key 对应的队列发送一个延迟消息 - HutchConsumer */
  public static void publishJsonWithDelay(
      long delayInMs, Class<? extends HutchConsumer> consumer, Object msg) {
    publishJsonWithDelay(delayInMs, HutchConsumer.rk(consumer), msg);
  }

  /**
   * 向 routing-key 对应的队列发送一个延迟消息
   *
   * @param delayInMs 梯度延迟的时间, 单位 ms
   * @param routingKey 消息的 routing-key
   * @param msg 具体的 json 格式的消息体
   */
  public static void publishJsonWithDelay(long delayInMs, String routingKey, Object msg) {
    var props =
        new BasicProperties()
            .builder()
            .contentType("application/json")
            .headers(Collections.singletonMap("CC", List.of(routingKey)))
            .contentEncoding("UTF-8");
    byte[] body;
    try {
      body = om().writeValueAsBytes(msg);
      internalPublishWithDelay(delayInMs, props.build(), body);
    } catch (JsonProcessingException e) {
      log.error("publishJson error", e);
    }
  }

  /** 向延迟队列中发布消息 */
  static void internalPublishWithDelay(long delayInMs, BasicProperties props, byte[] body) {
    var fixDelay = HutchUtils.fixDealyTime(delayInMs);
    publish(Hutch.HUTCH_SCHEDULE_EXCHANGE, Hutch.delayRoutingKey(fixDelay), props, body);
  }

  /** Hutch 自己使用的 ObjectMapper, 也可以通过 setter 进行定制 */
  public static ObjectMapper om() {
    if (Hutch.objectMapper == null) {
      var objectMapper = new ObjectMapper();
      objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      Hutch.objectMapper = objectMapper;
    }
    return Hutch.objectMapper;
  }

  public static Set<HutchConsumer> consumers() {
    var beans = CDI.current().getBeanManager().getBeans(HutchConsumer.class);
    var queues = new HashSet<HutchConsumer>();
    for (var bean : beans) {
      var hco = HutchUtils.findHutchConsumerBean(bean.getBeanClass());
      if (hco.isEmpty()) {
        continue;
      }
      queues.add(hco.get());
    }
    return queues;
  }

  public static Set<String> queues() {
    return consumers().stream().map(HutchConsumer::queue).collect(Collectors.toSet());
  }

  @Override
  public Hutch start() {
    if (this.isStarted) {
      return this;
    }
    try {
      connect();
      declearExchanges();
      declearScheduleQueues();
      declearhutchConsumerQueues();
    } finally {
      this.isStarted = true;
    }
    return this;
  }

  /** 初始化 Hutch 自己使用的默认操作进行连接 */
  @SneakyThrows
  public void connect() {
    if (currentHutch == null) {
      currentHutch = this;
    }
    log.info("Hutch({}) connect to RabbitMQ: {}", Hutch.name(), config.getUri());
    this.conn = this.newConnect("hutch");
    this.ch = conn.createChannel();
  }

  protected void declearExchanges() {
    try {
      this.ch.exchangeDeclare(HUTCH_EXCHANGE, "topic", true);
      this.ch.exchangeDeclare(HUTCH_SCHEDULE_EXCHANGE, "topic", true);
    } catch (Exception e) {
      // ignore
      log.error("Declare exchange error", e);
    }
  }

  protected void declearScheduleQueues() {
    // 初始化 delay queue 相关的信息
    var delayQueueArgs = new HashMap<String, Object>();
    // TODO: 可以考虑 x-message-ttl 为每个队列自己的超时时间, 这里设置成 30 天没有太大意义. (需要与 hutch-schedule 进行迁移)
    delayQueueArgs.put("x-message-ttl", TimeUnit.DAYS.toMillis(30));
    delayQueueArgs.put("x-dead-letter-exchange", HUTCH_EXCHANGE);
    for (var g : Gradient.values()) {
      try {
        this.ch.queueDeclare(g.queue(), true, false, false, delayQueueArgs);
        this.ch.queueBind(g.queue(), HUTCH_SCHEDULE_EXCHANGE, Hutch.delayRoutingKey(g.fixdDelay()));
      } catch (Exception e) {
        log.error("Declare delay queue {} error", g.queue(), e);
      }
    }
  }

  protected void declearhutchConsumerQueues() {
    var queues = Hutch.queues();
    log.info(
        "Start Hutch({} executor) with queues({}): {}",
        HutchConfig.getSharedExecutor().getClass().getSimpleName(),
        queues.size(),
        queues);
    for (var hc : Hutch.consumers()) {
      declearHutchConsumQueue(hc);
      initHutchConsumer(hc);
      log.debug("Connect to {}", hc.queue());
    }
  }

  protected void declearHutchConsumQueue(HutchConsumer hc) {
    try {
      this.ch.queueDeclare(hc.queue(), true, false, false, hc.queueArguments());
      this.ch.queueBind(hc.queue(), HUTCH_EXCHANGE, hc.routingKey());
    } catch (Exception e) {
      log.error("Declare queues error", e);
    }
  }

  protected void initHutchConsumer(HutchConsumer hc) {
    // Ref: https://github.com/rabbitmq/rabbitmq-perf-test/issues/93
    // 可以考虑每为每一个 Queue 设置一个连接的 Connection, 因为一个 Connection 对应一个 TCP 连接, 而有的时候
    // 一个 TCP 连接的吞吐量是有限的, 所以可以建立多个连接.
    var conn = this.newConnect(hc.queue() + "_conn");
    var scl = new LinkedList<SimpleConsumer>();
    for (var i = 0; i < hc.concurrency(); i++) {
      scl.add(consumeHutchConsumer(conn, hc));
    }
    this.hutchConsumers.put(hc.queue(), scl);
  }

  @Override
  public void stop() {
    log.info("Stop Hutch");
    try {
      if (this.isStarted) {
        for (var q : this.hutchConsumers.keySet()) {
          this.hutchConsumers.get(q).forEach(SimpleConsumer::cancel);
        }
      }
    } finally {
      RabbitUtils.closeChannel(this.ch);
      RabbitUtils.closeConnection(this.conn);
      this.isStarted = false;
    }
  }

  private Connection newConnect(String name) {
    return RabbitUtils.connect(this.config, name);
  }

  /** 根据 Conn 在 RabbitMQ 上订阅一个队列进行消费 */
  public SimpleConsumer consumeHutchConsumer(Connection conn, HutchConsumer hc) {
    var autoAck = false;
    Channel ch = null;
    SimpleConsumer consumer = null;
    try {
      // 并发处理, 每一个 Consumer 为一个并发
      ch = conn.createChannel();
      consumer = new SimpleConsumer(ch, hc.queue(), hc);
      ch.basicQos(hc.prefetch());
      ch.basicConsume(hc.queue(), autoAck, consumer);
      ch.queueDeclarePassive(hc.queue());
    } catch (Exception e) {
      RabbitUtils.closeChannel(ch);
      RabbitUtils.closeConnection(conn);
    }
    return consumer;
  }
}
