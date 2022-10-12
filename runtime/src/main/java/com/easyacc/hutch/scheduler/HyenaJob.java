package com.easyacc.hutch.scheduler;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Threshold;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** 提供基于 redis 主动式 hutch scheduler job */
@Slf4j
public class HyenaJob implements Runnable {

  /** 根据 Threshold.key 计算出来的同一个 Consumer 的不同的 threshold.keys */
  private List<String> consumerThresholdKeys = new ArrayList<>();
  /** 缓存最后更新时间 */
  private Instant updatedAt = Instant.now().minusSeconds(11);

  private final HutchConsumer hc;
  private final Threshold threshold;

  public HyenaJob(HutchConsumer hutchConsumer) {
    // 如果初始化了 HyenaJob 那么就一定需要拥有 threshold
    Objects.requireNonNull(hutchConsumer.threshold());

    this.hc = hutchConsumer;
    this.threshold = hc.threshold();
  }

  @Override
  public void run() {
    // 检查 Hutch 是否启动
    if (Hutch.current() == null) {
      log.error("Hutch 还未启动!");
      return;
    }

    // 1. 尝试刷新一次 redis keys
    this.reloadRedisKeys(this.hc.queue());
    // 2. 从 redis 中获取 task
    for (var key : this.consumerThresholdKeys) {
      this.fetchAndPublish(key);
    }
  }

  /** 刷新一次 redis keys */
  private void reloadRedisKeys(String prefix) {
    var intervals = Duration.between(this.updatedAt, Instant.now()).toSeconds();
    if (intervals < TimeUnit.MINUTES.toSeconds(1)) {
      log.debug("Reload skipped. The interval must > 1m, right now is: {}s", intervals);
    }

    this.consumerThresholdKeys = Hutch.redis().keys(String.format("%s*", prefix));
    this.updatedAt = Instant.now();
  }

  public void fetchAndPublish(String key) {
    var tasks = Hutch.redis().zrange(key, 0, threshold.rate() - 1);
    if (tasks.isEmpty()) {
      log.debug("从 redis 中未找到任务数据! key: {}", key);
      return;
    }

    // 通过 Hutch 来 publish 出去
    threshold.publish(tasks);
    // 从 redis 队列中移除 tasks
    Hutch.redis().zrem(key, tasks.toArray(String[]::new));
  }
}
