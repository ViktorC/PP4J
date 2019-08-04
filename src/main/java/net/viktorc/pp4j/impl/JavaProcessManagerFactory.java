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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import net.viktorc.pp4j.api.JavaProcessConfig;
import net.viktorc.pp4j.api.JavaProcessConfig.JVMArch;
import net.viktorc.pp4j.api.JavaProcessConfig.JVMType;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;

/**
 * An implementation of the {@link ProcessManagerFactory} for the creation of {@link JavaProcessManager} instances using a single
 * {@link ProcessBuilder} instance.
 *
 * @param <T> A type variable implementing the {@link Runnable} and {@link Serializable} interfaces that defines the base class of the
 * startup tasks of the created {@link JavaProcessManager} instances.
 * @author Viktor Csomor
 */
public class JavaProcessManagerFactory<T extends Runnable & Serializable> implements ProcessManagerFactory {

  private final JavaProcessConfig config;
  private final T initTask;
  private final T wrapUpTask;
  private final Long keepAliveTime;

  /**
   * Constructs an instance based on the specified JVM config, <code>keepAliveTime</code>, initiation task, and wrap-up task which are
   * used for the creation of all processes of the pool.
   *
   * @param config The JVM config for starting the Java process.
   * @param initTask The task to execute in each process on startup, before the process starts accepting submissions. If it is
   * <code>null</code>, no taks are executed on startup.
   * @param wrapUpTask The task to execute in each process before it is terminated. If it is <code>null</code>, no wrap-up task is
   * executed.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated.
   * @throws IllegalArgumentException If the <code>config</code> is <code>null</code> or contains invalid values.
   */
  public JavaProcessManagerFactory(JavaProcessConfig config, T initTask, T wrapUpTask, Long keepAliveTime) {
    if (config == null) {
      throw new IllegalArgumentException("The config argument cannot be null");
    }
    if (config.getJavaLauncherCommand() == null || config.getJavaLauncherCommand().isEmpty()) {
      throw new IllegalArgumentException("The Java launcher command cannot be null or empty");
    }
    if (config.getInitHeapSizeMb().isPresent() && config.getInitHeapSizeMb().get() <= 0) {
      throw new IllegalArgumentException("Initial heap size must be greater than 0");
    }
    if (config.getMaxHeapSizeMb().isPresent() && config.getMaxHeapSizeMb().get() <= 0) {
      throw new IllegalArgumentException("Maximum heap size must be greater than 0");
    }
    if (config.getStackSizeKb().isPresent() && config.getStackSizeKb().get() <= 0) {
      throw new IllegalArgumentException("Stack size must be greater than 0");
    }
    if (config.getClassPath().isPresent() && config.getClassPath().get().isEmpty()) {
      throw new IllegalArgumentException("Class path cannot be an empty string");
    }
    this.config = config;
    this.initTask = initTask;
    this.wrapUpTask = wrapUpTask;
    this.keepAliveTime = keepAliveTime;
  }

  /**
   * Constructs an instance based on the specified JVM config which is used for the creation of all processes of the pool.
   *
   * @param config The JVM config for starting the Java process.
   * @throws IllegalArgumentException If the <code>config</code> is <code>null</code> or contains invalid values.
   */
  public JavaProcessManagerFactory(JavaProcessConfig config) {
    this(config, null, null, null);
  }

  /**
   * Creates a <code>ProcessBuilder</code> instance for launching Java processes using the provided Java process configuration.
   *
   * @return A process builder for launching Java processes.
   */
  protected ProcessBuilder createProcessBuilder() {
    String javaCommand = config.getJavaLauncherCommand();
    List<String> javaOptions = new ArrayList<>();
    config.getClassPath().ifPresent(v -> {
      javaOptions.add("-cp");
      javaOptions.add(v);
    });
    config.getArch().ifPresent(v -> javaOptions.add(v == JVMArch.BIT_32 ? "-d32" : "-d64"));
    config.getType().ifPresent(v -> javaOptions.add(v == JVMType.CLIENT ? "-client" : "-server"));
    config.getInitHeapSizeMb().ifPresent(v -> javaOptions.add(String.format("-Xms%dm", v)));
    config.getMaxHeapSizeMb().ifPresent(v -> javaOptions.add(String.format("-Xmx%dm", v)));
    config.getStackSizeKb().ifPresent(v -> javaOptions.add(String.format("-Xss%dk", v)));
    String className = JavaProcess.class.getName();
    List<String> args = new ArrayList<>();
    args.add(javaCommand);
    args.addAll(javaOptions);
    args.add(className);
    ProcessBuilder builder = new ProcessBuilder(args);
    // Redirect the error stream to reduce the number of used threads per process.
    builder.redirectErrorStream(true);
    return builder;
  }

  @Override
  public ProcessManager newProcessManager() {
    ProcessBuilder builder = createProcessBuilder();
    return new JavaProcessManager<>(builder, initTask, wrapUpTask, keepAliveTime);
  }

}
