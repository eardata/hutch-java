package com.easyacc.hutch.scheduler;

import com.easyacc.hutch.Hutch;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

/** 提供基于 redis 主动式 hutch scheduler job */
@Slf4j
public class HyenaJob implements Job {
  /** 需要执行的 redis keys */
  private List<String> redisKeys = new ArrayList<>();
  /** 缓存最后更新时间 */
  private Instant updatedAt = Instant.now().minusSeconds(11);

  @Override
  public void execute(JobExecutionContext context) {
    var redis = Hutch.current().getRedisConnection().sync();

    var jobDataMap = context.getJobDetail().getJobDataMap();
    var rate = jobDataMap.getInt("rate");
    var queue = jobDataMap.getString("queue");
    var routingKey = jobDataMap.getString("routingKey");

    // 1. 尝试刷新一次 redis keys
    this.reloadRedisKeys(queue, redis);

    // 2. 从 redis 中获取 task
    for (var key : this.redisKeys) {
      var tasks = redis.zrange(key, 0, rate - 1);
      if (tasks.isEmpty()) {
        log.debug("从 redis 中未找到任务数据! key: {}", key);
        continue;
      }

      // 通过 Hutch 来 publish 出去
      tasks.forEach(task -> Hutch.publish(routingKey, task));
      // 从 redis 队列中移除 tasks
      redis.zrem(key, tasks.toArray(String[]::new));
    }
  }

  /** 刷新一次 redis keys */
  private void reloadRedisKeys(String queue, RedisCommands<String, String> redis) {
    var intervals = Duration.between(this.updatedAt, Instant.now()).toSeconds();
    if (intervals < 10) {
      log.info("Reload skipped. The interval must > 10s, right now is: {}s", intervals);
    }

    this.redisKeys = redis.keys(String.format("%s.*", queue));
    this.updatedAt = Instant.now();
  }
}
