package com.easyacc.hutch.gizmo;

import io.quarkus.gizmo.ClassOutput;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/19 Time: 07:58 */
public class PathClassOutput implements ClassOutput {

  @Override
  public void write(String name, byte[] data) {
    var path = Thread.currentThread().getContextClassLoader().getResource("").getPath();
    System.out.println("输出路径:" + path);
    // 文件输出流输出生成的class文件
    try (var os = Files.newOutputStream(Path.of(path + "/" + name + ".class"))) {
      // class文件输出到maven的target/classes
      os.write(data);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public Writer getSourceWriter(String className) {
    return ClassOutput.super.getSourceWriter(className);
  }
}
