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
package net.viktorc.pp4j.api;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * An interface for the definition of the timeout of Java processes and the options for the "java" program to enable performance
 * optimization for the forking of JVMs.
 *
 * @author Viktor Csomor
 */
public interface JavaProcessOptions {

  /**
   * The simplest, no-override implementation of the interface using the default methods.
   */
  JavaProcessOptions DEFAULT = new JavaProcessOptions() { };

  /**
   * Returns the command for the Java application launcher which could be an absolute path to the executable or if it's under the system's
   * PATH environment variable, simply the <code>java</code> command.
   *
   * @return The command for the Java application launcher.
   */
  default String getJavaLauncherCommand() {
    return "java";
  }

  /**
   * Returns a set of paths to load class files from in the Java process, separated by the system's path separator.
   *
   * @return The class path to use in the JVM.
   */
  default Optional<String> getClassPath() {
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
          // Ignore it.
        }
      }
      classPathEntries.removeIf(String::isEmpty);
      classPath = String.join(File.pathSeparator, classPathEntries);
    }
    return Optional.ofNullable(classPath.isEmpty() ? null : classPath);
  }

  /**
   * Returns the architecture the JVM should use, i.e. 32-bit or 64-bit.
   *
   * @return The architecture of the JVM.
   */
  default Optional<JVMArch> getArch() {
    return Optional.ofNullable(null);
  }

  /**
   * Returns the type of the JVM, i.e. client or server.
   *
   * @return The type of the JVM.
   */
  default Optional<JVMType> getType() {
    return Optional.ofNullable(null);
  }

  /**
   * Returns the minimum and hence initial heap size the JVM should use in megabytes.
   *
   * @return The minimum heap size of the JVM in megabytes.
   */
  default Optional<Integer> getInitHeapSizeMb() {
    return Optional.ofNullable(null);
  }

  /**
   * Returns the maximum heap size the JVM should use in megabytes.
   *
   * @return The maximum heap size of the JVM in megabytes.
   */
  default Optional<Integer> getMaxHeapSizeMb() {
    return Optional.ofNullable(null);
  }

  /**
   * Return the max stack size the JVM should use in kilobytes.
   *
   * @return The max stack size of the JVM in kilobytes.
   */
  default Optional<Integer> getStackSizeKb() {
    return Optional.ofNullable(null);
  }

  /**
   * The definitions of the different possible JVM architectures.
   *
   * @author Viktor Csomor
   */
  enum JVMArch {
    BIT_32,
    BIT_64
  }

  /**
   * The definitions of the possible JVM types.
   *
   * @author Viktor Csomor
   */
  enum JVMType {
    CLIENT,
    SERVER
  }

}