package com.easyacc.hutch.core;

import com.easyacc.hutch.Hutch;
import java.util.UUID;
import lombok.Getter;

/** 一次任务执行的 Context, 用于记录一次任务执行过程中的一些元信息 */
public class ConsumeContext {

  @Getter
  // task Id
  private String tid;

  private long startAt;

  private HutchConsumer consumer;

  public static ConsumeContext ofConsumer(HutchConsumer consumer) {
    var cc = new ConsumeContext();
    cc.consumer = consumer;
    cc.startAt = System.currentTimeMillis();
    cc.tid = generateTid();
    return cc;
  }

  /**
   * 结束并返回执行耗时(单位 ms)
   *
   * @return 1292 ms
   */
  public long endTime() {
    return System.currentTimeMillis() - this.startAt;
  }

  private String logPrefix() {
    return String.format("%s TID - %s", consumer.name(), getTid());
  }

  public void info(String s, Object... objectsj) {
    Hutch.log().info(String.format("%s %s", logPrefix(), s), objectsj);
  }

  public void error(String s, Object... objectsj) {
    Hutch.log().error(String.format("%s %s", logPrefix(), s), objectsj);
  }

  public void error(String s, Throwable t) {
    Hutch.log().error(String.format("%s %s", logPrefix(), s), t);
  }

  public void debug(String s, Object... objectsj) {
    Hutch.log().debug(String.format("%s %s", logPrefix(), s), objectsj);
  }

  public void warn(String s, Object... objectsj) {
    Hutch.log().warn(String.format("%s %s", logPrefix(), s), objectsj);
  }

  /**
   * 生成一个 Tid
   *
   * @return uuid 删除了 "-" 之后作为 tid
   */
  public static String generateTid() {
    return UUID.randomUUID().toString().replaceAll("-", "");
  }
}
