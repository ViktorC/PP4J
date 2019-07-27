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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.ProcessExecutorService;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link ProcessExecutorService} interface for maintaining and managing a pool of pre-started processes. The
 * processes are executed in instances of an own {@link net.viktorc.pp4j.api.ProcessExecutor} implementation. Each executor is assigned an
 * instance of an implementation of the {@link net.viktorc.pp4j.api.ProcessManager} interface using an implementation of the
 * {@link ProcessManagerFactory} interface. The pool accepts submissions in the form of {@link Submission} implementations which are
 * executed in any one of the available active process executors maintained by the pool. While executing a submission, the executor cannot
 * accept further submissions. The submissions are queued and executed as soon as there is an available executor. The size of the pool is
 * always kept between the minimum pool size and the maximum pool size (both inclusive). The reserve size specifies the minimum number of
 * processes that should always be available (there are no guarantees that there actually will be this many available executors at any
 * given time). This class uses <a href="https://www.slf4j.org/">SLF4J</a> for logging.
 *
 * @author Viktor Csomor
 */
public class ProcessPoolExecutor implements ProcessExecutorService {

  /**
   * The number of milliseconds after which idle process executor instances and process executor threads are evicted from the object pool
   * and the thread pool respectively.
   */
  private static final long EVICT_TIME = 60L * 1000;

  private final ProcessManagerFactory processManagerFactory;
  private final int minPoolSize;
  private final int maxPoolSize;
  private final int reserveSize;
  private final InternalProcessExecutorThreadPool processExecutorThreadPool;
  private final ExecutorService secondaryThreadPool;
  private final Queue<InternalProcessExecutor> processExecutors;
  private final BlockingDeque<InternalSubmission<?>> submissionQueue;
  private final CountDownLatch poolInitLatch;
  private final CountDownLatch poolTerminationLatch;
  private final Object mainLock;
  private final Object forceShutdownLock;
  protected final Logger logger;

  private volatile int numOfSubmissions;
  private volatile boolean shutdown;

  /**
   * Constructs a pool of processes. The initial size of the pool is the minimum pool size or the reserve size depending on which one is
   * greater. This constructor blocks until the initial number of processes start up. The size of the pool is dynamically adjusted based on
   * the pool parameters and the rate of incoming submissions.
   *
   * @param processManagerFactory A {@link ProcessManagerFactory} instance that is used to build {@link net.viktorc.pp4j.api.ProcessManager}
   * instances that manage the processes' life cycle in the pool.
   * @param minPoolSize The minimum size of the process pool.
   * @param maxPoolSize The maximum size of the process pool.
   * @param reserveSize The number of available processes to keep in the pool.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   * @throws IllegalArgumentException If the manager factory is null, or the minimum pool size is less than 0, or the maximum pool size is
   * less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum pool size.
   */
  public ProcessPoolExecutor(ProcessManagerFactory processManagerFactory, int minPoolSize, int maxPoolSize, int reserveSize)
      throws InterruptedException {
    if (processManagerFactory == null) {
      throw new IllegalArgumentException("The process manager factory cannot be null");
    }
    if (minPoolSize < 0) {
      throw new IllegalArgumentException("The minimum pool size has to be greater than 0");
    }
    if (maxPoolSize < 1 || maxPoolSize < minPoolSize) {
      throw new IllegalArgumentException("The maximum pool size has to be at least 1 and at least as great as the minimum pool size");
    }
    if (reserveSize < 0 || reserveSize > maxPoolSize) {
      throw new IllegalArgumentException("The reserve has to be at least 0 and less than the maximum pool size");
    }
    this.processManagerFactory = processManagerFactory;
    this.minPoolSize = minPoolSize;
    this.maxPoolSize = maxPoolSize;
    this.reserveSize = reserveSize;
    processExecutorThreadPool = new InternalProcessExecutorThreadPool();
    int actualMinSize = Math.max(minPoolSize, reserveSize);
    /* One normal process requires minimum 2 secondary threads (stdout listener, submission handler), 3 if
     * the stderr is not redirected to stdout (stderr listener), and 4 if keepAliveTime is positive (timer). */
    secondaryThreadPool = new ThreadPoolExecutor(2 * actualMinSize, Integer.MAX_VALUE, EVICT_TIME, TimeUnit.MILLISECONDS,
        new SynchronousQueue<>(), new CustomizedThreadFactory(this + "-secondaryThreadPool"));
    submissionQueue = new LinkedBlockingDeque<>();
    processExecutors = new LinkedBlockingQueue<>();
    poolInitLatch = new CountDownLatch(actualMinSize);
    poolTerminationLatch = new CountDownLatch(1);
    mainLock = new Object();
    forceShutdownLock = new Object();
    logger = LoggerFactory.getLogger(getClass());
    synchronized (mainLock) {
      for (int i = 0; i < actualMinSize; i++) {
        startNewProcess();
      }
    }
    // Wait for the processes in the initial pool to start up.
    poolInitLatch.await();
    logger.debug("Pool started up");
  }

