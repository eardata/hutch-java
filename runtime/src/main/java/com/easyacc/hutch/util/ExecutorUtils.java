package com.easyacc.hutch.util;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Created by IntelliJ IDEA. User: kenyon Date: 2023/12/8 Time: 18:10 */
public class ExecutorUtils {
  public static List<Runnable> closeNow(ExecutorService executor) {
    if (executor == null) {
      return List.of();
    }
    return executor.shutdownNow();
  }

  public static void close(ScheduledExecutorService service) {
    if (service == null) {
      return;
    }

    service.shutdown();
    try {
      while (!service.isTerminated()) {
        TimeUnit.SECONDS.sleep(1);
      }
    } catch (InterruptedException ex) {
      service.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
