package com.easyacc.hutch.util;

import java.util.concurrent.ExecutorService;

/** Created by IntelliJ IDEA. User: kenyon Date: 2023/12/8 Time: 18:10 */
public class ExecutorUtils {
  public static void close(ExecutorService service) {
    if (service == null) {
      return;
    }

    // 因为关闭任务, 无法确保任务执行后能够发送消息给 mq, 所以也直接一并关闭
    service.shutdownNow();
  }
}