  /**
   * Returns the minimum number of processes to hold in the pool.
   *
   * @return The minimum size of the process pool.
   */
  public int getMinSize() {
    return minPoolSize;
  }

  /**
   * Returns the maximum allowed number of processes to hold in the pool.
   *
   * @return The maximum size of the process pool.
   */
  public int getMaxSize() {
    return maxPoolSize;
  }

  /**
   * Returns the minimum number of available processes to keep in the pool.
   *
   * @return The number of available processes to keep in the pool.
   */
  public int getReserveSize() {
    return reserveSize;
  }

  /**
   * Returns the number of running processes currently held in the pool.
   *
   * @return The number of running processes.
   */
  public int getNumOfProcesses() {
    return processExecutors.size();
  }

  /**
   * Returns the number of submissions currently being held in the queue or executed in the pool.
   *
   * @return The number of submissions currently handled by the pool.
   */
  public int getNumOfSubmissions() {
    return numOfSubmissions;
  }

  /**
   * Returns the number of active, queued, and currently executing processes as string.
   *
   * @return A string of statistics concerning the size of the process pool.
   */
  private String getPoolStats() {
    return "Processes: " + processExecutors.size() + "; submissions: " + numOfSubmissions;
  }

  /**
   * Returns whether a new {@link InternalProcessExecutor} instance should be started.
   *
   * @return Whether the process pool should be extended.
   */
  private boolean doExtendPool() {
    return !shutdown && (processExecutors.size() < minPoolSize ||
        (processExecutors.size() < Math.min(maxPoolSize, numOfSubmissions + reserveSize)));
  }

  /**
   * Starts a new process by creating a new {@link InternalProcessExecutor} instance and running it.
   */
  private void startNewProcess() {
    InternalProcessExecutor executor = new InternalProcessExecutor();
    processExecutorThreadPool.execute(executor);
    processExecutors.add(executor);
    logger.debug("Process executor {} started{}{}", executor, System.lineSeparator(), getPoolStats());
  }

  /**
   * Waits until all submissions are completed and calls {@link #syncForceShutdown()}.
   */
  private void syncShutdown() {
    logger.debug("Waiting for submissions to complete...");
    try {
      synchronized (mainLock) {
        while (numOfSubmissions > 0) {
          mainLock.wait();
        }
      }
    } catch (InterruptedException e) {
      logger.warn(e.getMessage(), e);
      Thread.currentThread().interrupt();
      return;
    }
    syncForceShutdown();
  }

