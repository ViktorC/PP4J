/*
 * Copyright 2017 Viktor Csomor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.viktorc.pp4j.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.viktorc.pp4j.api.JavaProcessOptions;

/**
 * A simple implementation of the {@link net.viktorc.pp4j.api.JavaProcessOptions} interface for the definition of JVM options.
 *
 * @author Viktor Csomor
 */
public class SimpleJavaProcessOptions implements JavaProcessOptions {

  private JVMArch arch;
  private JVMType type;
  private Integer initHeapSizeMb;
  private Integer maxHeapSizeMb;
  private Integer stackSizeKb;
  private String additionalClassPath;

  /**
   * Constructs an instance according to the specified parameters.
   *
   * @param arch The architecture of the JVM. If it is null, it will be ignored.
   * @param type The type of the JVM. If it is null, it will be ignored.
   * @param initHeapSizeMb The initial heap size of the JVM in megabytes.
   * @param maxHeapSizeMb The maximum heap size of the JVM in megabytes.
   * @param stackSizeKb The maximum stack size of the JVM in kilobytes.
   * @param additionalClassPaths Any additional class paths to be used by the JVM.
   */
  public SimpleJavaProcessOptions(JVMArch arch, JVMType type, Integer initHeapSizeMb, Integer maxHeapSizeMb, Integer stackSizeKb,
      String... additionalClassPaths) {
    this.arch = arch;
    this.type = type;
    this.initHeapSizeMb = initHeapSizeMb;
    this.maxHeapSizeMb = maxHeapSizeMb;
    this.stackSizeKb = stackSizeKb;
    additionalClassPath = additionalClassPaths.length > 0 ? String.join(File.pathSeparator, additionalClassPaths) : null;
  }

  /**
   * Constructs an instance according to the specified parameters.
   *
   * @param initHeapSizeMb The initial heap size of the JVM in megabytes.
   * @param maxHeapSizeMb The maximum heap size of the JVM in megabytes.
   * @param stackSizeKb The maximum stack size of the JVM in kilobytes.
   */
  public SimpleJavaProcessOptions(Integer initHeapSizeMb, Integer maxHeapSizeMb, Integer stackSizeKb) {
    this(null, null, initHeapSizeMb, maxHeapSizeMb, stackSizeKb);
  }

  @Override
  public Optional<String> getClassPath() {
    List<String> classPaths = new ArrayList<>();
    JavaProcessOptions.super.getClassPath().ifPresent(classPaths::add);
    Optional.ofNullable(additionalClassPath).ifPresent(classPaths::add);
    return Optional.ofNullable(classPaths.isEmpty() ? null : String.join(File.pathSeparator, classPaths));
  }

  @Override
  public Optional<JVMArch> getArch() {
    return Optional.ofNullable(arch);
  }

  @Override
  public Optional<JVMType> getType() {
    return Optional.ofNullable(type);
  }

  @Override
  public Optional<Integer> getInitHeapSizeMb() {
    return  Optional.ofNullable(initHeapSizeMb);
  }

  @Override
  public Optional<Integer> getMaxHeapSizeMb() {
    return  Optional.ofNullable(maxHeapSizeMb);
  }

  @Override
  public Optional<Integer> getStackSizeKb() {
    return  Optional.ofNullable(stackSizeKb);
  }

}
