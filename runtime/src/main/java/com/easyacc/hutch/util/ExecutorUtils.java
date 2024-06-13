package com.easyacc.hutch.util;

import java.util.List;
import java.util.concurrent.ExecutorService;

/** Created by IntelliJ IDEA. User: kenyon Date: 2023/12/8 Time: 18:10 */
public class ExecutorUtils {
  public static List<Runnable> closeNow(ExecutorService executor) {
    if (executor == null) {
      return List.of();
    }
    return executor.shutdownNow();
  }
}
