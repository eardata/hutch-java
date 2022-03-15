package com.easyacc.hutch.util;

import com.google.common.base.CaseFormat;
import java.util.concurrent.TimeUnit;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/6 Time: 07:29 */
public class HutchUtils {
  public static String upCamelToLowerUnderscore(String uperCamel) {
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, uperCamel);
  }
  /**
   * 因为 rabbitmq 无法做到秒级精确, 所以做了梯度精确, 将 delay 转换为确定的梯度重试. (ms) 计算 delay 的 level:<br>
   * s: 5s 10s 20s 30s<br>
   * m: 60s 120s 180s 240s 300s 360s 420s 480s 540s 600s 1200s 1800s 2400s<br>
   * h: 3600s 7200s 10800s<br>
   */
  public static long fixDealyTime(long delay) {
    if (delay > 0 && delay <= TimeUnit.SECONDS.toMillis(5)) {
      return TimeUnit.SECONDS.toMillis(5);
    } else if (delay > TimeUnit.SECONDS.toMillis(5) && delay <= TimeUnit.SECONDS.toMillis(10)) {
      return TimeUnit.SECONDS.toMillis(10);
    } else if (delay > TimeUnit.SECONDS.toMillis(10) && delay <= TimeUnit.SECONDS.toMillis(20)) {
      return TimeUnit.SECONDS.toMillis(20);
    } else if (delay > TimeUnit.SECONDS.toMillis(20) && delay <= TimeUnit.SECONDS.toMillis(30)) {
      return TimeUnit.SECONDS.toMillis(30);
    } else if (delay > TimeUnit.SECONDS.toMillis(30) && delay <= TimeUnit.SECONDS.toMillis(60)) {
      return TimeUnit.SECONDS.toMillis(60);
    } else if (delay > TimeUnit.SECONDS.toMillis(60) && delay <= TimeUnit.SECONDS.toMillis(120)) {
      return TimeUnit.SECONDS.toMillis(120);
    } else if (delay > TimeUnit.SECONDS.toMillis(120) && delay <= TimeUnit.SECONDS.toMillis(180)) {
      return TimeUnit.SECONDS.toMillis(180);
    } else if (delay > TimeUnit.SECONDS.toMillis(180) && delay <= TimeUnit.SECONDS.toMillis(240)) {
      return TimeUnit.SECONDS.toMillis(240);
    } else if (delay > TimeUnit.SECONDS.toMillis(240) && delay <= TimeUnit.SECONDS.toMillis(300)) {
      return TimeUnit.SECONDS.toMillis(300);
    } else if (delay > TimeUnit.SECONDS.toMillis(300) && delay <= TimeUnit.SECONDS.toMillis(360)) {
      return TimeUnit.SECONDS.toMillis(360);
    } else if (delay > TimeUnit.SECONDS.toMillis(360) && delay <= TimeUnit.SECONDS.toMillis(420)) {
      return TimeUnit.SECONDS.toMillis(420);
    } else if (delay > TimeUnit.SECONDS.toMillis(420) && delay <= TimeUnit.SECONDS.toMillis(480)) {
      return TimeUnit.SECONDS.toMillis(480);
    } else if (delay > TimeUnit.SECONDS.toMillis(480) && delay <= TimeUnit.SECONDS.toMillis(540)) {
      return TimeUnit.SECONDS.toMillis(540);
    } else if (delay > TimeUnit.SECONDS.toMillis(540) && delay <= TimeUnit.SECONDS.toMillis(600)) {
      return TimeUnit.SECONDS.toMillis(600);
    } else if (delay > TimeUnit.SECONDS.toMillis(600) && delay <= TimeUnit.SECONDS.toMillis(1200)) {
      return TimeUnit.SECONDS.toMillis(1200);
    } else if (delay > TimeUnit.SECONDS.toMillis(1200)
        && delay <= TimeUnit.SECONDS.toMillis(1800)) {
      return TimeUnit.SECONDS.toMillis(1800);
    } else if (delay > TimeUnit.SECONDS.toMillis(1800)
        && delay <= TimeUnit.SECONDS.toMillis(2400)) {
      return TimeUnit.SECONDS.toMillis(2400);
    } else if (delay > TimeUnit.SECONDS.toMillis(2400)
        && delay <= TimeUnit.SECONDS.toMillis(3600)) {
      return TimeUnit.SECONDS.toMillis(3600);
    } else if (delay > TimeUnit.SECONDS.toMillis(3600)
        && delay <= TimeUnit.SECONDS.toMillis(7200)) {
      return TimeUnit.SECONDS.toMillis(7200);
    } else if (delay > TimeUnit.SECONDS.toMillis(7200)
        && delay <= TimeUnit.SECONDS.toMillis(10800)) {
      return TimeUnit.SECONDS.toMillis(10800);
    } else {
      return TimeUnit.HOURS.toMillis(3);
    }
  }

  /** 所有的梯度值 */
  public enum Gradient {
    G5,
    G10,
    G20,
    G30,

    G60,
    G120,
    G180,
    G240,
    G300,
    G360,
    G420,
    G480,
    G540,
    G600,
    G1200,
    G1800,
    G2400,

    G3600,
    G7200,
    G10800;

    @Override
    public String toString() {
      return super.toString().replace("G", "") + "s";
    }

    /**
     * 返回对应的 fixdDelay (ms)
     *
     * @return
     */
    public long fixdDelay() {
      return Long.parseLong(super.toString().replace("G", "")) * 1000;
    }

    public String queue() {
      return String.format("hutch_delay_queue_%s", this.toString());
    }
  }
}
