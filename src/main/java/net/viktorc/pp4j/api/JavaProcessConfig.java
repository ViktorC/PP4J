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

import java.util.Optional;

/**
 * An interface for the definition of the Java processes launcher and some of the JVM options.
 *
 * @author Viktor Csomor
 */
public interface JavaProcessConfig {

  /**
   * Returns the command for the Java application launcher which could be an absolute path to the executable or if it's under the system's
   * PATH environment variable, simply the <code>java</code> command.
   *
   * @return The command for the Java application launcher.
   */
  String getJavaLauncherCommand();

  /**
   * Returns a set of paths to load class files from in the Java process, separated by the system's path separator.
   *
   * @return The class path to use in the JVM.
   */
  Optional<String> getClassPath();

  /**
   * Returns the architecture the JVM should use, i.e. 32-bit or 64-bit.
   *
   * @return The architecture of the JVM.
   */
  Optional<JVMArch> getArch();

  /**
   * Returns the type of the JVM, i.e. client or server.
   *
   * @return The type of the JVM.
   */
  Optional<JVMType> getType();

  /**
   * Returns the minimum and hence initial heap size the JVM should use in megabytes.
   *
   * @return The minimum heap size of the JVM in megabytes.
   */
  Optional<Integer> getInitHeapSizeMb();

  /**
   * Returns the maximum heap size the JVM should use in megabytes.
   *
   * @return The maximum heap size of the JVM in megabytes.
   */
  Optional<Integer> getMaxHeapSizeMb();

  /**
   * Return the max stack size the JVM should use in kilobytes.
   *
   * @return The max stack size of the JVM in kilobytes.
   */
  Optional<Integer> getStackSizeKb();

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