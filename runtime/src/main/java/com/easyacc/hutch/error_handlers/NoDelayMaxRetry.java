package com.easyacc.hutch.error_handlers;

import lombok.extern.slf4j.Slf4j;

/** for test. The rabbitmq min delay is 5s */
@Slf4j
public class NoDelayMaxRetry extends MaxRetry {

  /** 根据 retryCount 计算重试时间: 3s, 5s, 11s... */
  long backoffDelay(long retryCount) {
    return 0;
  }
}
