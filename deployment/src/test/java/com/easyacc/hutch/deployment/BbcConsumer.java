package com.easyacc.hutch.deployment;

import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Message;
import java.util.concurrent.atomic.AtomicInteger;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 17:02 */
public class BbcConsumer implements HutchConsumer {
  public static AtomicInteger Timers = new AtomicInteger(0);

  @Override
  public void onMessage(Message message) {
    Timers.incrementAndGet();
    System.out.println("BbcConsumer received message: " + message.getBodyContentAsString());
    throw new RuntimeException("BbcConsumer received message: " + message.getBodyContentAsString());
  }

  @Override
  public int maxRetry() {
    return 1;
  }
}
