package com.easyacc.hutch.config;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.annotations.StaticInitSafe;

/** 提供配置 Hutch 的配置. 需要是 StaticInitSafe 状态, 能够在 static 的时候就开始处理 */
@StaticInitSafe
@ConfigRoot(name = "hutch", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class HutchConfig {

  /** Hutch 的 prefix 前缀名称 */
  @ConfigItem(defaultValue = "hutch")
  public String name;
}
