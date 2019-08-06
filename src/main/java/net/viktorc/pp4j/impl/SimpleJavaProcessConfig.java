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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.viktorc.pp4j.api.JavaProcessConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple implementation of the {@link JavaProcessConfig} interface for the definition of JVM options.
 *
 * @author Viktor Csomor
 */
public class SimpleJavaProcessConfig implements JavaProcessConfig {

  private static final String DEFAULT_JAVA_LAUNCHER_COMMAND = "java";
  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleJavaProcessConfig.class);

  private final String javaLauncherCommand;
  private final JVMArch arch;
  private final JVMType type;
  private final Integer initHeapSizeMb;
  private final Integer maxHeapSizeMb;
  private final Integer stackSizeKb;
  private final String additionalClassPath;

  /**
   * Constructs a <code>SimpleJavaProcessConfig</code> instance according to the specified parameters.
   *
   * @param javaLauncherCommand The command for the Java application launcher.
   * @param arch The architecture of the JVM. If it is null, it will be ignored.
   * @param type The type of the JVM. If it is null, it will be ignored.
   * @param initHeapSizeMb The initial heap size of the JVM in megabytes. If it is null, it will be ignored.
   * @param maxHeapSizeMb The maximum heap size of the JVM in megabytes. If it is null, it will be ignored.
   * @param stackSizeKb The maximum stack size of the JVM in kilobytes. If it is null, it will be ignored.
   * @param additionalClassPaths Any additional class paths to be used by the JVM. Null entries and empty strings will be ignored.
   */
  public SimpleJavaProcessConfig(String javaLauncherCommand, JVMArch arch, JVMType type, Integer initHeapSizeMb, Integer maxHeapSizeMb,
      Integer stackSizeKb, String... additionalClassPaths) {
    this.javaLauncherCommand = javaLauncherCommand;
    this.arch = arch;
    this.type = type;
    this.initHeapSizeMb = initHeapSizeMb;
    this.maxHeapSizeMb = maxHeapSizeMb;
    this.stackSizeKb = stackSizeKb;
    List<String> additionalClassPathList = Arrays.stream(additionalClassPaths)
        .filter(s -> s != null && !s.isEmpty())
        .collect(Collectors.toList());
    additionalClassPath = additionalClassPathList.isEmpty() ? null : String.join(File.pathSeparator, additionalClassPathList);
  }

  /**
   * Constructs a <code>SimpleJavaProcessConfig</code> instance according to the specified parameters.
   *
   * @param arch The architecture of the JVM. If it is null, it will be ignored.
   * @param type The type of the JVM. If it is null, it will be ignored.
   * @param initHeapSizeMb The initial heap size of the JVM in megabytes. If it is null, it will be ignored.
   * @param maxHeapSizeMb The maximum heap size of the JVM in megabytes. If it is null, it will be ignored.
   * @param stackSizeKb The maximum stack size of the JVM in kilobytes. If it is null, it will be ignored.
   */
  public SimpleJavaProcessConfig(JVMArch arch, JVMType type, Integer initHeapSizeMb, Integer maxHeapSizeMb, Integer stackSizeKb) {
    this(DEFAULT_JAVA_LAUNCHER_COMMAND, arch, type, initHeapSizeMb, maxHeapSizeMb, stackSizeKb);
  }

  /**
   * Constructs a <code>SimpleJavaProcessConfig</code> instance according to the specified parameters.
   *
   * @param initHeapSizeMb The initial heap size of the JVM in megabytes. If it is null, it will be ignored.
   * @param maxHeapSizeMb The maximum heap size of the JVM in megabytes. If it is null, it will be ignored.
   * @param stackSizeKb The maximum stack size of the JVM in kilobytes. If it is null, it will be ignored.
   */
  public SimpleJavaProcessConfig(Integer initHeapSizeMb, Integer maxHeapSizeMb, Integer stackSizeKb) {
    this(null, null, initHeapSizeMb, maxHeapSizeMb, stackSizeKb);
  }

  /**
   * Constructs a <code>SimpleJavaProcessConfig</code> instance according to the specified parameters.
   *
   * @param javaLauncherCommand The command for the Java application launcher.
   * @param additionalClassPaths Any additional class paths to be used by the JVM. Null entries and empty strings will be ignored.
   */
  public SimpleJavaProcessConfig(String javaLauncherCommand, String... additionalClassPaths) {
    this(javaLauncherCommand, null, null, null, null, null, additionalClassPaths);
  }

  /**
   * Constructs a <code>SimpleJavaProcessConfig</code> instance using the default parameters.
   */
  public SimpleJavaProcessConfig() {
    this(DEFAULT_JAVA_LAUNCHER_COMMAND);
  }

  /**
   * It attempts the resolve the default Java class path to use in the new Java process.
   *
   * @return The default Java class path to use.
   */
  private Optional<String> getDefaultClassPath() {
    String classPath = System.getProperty("java.class.path");
    if (classPath == null) {
      classPath = "";
    }
    ClassLoader classLoader = this.getClass().getClassLoader();
    if (classLoader instanceof URLClassLoader) {
      @SuppressWarnings("resource")
      URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
      Set<String> classPathEntries = new HashSet<>(Arrays.asList(classPath.split(File.pathSeparator)));
      for (URL url : urlClassLoader.getURLs()) {
        try {
          classPathEntries.add(Paths.get(url.toURI()).toAbsolutePath().toString());
        } catch (FileSystemNotFoundException | URISyntaxException e) {
          LOGGER.warn(e.getMessage(), e);
        }
      }
      classPathEntries.removeIf(String::isEmpty);
      classPath = String.join(File.pathSeparator, classPathEntries);
    }
    return Optional.ofNullable(classPath.isEmpty() ? null : classPath);
  }

  @Override
  public String getJavaLauncherCommand() {
    return javaLauncherCommand;
  }

  @Override
  public Optional<String> getClassPath() {
    List<String> classPaths = new ArrayList<>();
    getDefaultClassPath().ifPresent(classPaths::add);
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
