package com.easyacc.hutch.deployment;

import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Message;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 17:02 */
public class LongTimeConsumer implements HutchConsumer {

  @Override
  public int concurrency() {
    return 2;
  }

  public static AtomicInteger Runs = new AtomicInteger(0);

  @Override
  public void onMessage(Message message) throws InterruptedException {
    Runs.incrementAndGet();
    try {
      System.out.println(Thread.currentThread().getName() + ": LongTimeConsumer begin wait..");
      Thread.sleep(Duration.of(10, ChronoUnit.SECONDS).toMillis());
      System.out.println(Thread.currentThread().getName() + ": LongTimeConsumer finished wait..");
    } finally {
      Runs.decrementAndGet();
    }
  }

  @Override
  public int maxRetry() {
    return 1;
  }
}
