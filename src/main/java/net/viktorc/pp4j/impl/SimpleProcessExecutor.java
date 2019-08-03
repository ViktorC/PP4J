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

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.Submission;

/**
 * An implementation of the {@link net.viktorc.pp4j.api.ProcessExecutor} interface that allows for the running and management of a process
 * based on a {@link ProcessManager} instance to enable the execution of submissions in this process. The process may be launched by
 * invoking the {@link #start()} method and submissions may be executed in the process using the {@link #execute(Submission)} method.
 *
 * @author Viktor Csomor
 */
public class SimpleProcessExecutor extends AbstractProcessExecutor implements AutoCloseable {

  /**
   * Constructs a process executor instance using the argument to manage the life-cycle of the process.
   *
   * @param manager The manager of the underlying process.
   */
  public SimpleProcessExecutor(ProcessManager manager) {
    super(manager, Executors.newCachedThreadPool());
  }

  /**
   * It launches the underlying process, blocks until it is ready for the execution of submissions, and it returns a {@link Future}
   * instance that can be used to wait for the termination of the process.
   *
   * @return A <code>Future</code> instance to monitor the life cycle of the process executor.
   * @throws InterruptedException If the thread is interrupted while waiting for the startup to complete.
   * @throws IllegalStateException If the process is already running.
   */
  public Future<?> start() throws InterruptedException {
    synchronized (stateLock) {
      if (isAlive()) {
        throw new IllegalStateException("The executor is already running");
      }
      Future<?> future = threadPool.submit(this);
      while (!isAlive()) {
        stateLock.wait();
      }
      return future;
    }
  }

  @Override
  protected void onExecutorStartup() {
    synchronized (stateLock) {
      stateLock.notifyAll();
    }
  }

  @Override
  protected void onExecutorTermination() {
  }

  @Override
  public void close() throws Exception {
    terminateForcibly();
    threadPool.shutdown();
    threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
  }

}
