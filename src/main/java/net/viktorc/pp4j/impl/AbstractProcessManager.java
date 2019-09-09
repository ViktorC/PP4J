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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;
import net.viktorc.pp4j.api.FailedStartupException;
import net.viktorc.pp4j.api.ProcessManager;

/**
 * An abstract implementation of the {@link net.viktorc.pp4j.api.ProcessManager} interface.
 *
 * @author Viktor Csomor
 */
public abstract class AbstractProcessManager implements ProcessManager {

  private final ProcessBuilder builder;
  private final Charset charset;
  private final Long keepAliveTime;
  private final ProcessOutputStore startupOutputStore;

  /**
   * Constructs a manager for the processes created by the specified {@link java.lang.ProcessBuilder} with the specified maximum life span.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   */
  protected AbstractProcessManager(ProcessBuilder builder, Charset charset, Long keepAliveTime) {
    if (builder == null) {
      throw new IllegalArgumentException("Process builder cannot be null");
    }
    if (charset == null) {
      throw new IllegalArgumentException("Charset cannot be null");
    }
    this.builder = builder;
    this.charset = charset;
    this.keepAliveTime = keepAliveTime;
    startupOutputStore = new ProcessOutputStore();
  }

  /**
   * Returns the process output store containing the process' startup output.
   *
   * @return The output store used to capture to process' pre-startup output.
   */
  public ProcessOutputStore getStartupOutputStore() {
    return startupOutputStore;
  }

  /**
   * It determines whether the line output by the process denotes that it has successfully started up.
   *
   * @param outputLine The line output by the process.
   * @param error Whether the line was output to the process' standard error stream or its standard out stream.
   * @return Whether the output line denotes the startup of the process.
   * @throws FailedStartupException If the output line denotes that the startup of the process has failed.
   */
  protected abstract boolean isProcessStartedUp(String outputLine, boolean error) throws FailedStartupException;

  @Override
  public Process start() throws IOException {
    return builder.start();
  }

  @Override
  public Charset getEncoding() {
    return charset;
  }

  @Override
  public boolean isStartedUp(String outputLine, boolean error) throws FailedStartupException {
    try {
      return isProcessStartedUp(outputLine, error);
    } finally {
      startupOutputStore.storeOutput(outputLine, error);
    }
  }

  @Override
  public Optional<Long> getKeepAliveTime() {
    return Optional.ofNullable(keepAliveTime);
  }

  @Override
  public void reset() {
    startupOutputStore.clear();
  }

}