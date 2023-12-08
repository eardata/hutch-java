package com.easyacc.hutch.util;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Created by IntelliJ IDEA. User: kenyon Date: 2023/12/8 Time: 18:10 */
public class ExecutorUtils {
  public static void close(ScheduledExecutorService service) {
    if (service == null) {
      return;
    }

    service.shutdown();
    try {
      // 最多等待 60s
      if (!service.awaitTermination(60, TimeUnit.SECONDS)) {
        service.shutdownNow();
      }
    } catch (InterruptedException ex) {
      service.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
