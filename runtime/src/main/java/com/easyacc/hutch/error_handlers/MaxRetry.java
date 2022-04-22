package com.easyacc.hutch.error_handlers;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.core.ErrorHandler;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Message;
import com.easyacc.hutch.core.MessageProperties;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/** 有限最大次数重试的 ErrorHandler */
@Slf4j
public class MaxRetry implements ErrorHandler {

  /** 获取重试的最大次数 */
  public static long getXDeathLatestCount(MessageProperties props) {
    var xDeath = props.getXDeathHeader();
    if (xDeath == null) {
      return 0;
    }
    var c = new AtomicLong(0L);
    xDeath.forEach(
        m -> {
          var cs = m.get("count");
          if (cs instanceof Long) {
            c.set((long) cs);
          }
        });
    return c.get();
  }

  /** 根据 retryCount 计算重试时间: 3s, 5s, 11s... */
  long backoffDelay(long retryCount) {
    return (long) (Math.pow(3, retryCount) + 2) * 1000;
  }

  @Override
  public void handle(HutchConsumer hc, Message msg, Exception ex) {
    // 重试检查
    var props = msg.getMessageProperties();
    var retryCount = getXDeathLatestCount(props);
    if (retryCount < hc.maxRetry()) {
      Hutch.publishMessageWithDelay(backoffDelay(retryCount), hc.routingKey(), msg);
    } else {
      log.info(
          "message retry count ({}) reach max ({}), ignore message", retryCount, hc.maxRetry());
    }
  }
}
