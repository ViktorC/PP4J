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

  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleProcessExecutor.class);
  private static final String PROCESS_EXECUTOR_ALREADY_STARTING_OR_RUNNING_MESSAGE = "Process executor already starting or running";

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
   * @throws IllegalStateException If the process is already starting or running.
   */
  public void start() throws InterruptedException {
    if (startLock.tryLock()) {
      try {
        if (runLock.tryLock()) {
          try {
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
          return;
        }
      } finally {
        startLock.unlock();
      }
    }
    throw new IllegalStateException(PROCESS_EXECUTOR_ALREADY_STARTING_OR_RUNNING_MESSAGE);
  }

  /**
   * Waits until the process executor stops running.
   *
   * @throws InterruptedException If the thread is interrupted while waiting for the process executor to stop running.
   */
  public void waitFor() throws InterruptedException {
    startLock.lockInterruptibly();
    try {
      runLock.lockInterruptibly();
      runLock.unlock();
    } finally {
      startLock.unlock();
    }
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
    if (startLock.tryLock()) {
      try {
        if (runLock.tryLock()) {
          try {
            super.run();
            return;
          } finally {
            startupSemaphore.drainPermits();
            runLock.unlock();
          }
        }
      } finally {
        startLock.unlock();
      }
    }
    throw new IllegalStateException(PROCESS_EXECUTOR_ALREADY_STARTING_OR_RUNNING_MESSAGE);
  }

  @Override
  public void close() throws Exception {
    terminate();
    threadPool.shutdown();
    threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
  }

}
