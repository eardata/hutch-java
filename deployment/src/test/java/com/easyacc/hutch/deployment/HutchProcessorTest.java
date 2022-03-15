package com.easyacc.hutch.deployment;

import static com.google.common.truth.Truth.assertThat;

import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 16:44 */
class HutchProcessorTest {
  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.application.name", "hutch-app")
          .withEmptyApplication();

  @Test
  public void testGreeting() {
    assertThat(config).isNotNull();
  }
}
