package com.easyacc.hutch.core;

/** 自定义的主动限流参数 */
public interface Threshold {
  /** 每次执行加载出来的数量 */
  default int rate() {
    return 1;
  }

  /** 每次执行的间隔时间(单位: s) */
  default int interval() {
    return 1;
  }

  /** 通过 msg 来计算 redis 队列, 用于 publish 时计算目标 key, 默认使用 Consumer 队列名称 */
  default String queue(HutchConsumer hc, Object msg) {
    return hc.queue();
  }

  /** 将 redis 中的消息 publish 出去 */
  void publish(String msg);
}
