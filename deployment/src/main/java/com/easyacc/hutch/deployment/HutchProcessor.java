package com.easyacc.hutch.deployment;

import com.easyacc.hutch.Hutch;
import com.easyacc.hutch.config.HutchRecorder;
import com.easyacc.hutch.core.HutchConsumer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AutoAddScopeBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

public class HutchProcessor {

  static final DotName HUTCH_CONSUMER_NAME = DotName.createSimple(HutchConsumer.class.getName());
  private static final Logger LOGGER = Logger.getLogger(HutchProcessor.class);
  private static final String FEATURE = "hutch";

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }

  /** 构建期将标记有 @HutchConsumer 的元素设置为 SINGLETON 的 scope */
  @BuildStep
  AutoAddScopeBuildItem autoAddScope() {
    // 参考官方的 schedule extension 来编写
    // https://github.com/quarkusio/quarkus/blob/main/extensions/scheduler/deployment/src/main/java/io/quarkus/scheduler/deployment/SchedulerProcessor.java
    LOGGER.debug("run autoAddScope to add HutchConsumer to CDI");
    return AutoAddScopeBuildItem.builder()
        .unremovable()
        .implementsInterface(HUTCH_CONSUMER_NAME)
        .defaultScope(DotNames.SINGLETON)
        .reason("Found hutch consumer class")
        .build();
  }

  /** 不知道为什么通过 AutoAddScope 添加的类设置了 unremovable 但无法生效, 所以这里强制指定这 bean 需要保留在 CDI 中 */
  @BuildStep
  UnremovableBeanBuildItem unremovable() {
    return UnremovableBeanBuildItem.beanTypes(HUTCH_CONSUMER_NAME);
  }

  /** 在这里读取 HutchConfig 对 Hutch 做一些 static 与 runtime 的设置和初始化. 合并一个 Bean 交给 CDI */
  @BuildStep
  @Record(ExecutionTime.STATIC_INIT)
  AdditionalBeanBuildItem setUpHutchInstance(HutchRecorder recorder) {
    recorder.initHutchName();
    return AdditionalBeanBuildItem.builder()
        .addBeanClass(Hutch.class)
        .setDefaultScope(DotNames.SINGLETON)
        .setUnremovable()
        .build();
  }
}
