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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link net.viktorc.pp4j.api.ProcessExecutor} interface that allows for the running and management of a process
 * based on a {@link ProcessManager} instance to enable the execution of submissions in this process. The process may be launched by
 * invoking the {@link #start()} method and submissions may be executed in the process using the {@link #execute(Submission)} method.
 *
 * @author Viktor Csomor
 */
public class SimpleProcessExecutor extends AbstractProcessExecutor implements AutoCloseable {

  private static final String PROCESS_EXECUTOR_ALREADY_STARTED_OR_RUNNING_MESSAGE = "Process executor already started or running";
  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleProcessExecutor.class);

  private final Lock startLock;
  private final Semaphore startupSemaphore;

  /**
   * Constructs a process executor instance using the argument to manage the life-cycle of the process.
   *
   * @param manager The manager of the underlying process.
   */
  public SimpleProcessExecutor(ProcessManager manager) {
    super(manager, Executors.newCachedThreadPool(r -> {
      Thread thread = new Thread(r);
      thread.setUncaughtExceptionHandler((t, e) -> LOGGER.error(e.getMessage(), e));
      return thread;
    }));
    startLock = new ReentrantLock(true);
    startupSemaphore = new Semaphore(0);
  }

  /**
   * It starts the process executor and waits until it is running and set up.
   *
   * @throws InterruptedException If the thread is interrupted while waiting for the process executor to start up.
   * @throws IllegalStateException If the process is already started or running.
   */
  public void start() throws InterruptedException {
    LOGGER.trace("Attempting to start process executor...");
    if (startLock.tryLock()) {
      try {
        if (runLock.tryLock()) {
          try {
            LOGGER.trace("Starting process executor...");
            threadPool.execute(() -> {
              runLock.lock();
              try {
                super.run();
              } finally {
                startupSemaphore.drainPermits();
                runLock.unlock();
              }
            });
          } finally {
            runLock.unlock();
          }
          startupSemaphore.acquire(1);
          LOGGER.trace("Process executor started up and running");
          return;
        }
      } finally {
        startLock.unlock();
      }
    }
    throw new IllegalStateException(PROCESS_EXECUTOR_ALREADY_STARTED_OR_RUNNING_MESSAGE);
  }

  /**
   * Waits until the process executor stops running.
   *
   * @throws InterruptedException If the thread is interrupted while waiting for the process executor to stop running.
   */
  public void waitFor() throws InterruptedException {
    LOGGER.trace("Waiting for process executor to stop running");
    startLock.lockInterruptibly();
    try {
      runLock.lockInterruptibly();
      LOGGER.trace("Process executor not running");
      runLock.unlock();
    } finally {
      startLock.unlock();
    }
  }

  @Override
  protected Map<String, ThrowingRunnable> getAdditionalChildThreads() {
    return Collections.emptyMap();
  }

  @Override
  protected void onExecutorStartup() {
    startupSemaphore.release();
  }

  @Override
  protected void onExecutorTermination() {
  }

  @Override
  public void run() {
    LOGGER.trace("Attempting to run process executor...");
    if (startLock.tryLock()) {
      try {
        if (runLock.tryLock()) {
          try {
            LOGGER.trace("Running process executor...");
            super.run();
            return;
          } finally {
            startupSemaphore.drainPermits();
            runLock.unlock();
            LOGGER.trace("Process executor finished running");
          }
        }
      } finally {
        startLock.unlock();
      }
    }
    throw new IllegalStateException(PROCESS_EXECUTOR_ALREADY_STARTED_OR_RUNNING_MESSAGE);
  }

  @Override
  public void close() throws Exception {
    LOGGER.trace("Closing process executor...");
    terminate();
    LOGGER.trace("Shutting down thread pool...");
    threadPool.shutdown();
    threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    LOGGER.trace("Process executor closed");
  }

}
