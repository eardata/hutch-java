package com.easyacc.hutch.devmode;

import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

/** Created by IntelliJ IDEA. User: mpx Date: 2024/4/2 Time: 00:40 */
@Slf4j
public class ConsumerHotReplaceSetup implements HotReplacementSetup {

  private static DevHotReloadHandler handler;

  public static void reloadCheck() {
    if (ConsumerHotReplaceSetup.handler == null) {
      return;
    }
    ConsumerHotReplaceSetup.handler.handle();
  }

  @Override
  public void setupHotDeployment(HotReplacementContext context) {
    log.info("ConsumerHotReplaceSetup..........");
    // 1. 在应用初始化的时候触发一个 Hook
    // 2. 在这里需要嫁接在 Hutch 运行时一个触发关联, 每一个消息被处理, 触发一次间隔 2s 的 doScan 检查
    ConsumerHotReplaceSetup.handler = new DevHotReloadHandler(context);
  }

  private class DevHotReloadHandler {
    private static final long TWO_SECONDS = 2000;

    HotReplacementContext context;

    private volatile long nextUpdate;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public DevHotReloadHandler(HotReplacementContext context) {
      this.context = context;
    }

    /** 每次间隔 2s 则向 executor 中添加一个一步执行 context.doScan 的触发方法 */
    public void handle() {
      if (nextUpdate < System.currentTimeMillis()) {
        synchronized (this) {
          if (nextUpdate < System.currentTimeMillis()) {
            executor.execute(
                () -> {
                  try {
                    context.doScan(true);
                  } catch (RuntimeException e) {
                    throw e;
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
            // we update at most once every 2s
            nextUpdate = System.currentTimeMillis() + TWO_SECONDS;
          }
        }
      }
    }
  }
}