  /**
   * Kills all the processes, shuts down the pool, and waits for termination.
   */
  private void syncForceShutdown() {
    synchronized (forceShutdownLock) {
      if (poolTerminationLatch.getCount() == 0) {
        logger.debug("Process pool had already terminated");
        return;
      }
      while (poolInitLatch.getCount() != 0) {
        poolInitLatch.countDown();
      }
      synchronized (mainLock) {
        logger.debug("Shutting down process executors...");
        for (InternalProcessExecutor executor : processExecutors) {
          executor.terminate();
        }
      }
      logger.debug("Shutting down thread pools...");
      secondaryThreadPool.shutdown();
      processExecutorThreadPool.shutdown();
      try {
        secondaryThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        processExecutorThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn(e.getMessage(), e);
      }
      synchronized (mainLock) {
        logger.debug("Setting remaining submission future exceptions...");
        for (InternalSubmission<?> submission : submissionQueue) {
          submission.setException(new ProcessException("The process pool has shut down"));
        }
      }
      poolTerminationLatch.countDown();
      logger.debug("Process pool terminated");
    }
  }

  @Override
  public ProcessManagerFactory getProcessManagerFactory() {
    return processManagerFactory;
  }

  @Override
  public boolean execute(Submission<?> submission) {
    try {
      submit(submission).get();
      return true;
    } catch (ExecutionException e) {
      throw new ProcessException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProcessException(e);
    }
  }

  @Override
  public <T> Future<T> submit(Submission<T> submission, boolean terminateProcessAfterwards) {
    synchronized (mainLock) {
      if (submission == null) {
        throw new IllegalArgumentException("The submission cannot be null or empty");
      }
      if (shutdown) {
        throw new RejectedExecutionException("The pool has already been shut down");
      }
      numOfSubmissions++;
      InternalSubmission<T> internalSubmission = new InternalSubmission<>(submission, terminateProcessAfterwards);
      submissionQueue.addLast(internalSubmission);
      // If necessary, adjust the pool size given the new submission.
      if (doExtendPool()) {
        startNewProcess();
      }
      logger.debug("Submission {} received{}{}", internalSubmission, System.lineSeparator(), getPoolStats());
      // Return a Future holding the total execution time including the submission delay.
      return new InternalSubmissionFuture<>(internalSubmission);
    }
  }

  @Override
  public void shutdown() {
    synchronized (mainLock) {
      if (!shutdown) {
        shutdown = true;
        (new Thread(this::syncShutdown)).start();
      }
    }
  }

  @Override
  public List<Submission<?>> forceShutdown() {
    synchronized (mainLock) {
      shutdown = true;
      List<Submission<?>> queuedSubmissions = new ArrayList<>();
      for (InternalSubmission<?> s : submissionQueue) {
        queuedSubmissions.add(s.origSubmission);
      }
      if (poolTerminationLatch.getCount() != 0) {
        (new Thread(this::syncForceShutdown)).start();
      }
      return queuedSubmissions;
    }
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }

  @Override
  public boolean isTerminated() {
    return poolTerminationLatch.getCount() == 0;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return poolTerminationLatch.await(timeout, unit);
  }

  @Override
  public String toString() {
    return String.format("processPoolExecutor@%s", Integer.toHexString(hashCode()));
  }

  /**
   * An implementation of the {@link Submission} interface for wrapping other instances of the interface into a class that allows for
   * waiting for the completion of the submission, the cancellation thereof, and the tracking of the time its processing took.
   *
   * @author Viktor Csomor
   */
  private class InternalSubmission<T> implements Submission<T> {

    final Submission<T> origSubmission;
    final boolean terminateProcessAfterwards;
    final long receivedTime;
    final Object lock;
    Thread thread;
    Exception exception;
    volatile long submittedTime;
    volatile long processedTime;
    volatile boolean processed;
    volatile boolean cancelled;

    /**
     * Constructs an instance according to the specified parameters.
     *
     * @param originalSubmission The submission to wrap into an internal submission with extended features.
     * @throws IllegalArgumentException If the submission is null.
     */
    InternalSubmission(Submission<T> originalSubmission, boolean terminateProcessAfterwards) {
      if (originalSubmission == null) {
        throw new IllegalArgumentException("The submission cannot be null");
      }
      this.origSubmission = originalSubmission;
      this.terminateProcessAfterwards = terminateProcessAfterwards;
      receivedTime = System.nanoTime();
      lock = new Object();
    }

