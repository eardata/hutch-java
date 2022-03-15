package com.easyacc.hutch.deployment;

import com.easyacc.hutch.core.HutchConsumer;
import io.quarkus.arc.deployment.AutoAddScopeBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

class HutchProcessor {

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
    LOGGER.info("run autoAddScope to add @HutchConsumer to CDI");
    return AutoAddScopeBuildItem.builder()
        .implementsInterface(HUTCH_CONSUMER_NAME)
        .defaultScope(BuiltinScope.SINGLETON)
        .reason("Found hutch consumer class")
        .build();
  }
}
