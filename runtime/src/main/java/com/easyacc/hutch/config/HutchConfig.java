package com.easyacc.hutch.config;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.annotations.StaticInitSafe;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 20:01 */
@ConfigRoot(name = "hutch", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
@StaticInitSafe
public class HutchConfig {

  /** Hutch 的 prefix 前缀名称 */
  @ConfigItem(defaultValue = "hutch")
  public String name;
}
