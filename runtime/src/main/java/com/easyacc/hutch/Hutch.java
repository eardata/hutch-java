package com.easyacc.hutch;

import com.easyacc.hutch.config.HutchConfig;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.MessageProperties;
import com.easyacc.hutch.core.Threshold;
import com.easyacc.hutch.publisher.LimitPublisher;
import com.easyacc.hutch.scheduler.HyenaJob;
import com.easyacc.hutch.support.DefaultMessagePropertiesConverter;
import com.easyacc.hutch.support.MessagePropertiesConverter;
import com.easyacc.hutch.util.HutchUtils;
import com.easyacc.hutch.util.HutchUtils.Gradient;
import com.easyacc.hutch.util.RabbitUtils;
import com.easyacc.hutch.util.RedisUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.google.common.base.Strings;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.quarkus.runtime.LaunchMode;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import javax.enterprise.inject.spi.CDI;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * 直接利用 RabbitMQ Java Client 的 Buffer 和 Thread Pool 来解决 MQ 的队列问题.<br>
 * 0. 整个应用中, 只有一个 Hutch 实例, 其他的 HutchConsumer 都会被扫描到并被注入到进程中这一个 Hutch 实例中进行管理<br>
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
  private static final MessagePropertiesConverter MPC = new DefaultMessagePropertiesConverter();
  private static final Set<HutchConsumer> consumers = new HashSet<>();

  private static final Map<HutchConsumer, Threshold> cachedThresholds = new HashMap<>();
  private static final ReentrantLock lock = new ReentrantLock();

  /** 用于 queue 前缀的应用名, 因为 Quarkus 的 CDI 的机制, 现在需要在 HutchConsumer 初始化之前就设置好, 例如 static {} 代码块中 */
  public static String APP_NAME = "hutch";
  /** 用于方便进行 static 方法进行调用 */
  private static volatile Hutch currentHutch;

  @Setter private static ObjectMapper objectMapper;

  private final Map<String, List<SimpleConsumer>> hutchConsumers;

  /** 定时任务的 ExecutorService */
  private ScheduledExecutorService scheduledExecutor;

  @Getter private final HutchConfig config;

  /** Hutch 默认的 Channel, 主要用于消息发送 */
  @Getter private Channel ch;

  private Connection conn;
  /** 将 consumer 的 connection 与其他的区分开 */
  private Connection connForConsumer;

  @Getter private StatefulRedisConnection<String, String> redisConnection;

  @Getter private boolean isStarted = false;

  public Hutch(HutchConfig config) {
    this.config = config;
    this.hutchConsumers = new HashMap<>();
  }

  public static String name() {
    return APP_NAME;
  }

  /** 返回当前的 Hutch 实例 */
  public static Hutch current() {
    return currentHutch;
  }

  public static RedisCommands<String, String> redis() {
    return current().redisConnection.sync();
  }

  public static Logger log() {
    return log;
  }

  /**
   * 使用 delayInMs (ms) 的 routing_key. ex: hutch.exchange.5s
   *
   * @param delayInMs 传入需要延迟的时间(ms), 自动计算到对应的 routing-key
   */
  public static String delayRoutingKey(long delayInMs) {
    return String.format(
        "%s.%ss",
        HUTCH_SCHEDULE_EXCHANGE,
        TimeUnit.SECONDS.convert(HutchUtils.fixDealyTime(delayInMs), TimeUnit.MILLISECONDS));
  }

  /** 进行 schedule publish */
  @Deprecated(since = "走 limit")
  public static void publishWithSchedule(Class<? extends HutchConsumer> consumer, String msg) {
    // 寻找到对应的 Consumer 实例
    var hc = HutchConsumer.get(consumer);
    // 使用 msg 计算出 key 作为 redis key 的 suffix
    var key = LimitPublisher.zsetKey(hc, msg);
    Hutch.redis()
        // 使用当前时间作为 score
        .zadd(key, Timestamp.valueOf(LocalDateTime.now()).getTime(), msg);
  }

  // ----------------- default publish ------------------
  /** 最原始的发送 bytes - HutchConsumer */
  public static void publish(
      Class<? extends HutchConsumer> consumer, BasicProperties props, byte[] body) {
    publish(HutchConsumer.rk(consumer), props, body);
  }

  /** 最原始的发送 bytes */
  public static void publish(String routingKey, BasicProperties props, byte[] body) {
    publish(Hutch.HUTCH_EXCHANGE, routingKey, props, body);
  }

  /** 向延迟队列中发布消息 */
  public static void publishWithDelay(long delayInMs, BasicProperties props, byte[] body) {
    publish(Hutch.HUTCH_SCHEDULE_EXCHANGE, Hutch.delayRoutingKey(delayInMs), props, body);
  }

  /**
   * 最核心的 publish 方法
   *
   * @param exchange 指定 exchange
   * @param routingKey 指定 routingKey
   * @param props 指定消息的 props
   * @param body 指定二进制的 body
   */
  public static void publish(
      String exchange, String routingKey, BasicProperties props, byte[] body) {
    if (current() == null) {
      throw new IllegalStateException("Hutch is not started");
    }
    if (!current().isStarted()) {
      log.warn("Hutch({}) is not started, publish message failed", current());
      return;
    }
    try {
      log.debug("publish message to {} with routingKey {}", exchange, routingKey);
      current().getCh().basicPublish(exchange, routingKey, props, body);
    } catch (IOException e) {
      if (Hutch.HUTCH_SCHEDULE_EXCHANGE.equals(exchange)) {
        Hutch.log().error("publish with delay error", e);
      } else {
        Hutch.log().error("publish error", e);
      }
    }
  }
  // ----------------

  /** 处理 Delay Message 需要处理的 header 信息等等, 保留原来消息中的 props header 等信息 */
  public static BasicProperties convertToDelayProps(
      String routingKey, MessageProperties props, long delay) {
    props.setExpiration(HutchUtils.fixDealyTime(delay) + "");
    props.setHeader("CC", List.of(routingKey));
    return getMessagePropertiesConverter().fromMessageProperties(props, "UTF-8");
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

  /** Hutch 所有的 HutchConsumer 实例 */
  public static Set<HutchConsumer> consumers() {
    if (Hutch.consumers.isEmpty()) {
      var beans = CDI.current().getBeanManager().getBeans(HutchConsumer.class);
      for (var bean : beans) {
        var hco = HutchUtils.findHutchConsumerBean(bean.getBeanClass());
        if (hco.isEmpty()) {
          continue;
        }
        Hutch.consumers.add(hco.get());
      }
    }
    return Hutch.consumers;
  }

  /**
   * 对 threshold 进行 HutchConsumer 级别的缓存, 避免每次生成新的匿名类
   *
   * @param hc
   * @return
   */
  public static Threshold threshold(HutchConsumer hc) {
    if (cachedThresholds.containsKey(hc)) {
      return cachedThresholds.get(hc);
    }
    var t = hc.threshold();
    // 如果为 null, 也 put 进去
    cachedThresholds.put(hc, t);
    return cachedThresholds.get(hc);
  }

  public static Set<String> queues() {
    return consumers().stream().map(HutchConsumer::queue).collect(Collectors.toSet());
  }

  public static MessagePropertiesConverter getMessagePropertiesConverter() {
    return MPC;
  }

  // ------------------------- instance methods -------------------------

  /** 启动 Hutch 实例, 并且每次启动成功都将重置 currentHutch */
  @Override
  public Hutch start() {
    if (!this.config.enable) {
      log.info("Hutch is disabled by config property and will not be started");
      return this;
    }

    if (this.isStarted) {
      return this;
    }

    try {
      lock.lock();
      initScheduleExecutor();

      connect();
      declareExchanges();
      declareScheduleQueues();
      declareHutchConsumerQueues();
    } finally {
      currentHutch = this;
      // 确保 currentHutch 不为 null
      initRedisClient();
      initHutchConsumerTriggers();
      this.isStarted = true;
      lock.unlock();
    }
    return this;
  }

  /** 初始化 Hutch 自己使用的默认操作进行连接 */
  @SneakyThrows
  public void connect() {
    log.info("Hutch{}({}) connect to RabbitMQ: {}", this, Hutch.name(), config.getUri());
    // 不能完全使用一样, 是避免在 quakrus 的 dev 模式进行代码 reload
    // https://www.cloudamqp.com/blog/the-relationship-between-connections-and-channels-in-rabbitmq.html
    var mode = LaunchMode.current().name();
    this.conn = RabbitUtils.connect(this.config, String.format("hutch-%s", mode));
    this.connForConsumer =
        RabbitUtils.connect(this.config, String.format("hutch-consumers-%s", mode));
    this.ch = conn.createChannel();
  }

  protected void declareExchanges() {
    try {
      this.ch.exchangeDeclare(HUTCH_EXCHANGE, "topic", true);
      this.ch.exchangeDeclare(HUTCH_SCHEDULE_EXCHANGE, "topic", true);
    } catch (Exception e) {
      // ignore
      log.error("Declare exchange error", e);
    }
  }

  protected void declareScheduleQueues() {
    // 初始化 delay queue 相关的信息
    var delayQueueArgs = new HashMap<String, Object>();
    // TODO: 可以考虑 x-message-ttl 为每个队列自己的超时时间, 这里设置成 30 天没有太大意义. (需要与 hutch-schedule 进行迁移)
    delayQueueArgs.put("x-message-ttl", TimeUnit.DAYS.toMillis(30));
    delayQueueArgs.put("x-dead-letter-exchange", HUTCH_EXCHANGE);
    if (this.config.quorum) {
      delayQueueArgs.put("x-queue-type", "quorum");
    }
    for (var g : Gradient.values()) {
      try {
        this.ch.queueDeclare(g.queue(), true, false, false, delayQueueArgs);
        this.ch.queueBind(g.queue(), HUTCH_SCHEDULE_EXCHANGE, Hutch.delayRoutingKey(g.fixdDelay()));
      } catch (Exception e) {
        log.error("Declare delay queue {} error", g.queue(), e);
      }
    }
  }

  /** 启动 Hutch 所有注册的 Consumer */
  protected void declareHutchConsumerQueues() {
    var queues = Hutch.queues();
    log.info(
        "Start Hutch ({}) with queues({}): {}",
        HutchConfig.getSharedExecutor().getClass().getSimpleName(),
        queues.size(),
        queues);
    for (var hc : Hutch.consumers()) {
      declareHutchConsumerQueue(hc);
      initHutchConsumer(hc);
      log.debug("Connect to {}", hc.queue());
    }
  }

  protected void declareHutchConsumerQueue(HutchConsumer hc) {
    try {
      var args = new HashMap<>(hc.queueArguments());
      if (this.config.quorum) {
        args.put("x-queue-type", "quorum");
      }
      this.ch.queueDeclare(hc.queue(), true, false, false, args);
      this.ch.queueBind(hc.queue(), HUTCH_EXCHANGE, hc.routingKey());
    } catch (Exception e) {
      log.error("Declare queues error", e);
    }
  }

  /** 每个实例拥有一个自己的 ScheduleExecutor. 并且 shutdown 之后, 需要重新构建一个 */
  protected void initScheduleExecutor() {
    this.scheduledExecutor = Executors.newScheduledThreadPool(this.config.schdulePoolSize);
  }

  protected void initHutchConsumer(HutchConsumer hc) {
    // Ref: https://github.com/rabbitmq/rabbitmq-perf-test/issues/93
    // 所有的队列保持一个 connection, 实际使用, 队列会非常多, 数量很容易增加到 30 个以上
    var scl = new LinkedList<SimpleConsumer>();
    for (var i = 0; i < hc.concurrency(); i++) {
      scl.add(consumeHutchConsumer(hc));
    }
    this.hutchConsumers.put(hc.queue(), scl);
  }

  /** 为所有的 Consumer 初始化 Job Trigger */
  protected void initHutchConsumerTriggers() {
    for (var hc : Hutch.consumers()) {
      initHutchConsumerTrigger(hc);
    }
  }

  /** 初始化 Job Trigger */
  protected void initHutchConsumerTrigger(HutchConsumer hc) {
    // TODO: 不需要每一次 threshold 的获取都去创建一个新的对象, 可以提供注册方法, 将其缓存起来. 不用 HyenaJob 每次运行都创建一个新的配置对象
    var threshold = threshold(hc);
    if (threshold == null) {
      return;
    }
    scheduledExecutor.scheduleAtFixedRate(
        new HyenaJob(hc), 0, threshold.interval(), TimeUnit.SECONDS);
  }

  /** 初始化 Redis Connection */
  protected void initRedisClient() {
    if (Strings.isNullOrEmpty(this.config.redisUrl)) {
      return;
    }
    this.redisConnection = RedisClient.create(this.config.redisUrl).connect();
    log.debug("初始化 redis 连接: {}", this.config.redisUrl);
  }

  /**
   *
   *
   * <ul>
   *   <li>停止所有的 SimpleConsumer
   *   <li>关闭主 Channel
   *   <li>关闭主 Connection
   *   <li>关闭 Consumer Connection
   * </ul>
   */
  @Override
  public void stop() {
    try {
      lock.lock();
      log.info("Stop Hutch");
      if (this.isStarted) {
        for (var q : this.hutchConsumers.keySet()) {
          this.hutchConsumers.get(q).forEach(SimpleConsumer::close);
        }
        this.hutchConsumers.clear();
      }
    } finally {
      scheduledExecutor.shutdownNow();
      RedisUtils.close(this.redisConnection);
      RabbitUtils.closeChannel(this.ch);
      RabbitUtils.closeConnection(this.conn);
      RabbitUtils.closeConnection(this.connForConsumer);
      this.isStarted = false;
      lock.unlock();
    }
  }

  /** 根据 Conn 在 RabbitMQ 上订阅一个队列进行消费 */
  public SimpleConsumer consumeHutchConsumer(HutchConsumer hc) {
    SimpleConsumer consumer = null;
    Channel ch = null;
    try {
      ch = this.connForConsumer.createChannel();
      // 并发处理, 每一个 Consumer 为一个并发
      consumer = new SimpleConsumer(ch, hc);
      consumer.consume();
    } catch (Exception e) {
      RabbitUtils.closeChannel(ch);
    }
    return consumer;
  }
}
