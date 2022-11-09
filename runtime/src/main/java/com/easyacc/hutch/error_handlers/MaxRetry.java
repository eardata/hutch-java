package com.easyacc.hutch.error_handlers;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.core.ErrorHandler;
import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Message;
import com.easyacc.hutch.core.MessageProperties;
import com.easyacc.hutch.util.HutchUtils;
import java.util.Map;
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

    var c = 0L;
    for (Map<String, ?> m : xDeath) {
      if (m.get("count") instanceof Long ct) {
        c = ct;
      }
    }
    return c;
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
      var rk = hc.routingKey();
      var delayInMs = backoffDelay(retryCount);
      var basicProps = Hutch.convertToDelayProps(rk, msg.getMessageProperties(), delayInMs);

      Hutch.publishWithDelay(delayInMs, basicProps, msg.getBody());
      hc.cc()
          .debug(
              "publish with delay {} using routing_key {} and origin routing_key: {}",
              basicProps.getExpiration(),
              HutchUtils.delayRoutingKey(delayInMs),
              rk);
    } else {
      hc.cc()
          .info(
              "message retry count ({}) reach max ({}), ignore message", retryCount, hc.maxRetry());
    }
  }
}
