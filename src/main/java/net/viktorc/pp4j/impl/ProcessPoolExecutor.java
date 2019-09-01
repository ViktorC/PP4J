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
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import net.viktorc.pp4j.api.DisruptedExecutionException;
import net.viktorc.pp4j.api.FailedCommandException;
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

  private static final long DEFAULT_THREAD_KEEP_ALIVE_TIME = 60L * 1000L;
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessPoolExecutor.class);

  private final ProcessManagerFactory processManagerFactory;
  private final int minPoolSize;
  private final int maxPoolSize;
  private final int reserveSize;
  private final long threadKeepAliveTime;
  private final InternalProcessExecutorThreadPool processExecutorThreadPool;
  private final ExecutorService secondaryThreadPool;
  private final Queue<InternalProcessExecutor> processExecutors;
  private final BlockingDeque<InternalSubmission<?>> submissionQueue;
  private final CountDownLatch poolInitLatch;
  private final CountDownLatch poolTerminationLatch;
  private final Object mainLock;

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
   * @param threadKeepAliveTime The number of milliseconds of idleness after which threads are terminated if the sizes of the thread pools
   * exceed their core sizes.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   * @throws IllegalArgumentException If the manager factory is null, or the minimum pool size is less than 0, or the maximum pool size is
   * less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum pool size.
   */
  public ProcessPoolExecutor(ProcessManagerFactory processManagerFactory, int minPoolSize, int maxPoolSize, int reserveSize,
      long threadKeepAliveTime) throws InterruptedException {
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
    if (threadKeepAliveTime <= 0) {
      throw new IllegalArgumentException("The thread keep-alive time must be greater than 0");
    }
    this.processManagerFactory = processManagerFactory;
    this.minPoolSize = minPoolSize;
    this.maxPoolSize = maxPoolSize;
    this.reserveSize = reserveSize;
    this.threadKeepAliveTime = threadKeepAliveTime;
    processExecutorThreadPool = new InternalProcessExecutorThreadPool();
    int actualMinSize = Math.max(minPoolSize, reserveSize);
    /* One normal process requires minimum 2 secondary threads (stdout listener, submission handler), 3 if
     * the stderr is not redirected to stdout (stderr listener), and 4 if keepAliveTime is positive (timer). */
    secondaryThreadPool = new ThreadPoolExecutor(2 * actualMinSize, Integer.MAX_VALUE, threadKeepAliveTime,
        TimeUnit.MILLISECONDS, new SynchronousQueue<>(), new CustomizedThreadFactory(this + "-secondaryThreadPool"));
    submissionQueue = new LinkedBlockingDeque<>();
    processExecutors = new LinkedBlockingQueue<>();
    poolInitLatch = new CountDownLatch(actualMinSize);
    poolTerminationLatch = new CountDownLatch(1);
    mainLock = new Object();
    try {
      startPool(actualMinSize);
    } catch (InterruptedException e) {
      processExecutorThreadPool.shutdownNow();
      secondaryThreadPool.shutdownNow();
      LOGGER.debug("Internal thread pools shut down due to constructor interruption");
      throw e;
    }
  }

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
    this(processManagerFactory, minPoolSize, maxPoolSize, reserveSize, DEFAULT_THREAD_KEEP_ALIVE_TIME);
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
   * Returns the duration of idleness in milliseconds after which excess threads are terminated.
   *
   * @return The number of milliseconds of idleness after which threads are terminated if the sizes of the thread pools exceed their core
   * sizes.
   */
  public long getThreadKeepAliveTime() {
    return threadKeepAliveTime;
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
   * Returns whether a new {@link InternalProcessExecutor} instance should be started and added to the pool.
   *
   * @return Whether the process pool should be extended.
   */
  private boolean doExtendPool() {
    return (!shutdown || !submissionQueue.isEmpty()) &&
        (processExecutors.size() < minPoolSize || (processExecutors.size() < Math.min(maxPoolSize, numOfSubmissions + reserveSize)));
  }

  /**
   * Starts a new process by creating a new {@link InternalProcessExecutor} instance and running it.
   */
  private void startNewProcess() {
    InternalProcessExecutor executor = new InternalProcessExecutor();
    processExecutorThreadPool.execute(executor);
    processExecutors.add(executor);
    LOGGER.debug("Process executor {} started", executor);
    LOGGER.debug(getPoolStats());
  }

  /**
   * It start up the specified number of process executors and blocks until they are ready for submissions.
   *
   * @param actualMinSize The initial number of process executors to have in the pool.
   * @throws InterruptedException If the current thread is interrupted while waiting for the processes to start up.
   */
  private void startPool(int actualMinSize) throws InterruptedException {
    LOGGER.debug("Starting up process pool...");
    synchronized (mainLock) {
      for (int i = 0; i < actualMinSize; i++) {
        startNewProcess();
      }
    }
    poolInitLatch.await();
    LOGGER.debug("Pool started up");
  }

  /**
   * Waits until all submissions are completed, terminates the process executors, ands shuts down the thread pools.
   */
  private void syncShutdown() {
    synchronized (mainLock) {
      try {
        LOGGER.debug("Waiting for remaining submissions to complete...");
        while (numOfSubmissions > 0) {
          mainLock.wait();
        }
      } catch (InterruptedException e) {
        LOGGER.warn(e.getMessage(), e);
        Thread.currentThread().interrupt();
        return;
      }
      LOGGER.debug("Terminating process executors...");
      for (InternalProcessExecutor executor : processExecutors) {
        executor.terminate();
      }
    }
    LOGGER.debug("Shutting down thread pools...");
    secondaryThreadPool.shutdown();
    processExecutorThreadPool.shutdown();
    try {
      secondaryThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
      processExecutorThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    } catch (InterruptedException e) {
      LOGGER.warn(e.getMessage(), e);
      Thread.currentThread().interrupt();
    }
    while (poolInitLatch.getCount() != 0) {
      poolInitLatch.countDown();
    }
    poolTerminationLatch.countDown();
    LOGGER.debug("Process pool terminated");
  }

  @Override
  public ProcessManagerFactory getProcessManagerFactory() {
    return processManagerFactory;
  }

  @Override
  public void execute(Submission<?> submission) throws FailedCommandException, DisruptedExecutionException {
    Future<?> future = submit(submission);
    try {
      future.get();
    } catch (ExecutionException e) {
      throw (FailedCommandException) e.getCause();
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new DisruptedExecutionException(e);
    }
  }

  @Override
  public <T> Future<T> submit(Submission<T> submission, boolean terminateProcessAfterwards) {
    synchronized (mainLock) {
      if (submission == null) {
        throw new IllegalArgumentException("The submission cannot be null");
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
      LOGGER.debug("Submission {} received", internalSubmission);
      LOGGER.debug(getPoolStats());
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
      List<Submission<?>> queuedSubmissions = new ArrayList<>();
      for (InternalSubmission<?> submission : submissionQueue) {
        queuedSubmissions.add(submission.getOrigSubmission());
      }
      numOfSubmissions -= submissionQueue.size();
      submissionQueue.clear();
      shutdown();
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
   * @param <T> The return type associated with the submission.
   * @author Viktor Csomor
   */
  private static class InternalSubmission<T> implements Submission<T> {

    private final Submission<T> origSubmission;
    private final boolean terminateProcessAfterwards;
    private final long receivedTime;
    private final Object lock;

    private Thread thread;
    private Exception exception;
    private boolean processed;
    private boolean cancelled;

    private volatile long submittedTime;
    private volatile long processedTime;

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
     * Returns the original submission instance wrapped by this internal submission.
     *
     * @return The original submission instance.
     */
    Submission<T> getOrigSubmission() {
      return origSubmission;
    }

    /**
     * Returns whether the process executing the submission should be terminated afterwards.
     *
     * @return Whether the executing process should be terminated afterwards.
     */
    boolean getTerminateProcessAfterwards() {
      return terminateProcessAfterwards;
    }

    /**
     * Returns the internal state lock of the submission.
     *
     * @return The internal lock.
     */
    Object getLock() {
      return lock;
    }

    /**
     * Returns the system time at the construction of this submission wrapper instance.
     *
     * @return The time the wrapper submission was constructed in milliseconds.
     */
    long getReceivedTime() {
      return receivedTime;
    }

    /**
     * Returns the system time at the first delegation of this submission to a process executor. If the submission has not been delegated
     * yet, it returns <code>0</code>.
     *
     * @return The time the wrapper submission was delegated to a process executor in milliseconds.
     */
    long getSubmittedTime() {
      return submittedTime;
    }

    /**
     * Returns the system time at the completion of the execution of this submission. If the submission has not been processed yet, it
     * returns <code>0</code>.
     *
     * @return The time the submission's execution was completed in milliseconds.
     */
    long getProcessedTime() {
      return processedTime;
    }

    /**
     * Returns the thread executing the submission or <code>null</code> if its execution has not begun yet.
     *
     * @return The thread executing the submission.
     */
    Thread getThread() {
      synchronized (lock) {
        return thread;
      }
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
     * Returns the exception thrown during the execution of the submission if there was one.
     *
     * @return The exception thrown during the execution of the submission or <code>null</code> if there was not one.
     */
    Exception getException() {
      synchronized (lock) {
        return exception;
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
     * Returns whether the submission has been fully executed.
     *
     * @return Whether submission has been processed.
     */
    boolean isProcessed() {
      synchronized (lock) {
        return processed;
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
     * Returns whether the submission can be considered done. The submission is considered done if it has completed execution, has been
     * cancelled, or has failed.
     *
     * @return Whether the submission is done.
     */
    boolean isDone() {
      synchronized (lock) {
        return processed || cancelled || exception != null;
      }
    }

    /**
     * Sets the <code>cancelled</code> flag of the submission to <code>true</code>.
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
    public Optional<T> getResult() {
      return origSubmission.getResult();
    }

    @Override
    public void onStartedExecution() {
      // If it is the first time the submission is submitted to a process...
      if (submittedTime == 0) {
        submittedTime = System.nanoTime();
        origSubmission.onStartedExecution();
      }
    }

    @Override
    public void onFinishedExecution() {
      processedTime = System.nanoTime();
      origSubmission.onFinishedExecution();
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
   * @param <T> The return type associated with the submission.
   * @author Viktor Csomor
   */
  private static class InternalSubmissionFuture<T> implements Future<T> {

    private final InternalSubmission<T> submission;

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
      if (submission.isCancelled()) {
        throw new CancellationException(String.format("Submission %s cancelled", submission));
      }
      if (submission.getException() != null) {
        throw new ExecutionException(submission.getException());
      }
      return submission.getResult().orElse(null);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      synchronized (submission.getLock()) {
        // If the submission has already been cancelled or if it has already been processed, don't do anything and return false.
        if (submission.isDone()) {
          return false;
        }
        submission.cancel();
        // If it is already being processed and mayInterruptIfRunning is true, interrupt the executor thread.
        if (mayInterruptIfRunning && submission.getThread() != null) {
          submission.getThread().interrupt();
        }
        return submission.isCancelled();
      }
    }

    @Override
    public T get() throws InterruptedException, ExecutionException, CancellationException {
      // Wait until the submission is processed, or cancelled, or fails.
      synchronized (submission.getLock()) {
        while (!submission.isDone()) {
          submission.getLock().wait();
        }
        return getResult();
      }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
      // Wait until the submission is processed, or cancelled, or fails, or the method times out.
      synchronized (submission.getLock()) {
        long timeoutNs = unit.toNanos(timeout);
        long start = System.nanoTime();
        while (!submission.isDone() && timeoutNs > 0) {
          submission.getLock().wait(timeoutNs / 1000000, (int) (timeoutNs % 1000000));
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
      return submission.isCancelled();
    }

    @Override
    public boolean isDone() {
      return submission.isDone();
    }

  }

  /**
   * A sub-class of {@link AbstractProcessExecutor} that utilizes an additional thread to listen to the submission queue of the process
   * pool and take submissions for executions.
   *
   * @author Viktor Csomor
   */
  private class InternalProcessExecutor extends AbstractProcessExecutor {

    private Thread submissionThread;

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
     */
    void updateSubmissionQueue(InternalSubmission<?> submission) {
      if (submission != null) {
        synchronized (mainLock) {
          if (submission.isDone()) {
            numOfSubmissions--;
            mainLock.notifyAll();
            LOGGER.debug(String.format("Submission %s processed%s", submission,
                submission.isProcessed() ? String.format(" - delay: %.3f; execution time: %.3f",
                    (submission.getSubmittedTime() - submission.getReceivedTime()) / 1000000000d,
                    (submission.getProcessedTime() - submission.getSubmittedTime()) / 1000000000d) : ""));
            LOGGER.debug(getPoolStats());
          } else {
            // If the execute method failed and there was no exception thrown, put the submission back into the queue at the front.
            submission.setThread(null);
            submissionQueue.addFirst(submission);
            LOGGER.trace("Submission put back in queue");
          }
        }
      }
    }

    /**
     * Takes a submission of the queue, waiting until there is one available, and attempts to execute it. If the submission's execution is
     * unsuccessful or interrupted without any other exceptions thrown, the submission is put back into the queue. If an exception is
     * thrown during its execution, the submission is deemed completed and the process is terminated.
     */
    void waitForAndExecuteAvailableSubmission() {
      InternalSubmission<?> submission;
      try {
        LOGGER.trace("Waiting for process executor to be ready for submission execution...");
        executeLock.lockInterruptibly();
        executeLock.unlock();
        LOGGER.trace("Waiting for a submission...");
        submission = submissionQueue.takeFirst();
        LOGGER.trace("Submission {} taken off queue", submission);
      } catch (InterruptedException e) {
        LOGGER.trace("Submission executor thread interrupted while waiting", e);
        return;
      }
      try {
        submission.setThread(submissionThread);
        if (!submission.isCancelled()) {
          tryExecute(submission, submission.getTerminateProcessAfterwards());
        }
      } catch (FailedCommandException e) {
        LOGGER.trace("Exception while executing submission", e);
        submission.setException(e);
      } catch (DisruptedExecutionException e) {
        // The submission is not assumed to be at fault.
      } finally {
        if (submission.isCancelled()) {
          LOGGER.trace("Submission cancelled");
        }
        updateSubmissionQueue(submission);
      }
    }

    /**
     * Waits on the blocking queue of submissions executing available ones one at a time.
     */
    void takeAndExecuteSubmissions() {
      synchronized (stateLock) {
        submissionThread = Thread.currentThread();
      }
      try {
        while (isAlive()) {
          waitForAndExecuteAvailableSubmission();
        }
      } finally {
        synchronized (stateLock) {
          submissionThread = null;
        }
      }
    }

    @Override
    protected Map<String, ThrowingRunnable> getAdditionalChildThreads() {
      return Collections.singletonMap("submission executor", this::takeAndExecuteSubmissions);
    }

    @Override
    protected void onExecutorStartup() {
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
      return String.format("%s-internalProcessExecutor@%s", ProcessPoolExecutor.this, Integer.toHexString(hashCode()));
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

    private final String poolName;
    private final ThreadFactory defaultFactory;

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
        LOGGER.error(e.getMessage(), e);
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
      super(Math.max(minPoolSize, reserveSize), maxPoolSize, threadKeepAliveTime, TimeUnit.MILLISECONDS,
          new LinkedTransferQueue<Runnable>() {

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
        LOGGER.debug("Process executor {} stopped", executor);
        LOGGER.debug(getPoolStats());
        if (doExtendPool()) {
          startNewProcess();
        }
      }
    }

  }

}