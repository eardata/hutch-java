package com.easyacc.hutch.deployment;

import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Message;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 16:46 */
public class AbcConsumer implements HutchConsumer {
  public static AtomicInteger Timers = new AtomicInteger(0);

  @Override
  public void onMessage(Message message) {
    Timers.incrementAndGet();
    System.out.println("AbcConsumer received message: " + message.getBodyContentAsString());
  }

  public static void clas() {
    var c = MethodHandles.lookup().lookupClass();
    System.out.println(c.getSimpleName());
  }
}