    /**
     * Sets the thread that is executing the submission.
     *
     * @param t The thread that executes the submission.
     */
    void setThread(Thread t) {
      synchronized (lock) {
        thread = t;
      }
    }

    /**
     * Sets the exception thrown during the execution of the submission if there was any.
     *
     * @param e The exception thrown during the execution of the submission.
     */
    void setException(Exception e) {
      // Notify the future associated with the submission that an exception was thrown while processing the submission.
      synchronized (lock) {
        exception = e;
        lock.notifyAll();
      }
    }

    /**
     * Returns whether the <code>cancelled</code> flag of the submission has been set to true.
     *
     * @return Whether the submission has been cancelled.
     */
    boolean isCancelled() {
      synchronized (lock) {
        return cancelled;
      }
    }

    /**
     * Sets the <code>cancelled</code> flag of the submission to true.
     */
    void cancel() {
      synchronized (lock) {
        cancelled = true;
        lock.notifyAll();
      }
    }

    @Override
    public List<Command> getCommands() {
      return origSubmission.getCommands();
    }

    @Override
    public Optional<T> getResult() throws ExecutionException {
      return origSubmission.getResult();
    }

    @Override
    public void onStartedProcessing() {
      // If it is the first time the submission is submitted to a process...
      if (submittedTime == 0) {
        submittedTime = System.nanoTime();
        origSubmission.onStartedProcessing();
      }
    }

    @Override
    public void onFinishedProcessing() {
      origSubmission.onFinishedProcessing();
      processedTime = System.nanoTime();
      // Notify the future associated with the submission that the submission has been processed.
      synchronized (lock) {
        processed = true;
        lock.notifyAll();
      }
    }

    @Override
    public String toString() {
      return String.format("%s@%s", origSubmission, Integer.toHexString(hashCode()));
    }

  }

  /**
   * An implementation of {@link Future} that can be used to cancel, wait on, and retrieve the return value of an
   * {@link InternalSubmission} instance.
   *
   * @author Viktor Csomor
   */
  private class InternalSubmissionFuture<T> implements Future<T> {

    final InternalSubmission<T> submission;

    /**
     * Constructs a {@link Future} for the specified submission.
     *
     * @param submission The submission to get a {@link Future} for.
     */
    InternalSubmissionFuture(InternalSubmission<T> submission) {
      this.submission = submission;
    }

    /**
     * Returns the result of the submission or throws the appropriate exception.
     *
     * @return The result of the submission.
     * @throws ExecutionException If an exception was thrown during the execution of the submission.
     */
    private T getResult() throws ExecutionException {
      if (submission.cancelled) {
        throw new CancellationException(String.format("Submission %s cancelled", submission));
      }
      if (submission.exception != null) {
        throw new ExecutionException(submission.exception);
      }
      return submission.getResult().orElse(null);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      synchronized (submission.lock) {
        /* If the submission has already been cancelled or if it has already been processed, don't do
         * anything and return false. */
        if (submission.cancelled || submission.processed) {
          return false;
        }
        /* If it is already being processed and mayInterruptIfRunning is true, interrupt the executor
         * thread. */
        if (submission.thread != null) {
          if (mayInterruptIfRunning) {
            submission.cancel();
            submission.thread.interrupt();
          }
          // If mayInterruptIfRunning is false, don't let the submission be cancelled.
        } else {
          // If the processing of the submission has not commenced yet, cancel it.
          submission.cancel();
        }
        return submission.cancelled;
      }
    }

    @Override
    public T get() throws InterruptedException, ExecutionException, CancellationException {
      // Wait until the submission is processed, or cancelled, or fails.
      synchronized (submission.lock) {
        while (!submission.processed && !submission.cancelled && submission.exception == null) {
          submission.lock.wait();
        }
        return getResult();
      }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
      // Wait until the submission is processed, or cancelled, or fails, or the method times out.
      synchronized (submission.lock) {
        long timeoutNs = unit.toNanos(timeout);
        long start = System.nanoTime();
        while (!submission.processed && !submission.cancelled && submission.exception == null && timeoutNs > 0) {
          submission.lock.wait(timeoutNs / 1000000, (int) (timeoutNs % 1000000));
          timeoutNs -= (System.nanoTime() - start);
        }
        T result = getResult();
        if (timeoutNs <= 0) {
          throw new TimeoutException(String.format("Submission %s timed out", submission));
        }
        return result;
      }
    }

