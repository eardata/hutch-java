package com.easyacc.hutch.gizmo;

import com.easyacc.hutch.core.HutchConsumer;
import com.easyacc.hutch.core.Message;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.MethodDescriptor;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/19 Time: 07:54 */
public class GizmoTest {
  @Test
  void createConsumerClass() {
    var cc =
        ClassCreator.builder()
            .classOutput(new PathClassOutput())
            .className("com.easyacc.hutch.deployment.GizomoConsumer")
            .interfaces(HutchConsumer.class)
            .build();

    var mc = cc.getMethodCreator("onMessage", void.class, Message.class);
    mc.setModifiers(Opcodes.ACC_PUBLIC);
    mc.addAnnotation("Override", RetentionPolicy.CLASS);

    MethodDescriptor.ofMethod(HutchConsumer.class, "onMessage", void.class, Message.class);

    cc.close();
  }
}
