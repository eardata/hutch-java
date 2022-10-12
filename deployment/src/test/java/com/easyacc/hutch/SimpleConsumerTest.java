package com.easyacc.hutch;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.core.MessageProperties;
import com.easyacc.hutch.error_handlers.MaxRetry;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SimpleConsumerTest {
  /** 测试获取最大重试次数代码 */
  @Test
  void testGetXDeathLatestCount() {
    var props = new MessageProperties();
    props.setHeader("CC", List.of("abc_test"));
    props.setHeader("x-first-death-exchange", "hutch.schedule");
    props.setHeader("x-first-death-reason", "expired");
    props.setHeader("x-first-queue", "hutch_delay_5s");

    // 多次执行, 信息会留存, 寻找其中 count 最大的即可
    var c1 = new HashMap<String, Object>();
    c1.put("reason", "expired");
    c1.put("original-expiration", "5000");
    c1.put("count", 1L);
    c1.put("exchange", "hutch.schedule");
    c1.put("routing-keys", List.of("hutch.schedule.5s", "abc_test"));

    var c2 = new HashMap<String, Object>();
    c2.put("reason", "expired");
    c2.put("original-expiration", "5000");
    c2.put("count", 3L);
    c2.put("exchange", "hutch.schedule");
    c2.put("routing-keys", List.of("hutch.schedule.5s", "abc_test"));
    props.setHeader("x-death", List.of(c1, c2));

    assertThat(MaxRetry.getXDeathLatestCount(props)).isEqualTo(3L);

    var props2 = new MessageProperties();
    assertThat(MaxRetry.getXDeathLatestCount(props2)).isEqualTo(0L);
  }
}