    @Override
    public boolean isCancelled() {
      return submission.cancelled;
    }

    @Override
    public boolean isDone() {
      return submission.processed;
    }

  }

  /**
   * A sub-class of {@link AbstractProcessExecutor} that utilizes an additional thread to listen to the submission queue of the process
   * pool and take submissions for executions.
   *
   * @author Viktor Csomor
   */
  private class InternalProcessExecutor extends AbstractProcessExecutor {

    Thread submissionThread;

    /**
     * Constructs an instances using a newly created process manager and the <code>secondaryThreadPool</code>.
     */
    InternalProcessExecutor() {
      super(processManagerFactory.newProcessManager(), secondaryThreadPool);
    }

    /**
     * Updates the process pool and its submission queue based on the outcome of the attempt to execute the submission.
     *
     * @param submission The submission on which the attempt was made or null if no submission was retrieved.
     * @param submissionDone Whether the submission can be considered completed, even if that is due to cancellation or a fatal error.
     */
    void updateSubmissionQueue(InternalSubmission<?> submission, boolean submissionDone) {
      if (submission != null) {
        synchronized (mainLock) {
          if (submissionDone) {
            numOfSubmissions--;
            logger.debug(String.format("Submission %s processed; delay: %.3f; execution time: %.3f.%n%s",
                submission,
                (submission.submittedTime - submission.receivedTime) / 1000000000d,
                (submission.processedTime - submission.submittedTime) / 1000000000d,
                getPoolStats()));
            // If the pool is shutdown and there are no more submissions left, signal it.
            if (shutdown && numOfSubmissions == 0) {
              mainLock.notifyAll();
            }
          } else {
            // If the execute method failed and there was no exception thrown, put the submission back into the queue at the front.
            submission.setThread(null);
            submissionQueue.addFirst(submission);
            logger.trace("Submission put back in queue");
          }
        }
      }
    }

    /**
     * Takes a submission of the queue, waiting until there is one available, and attempts to execute it. If the submission's execution is
     * unsuccessful or interrupted without any other exceptions thrown, the submission is put back into the queue. If an exception is
     * thrown during its execution, the submission is deemed completed and the process is terminated.
     */
    void takeAndExecuteSubmission() {
      InternalSubmission<?> submission;
      boolean submissionDone = false;
      try {
        submission = submissionQueue.takeFirst();
        logger.trace("Submission {} taken off queue", submission);
      } catch (InterruptedException e) {
        logger.trace("Thread interrupted while waiting for submission", e);
        return;
      }
      try {
        submission.setThread(submissionThread);
        submissionDone = !submission.isCancelled() && execute(submission, submission.terminateProcessAfterwards);
      } catch (Exception e) {
        submission.setException(e);
        logger.trace("Exception while executing submission", e);
        submissionDone = true;
        terminateForcibly();
      } finally {
        if (submission.isCancelled()) {
          logger.trace("Submission cancelled");
          submissionDone = true;
        }
        updateSubmissionQueue(submission, submissionDone);
      }
    }

    /**
     * Waits on the blocking queue of submissions executing available ones one at a time.
     */
    void startHandlingSubmissions() {
      numOfChildThreads.incrementAndGet();
      synchronized (stateLock) {
        submissionThread = Thread.currentThread();
      }
      try {
        while (isAlive()) {
          takeAndExecuteSubmission();
        }
      } finally {
        synchronized (stateLock) {
          submissionThread = null;
        }
        terminationSemaphore.release();
      }
    }

    @Override
    protected void onExecutorStartup() {
      threadPool.submit(this::startHandlingSubmissions);
      poolInitLatch.countDown();
    }

