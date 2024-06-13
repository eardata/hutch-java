package com.easyacc.hutch.utils;

import static com.google.common.truth.Truth.assertThat;

import com.easyacc.hutch.util.HutchUtils.Gradient;
import org.junit.jupiter.api.Test;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/19 Time: 07:21 */
public class HutchUtilsTest {
  @Test
  void testGradientName() {
    assertThat(Gradient.G5.toString()).isEqualTo("5s");
    assertThat(Gradient.G5.queue()).isEqualTo("hutch_delay_queue_5s");
    assertThat(Gradient.G5.fixdDelay()).isEqualTo(5000L);
  }


  @Test
  void testBackOffDelay() {
    assertThat(Math.pow(3, 0)).isEqualTo(1);
    assertThat(Math.pow(3, 1)).isEqualTo(3);
    assertThat(Math.pow(3, 2)).isEqualTo(9);
    assertThat(Math.pow(3, 3)).isEqualTo(27);
    assertThat(Math.pow(3, 4)).isEqualTo(81);
    assertThat(Math.pow(3, 5)).isEqualTo(243);
  }
}
