package com.easyacc.hutch.util;

import java.util.Map;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 16:35 */
public class MapUtil {
  public static boolean isEmpty(final Map<?, ?> map) {
    return map == null || map.isEmpty();
  }

  public static boolean isNotEmpty(final Map<?, ?> map) {
    return !MapUtil.isEmpty(map);
  }
}