    @Override
    protected void onExecutorTermination() {
      /* Interrupt the submission handler thread to avoid having it stuck waiting for
       * submissions forever in case the queue is empty. */
      synchronized (stateLock) {
        if (submissionThread != null) {
          submissionThread.interrupt();
        }
      }
    }

    @Override
    public String toString() {
      return String.format("%s-intProcExecutor@%s", ProcessPoolExecutor.this, Integer.toHexString(hashCode()));
    }

  }

  /**
   * An implementation the {@link ThreadFactory} interface that provides more descriptive thread  names and extends the
   * {@link Thread.UncaughtExceptionHandler} of the created threads by logging the uncaught exceptions if the enclosing
   * {@link ProcessPoolExecutor} instance is verbose. It also attempts to shut down the enclosing pool if an exception is thrown in one of
   * the threads it created.
   *
   * @author Viktor Csomor
   */
  private class CustomizedThreadFactory implements ThreadFactory {

    final String poolName;
    final ThreadFactory defaultFactory;

    /**
     * Constructs an instance according to the specified parameters.
     *
     * @param poolName The name of the thread pool. It will be prepended to the name of the created threads.
     */
    CustomizedThreadFactory(String poolName) {
      this.poolName = poolName;
      defaultFactory = Executors.defaultThreadFactory();
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = defaultFactory.newThread(r);
      thread.setName(thread.getName().replaceFirst("pool-[0-9]+", poolName));
      thread.setUncaughtExceptionHandler((t, e) -> {
        logger.error(e.getMessage(), e);
        ProcessPoolExecutor.this.forceShutdown();
      });
      return thread;
    }

  }

  /**
   * A sub-class of {@link ThreadPoolExecutor} for the execution of {@link InternalProcessExecutor} instances. It utilizes an extension of
   * the {@link LinkedTransferQueue} and an implementation of the {@link java.util.concurrent.RejectedExecutionHandler} as per Robert
   * Tupelo-Schneck's <a href="https://stackoverflow.com/a/24493856">answer</a> to a StackOverflow question to facilitate a queueing logic
   * that has the pool first increase the number of its threads and only really queue tasks once the maximum pool size has been reached.
   *
   * @author Viktor Csomor
   */
  private class InternalProcessExecutorThreadPool extends ThreadPoolExecutor {

    /**
     * Constructs thread pool for the execution of {@link InternalProcessExecutor} instances. If there are more than
     * <code>Math.max(minPoolSize, reserveSize)</code> idle threads in the pool, excess threads are evicted after <code>EVICT_TIME</code>
     * milliseconds.
     */
    InternalProcessExecutorThreadPool() {
      super(Math.max(minPoolSize, reserveSize), maxPoolSize, EVICT_TIME, TimeUnit.MILLISECONDS,
          new LinkedTransferQueue<Runnable>() {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean offer(Runnable r) {
              /* If there is at least one thread waiting on the queue, delegate the task immediately;
               * else decline it and force the pool to create a new thread for running the task. */
              return tryTransfer(r);
            }
          },
          new CustomizedThreadFactory(ProcessPoolExecutor.this + "-processExecutorThreadPool"),
          (runnable, executor) -> {
            try {
              /* If there are no threads waiting on the queue (all of them are busy executing)
               * and the maximum pool size has been reached, when the queue declines the offer,
               * the pool will not create any more threads but call this handler instead. This
               * handler 'forces' the declined task on the queue, ensuring that it is not rejected. */
              executor.getQueue().put(runnable);
            } catch (InterruptedException e) {
              // Should not happen.
              Thread.currentThread().interrupt();
            }
          });
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      super.afterExecute(r, t);
      InternalProcessExecutor executor = (InternalProcessExecutor) r;
      // A process has terminated. Extend the pool if necessary by using the pooled executors.
      synchronized (mainLock) {
        processExecutors.remove(executor);
        logger.debug("Process executor {} stopped{}{}", executor, System.lineSeparator(), getPoolStats());
        if (doExtendPool()) {
          startNewProcess();
        }
      }
    }

  }

}