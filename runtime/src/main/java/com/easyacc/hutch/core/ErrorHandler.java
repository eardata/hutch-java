package com.easyacc.hutch.core;

/** 自定义的 Error Handler 处理器 */
public interface ErrorHandler {
  void handle(HutchConsumer hc, Message msg, Exception ex);
}
