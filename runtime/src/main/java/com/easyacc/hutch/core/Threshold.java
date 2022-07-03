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

  /** redis 队列, 默认使用 Consumer 队列 */
  default String queue(HutchConsumer hc) {
    return hc.queue();
  }
}
