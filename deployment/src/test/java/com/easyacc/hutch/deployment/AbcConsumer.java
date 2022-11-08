package com.easyacc.hutch.deployment;

import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Message;
import com.easyacc.hutch.core.Threshold;
import com.easyacc.hutch.publisher.BodyPublisher;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 16:46 */
public class AbcConsumer implements HutchConsumer {
  public static AtomicInteger Timers = new AtomicInteger(0);

  @Override
  public int concurrency() {
    return 256;
  }

  @Override
  public Threshold threshold() {
    return new Threshold() {
      @Override
      public int rate() {
        return 1;
      }

      @Override
      public int interval() {
        return 1;
      }

      @Override
      public String key(String msg) {
        return msg;
      }

      @Override
      public Consumer<List<String>> batch() {
        return msgs -> {
          for (var msg : msgs) {
            BodyPublisher.publish(AbcConsumer.class, msg);
          }
        };
      }
    };
  }

  @Override
  public void onMessage(Message message) {
    Timers.incrementAndGet();
    cc().info("received message: {}", message.getBodyContentAsString());
  }

  public static void clas() {
    var c = MethodHandles.lookup().lookupClass();
    System.out.println(c.getSimpleName());
  }
}
