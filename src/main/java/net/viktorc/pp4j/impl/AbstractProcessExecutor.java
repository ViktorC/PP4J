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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.DisruptedExecutionException;
import net.viktorc.pp4j.api.FailedCommandException;
import net.viktorc.pp4j.api.ProcessExecutor;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract implementation of the {@link ProcessExecutor} interface for starting, managing, and interacting with a process. The life
 * cycle of the associated process is encapsulated within that of the {@link #run()} method of instances of this class.
 *
 * @author Viktor Csomor
 */
public abstract class AbstractProcessExecutor implements ProcessExecutor, Runnable {

  /**
   * If a process cannot be started or an exception occurs which would make it impossible to retrieve the actual return code of the
   * process.
   */
  public static final int UNEXPECTED_TERMINATION_RETURN_CODE = -1;

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProcessExecutor.class);

  protected final ProcessManager manager;
  protected final ExecutorService threadPool;
  protected final Lock runLock;
  protected final Lock executeLock;
  protected final Object stateLock;

  private final Semaphore initSemaphore;
  private final Semaphore terminationSemaphore;

  private Process process;
  private BufferedWriter stdInWriter;
  private BufferedReader stdOutReader;
  private BufferedReader stdErrReader;
  private Command command;
  private FailedCommandException commandException;
  private boolean commandCompleted;
  private boolean running;
  private boolean startedUp;
  private boolean idle;
  private boolean killed;
  private int numOfChildThreads;

  /**
   * Constructs an executor for the specified process using <code>threadPool</code> to provide the threads required for listening to the
   * standard streams of the process and ensuring that the process is terminated once it times out if the
   * {@link ProcessManager#getKeepAliveTime()} method of <code>manager</code> returns a non-empty optional.
   *
   * @param manager The <code>ProcessManager</code> instance to manage the life-cycle of the underlying process.
   * @param threadPool The thread pool to use for running the helper threads required for the running of the process and the execution of
   * submissions.
   */
  protected AbstractProcessExecutor(ProcessManager manager, ExecutorService threadPool) {
    this.manager = manager;
    this.threadPool = threadPool;
    runLock = new ReentrantLock(true);
    executeLock = new ReentrantLock(true);
    stateLock = new Object();
    initSemaphore = new Semaphore(0);
    terminationSemaphore = new Semaphore(0);
  }

  /**
   * Processes the output of the process if the process has not started up yet.
   *
   * @param line The line output to the process' stream.
   * @param error Whether the stream is the process' standard error or standard out.
   */
  private void processStartupOutput(String line, boolean error) {
    synchronized (stateLock) {
      startedUp = manager.isStartedUp(line, error);
      LOGGER.trace("Output denotes process start-up completion: {}", startedUp);
      if (startedUp) {
        stateLock.notifyAll();
      }
    }
  }

  /**
   * Processes the output of the process if a command is being executed.
   *
   * @param line The line output to the process' stream.
   * @param error Whether the stream is the process' standard error or standard out.
   */
  private void processCommandOutput(String line, boolean error) {
    synchronized (stateLock) {
      try {
        commandCompleted = command.isCompleted(line, error);
      } catch (FailedCommandException e) {
        commandCompleted = true;
        commandException = e;
      }
      LOGGER.trace("Output denotes command completion: {}", commandCompleted);
      if (commandCompleted) {
        stateLock.notifyAll();
      }
    }
  }

  /**
   * Processes the output of the process invoking the appropriate methods based on the current state of the executor.
   *
   * @param line The line output to the process' stream.
   * @param error Whether the stream is the process' standard error or standard out.
   */
  private void processOutput(String line, boolean error) {
    synchronized (stateLock) {
      LOGGER.trace("Output \"{}\" printed to standard {} stream", line, error ? "error" : "out");
      if (!startedUp) {
        processStartupOutput(line, error);
      } else if (command != null && !commandCompleted) {
        processCommandOutput(line, error);
      }
    }
  }

  /**
   * Keeps listening to the specified output stream until the end of the stream is reached or the stream is closed.
   *
   * @param reader The buffered reader to use to listen to the steam.
   * @param error Whether it is the standard error or the standard out stream of the process.
   * @throws IOException If there is an error while reading from the stream.
   */
  private void readStreamAndProcessOutput(BufferedReader reader, boolean error) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      processOutput(line, error);
    }
  }

  /**
   * Waits until the process executor goes idle or the process dies.
   *
   * @throws InterruptedException If the current thread is interrupted while waiting for the process executor to go idle.
   */
  private void waitForIdleness() throws InterruptedException {
    synchronized (stateLock) {
      LOGGER.trace("Waiting for process to go idle");
      while (isAlive() && !idle) {
        stateLock.wait();
      }
    }
  }

  /**
   * Blocks the thread for the specified amount of time or until the process executor is not idle anymore or the process dies.
   *
   * @param keepAliveTime The amount of time to wait for in milliseconds.
   * @throws InterruptedException If the current thread is interrupted while waiting for the process executor to go idle.
   */
  private void waitKeepAliveTime(long keepAliveTime) throws InterruptedException {
    synchronized (stateLock) {
      LOGGER.trace("Process going idle...");
      long waitTime = keepAliveTime;
      long startTime = System.currentTimeMillis();
      while (isAlive() && idle && waitTime > 0) {
        stateLock.wait(keepAliveTime);
        waitTime -= System.currentTimeMillis() - startTime;
      }
    }
  }

  /**
   * It terminates the process if it is still alive and the executor is idle.
   */
  private void terminateIdleProcessIfTimedOut() {
    if (isAlive() && idle && executeLock.tryLock()) {
      try {
        LOGGER.trace("Attempting to terminate process due to prolonged idleness");
        terminate();
      } finally {
        executeLock.unlock();
      }
    }
  }

  /**
   * Keeps measuring the time spent in an idle state and terminates the process once this time reaches the specified amount.
   *
   * @param keepAliveTime The number of milliseconds of idleness after which the process is to be terminated.
   * @throws InterruptedException If the thread is interrupted while waiting for the process to go idle or while waiting for process to
   * exceed itss maximum allowed duration of idleness.
   */
  private void timeIdlenessAndTerminateProcess(long keepAliveTime) throws InterruptedException {
    synchronized (stateLock) {
      while (isAlive()) {
        waitForIdleness();
        if (isAlive()) {
          waitKeepAliveTime(keepAliveTime);
          terminateIdleProcessIfTimedOut();
        }
      }
    }
  }

  /**
   * It launches the specified runnable in the process executor's thread pool and handles the initialization and termination semaphores.
   *
   * @param runnable The child thread to run.
   * @param threadName The name of the child thread.
   */
  private void startChildThread(ThrowingRunnable runnable, String threadName) {
    threadPool.execute(() -> {
      LOGGER.trace("Starting {} thread of process executor {}...", threadName, this);
      initSemaphore.release();
      try {
        runnable.run();
      } catch (Throwable e) {
        LOGGER.trace(String.format("Error while running %s thread", threadName), e);
        terminateForcibly();
      } finally {
        terminationSemaphore.release();
        LOGGER.trace("Process executor {}'s {} thread stopped", this, threadName);
      }
    });
    numOfChildThreads++;
  }

  /**
   * Launches the child threads in the process executor's thread pool and waits for them to start running.
   *
   * @throws InterruptedException If the thread is interrupted while waiting for the child threads to start running.
   */
  private void startAndWaitForChildThreads() throws InterruptedException {
    LOGGER.trace("Executing core child threads...");
    startChildThread(() -> readStreamAndProcessOutput(stdOutReader, false), "standard out listener");
    startChildThread(() -> readStreamAndProcessOutput(stdErrReader, true), "standard error listener");
    manager.getKeepAliveTime()
        .ifPresent(keepAliveTime -> startChildThread(() -> timeIdlenessAndTerminateProcess(keepAliveTime), "idle process terminator"));
    LOGGER.trace("Executing additional child threads...");
    for (Entry<String, ThrowingRunnable> entry : getAdditionalChildThreads().entrySet()) {
      startChildThread(entry.getValue(), entry.getKey());
    }
    LOGGER.trace("Waiting for child threads to start running...");
    initSemaphore.acquire(numOfChildThreads);
    LOGGER.trace("Child threads running");
  }

  /**
   * Waits for the process to start up if it does not start up instantly.
   *
   * @throws InterruptedException If the thread is interrupted while waiting for the process to start up.
   */
  private void waitForProcessStartup() throws InterruptedException {
    synchronized (stateLock) {
      LOGGER.trace("Waiting for process to start up...");
      startedUp = manager.startsUpInstantly();
      while (!startedUp) {
        if (killed) {
          LOGGER.trace("Process killed before it could start up");
          return;
        }
        stateLock.wait();
      }
      LOGGER.trace("Process started up");
    }
  }

  /**
   * Executes the initial submission if it is defined.
   *
   * @throws FailedCommandException If the initial submission's execution fails due to a command failure.
   */
  private void executeInitialSubmission() throws FailedCommandException {
    Optional<Submission<?>> initSubmission = manager.getInitSubmission();
    if (initSubmission.isPresent()) {
      try {
        LOGGER.trace("Executing initial submission...");
        execute(initSubmission.get());
      } catch (DisruptedExecutionException e) {
        LOGGER.trace(e.getMessage(), e);
      }
    }
  }

  /**
   * Starts up the process, sets up the executor infrastructure, invokes the appropriate callback methods, and executes the start-up
   * submission if there is one.
   *
   * @throws IOException If the process cannot be started.
   * @throws InterruptedException If the executing thread is interrupted while waiting for the process to start up.
   * @throws FailedCommandException If the initial submission's execution fails due to a command failure.
   */
  private void setUpExecutor() throws IOException, InterruptedException, FailedCommandException {
    LOGGER.trace("Setting up executor...");
    executeLock.lock();
    try {
      synchronized (stateLock) {
        numOfChildThreads = 0;
        initSemaphore.drainPermits();
        terminationSemaphore.drainPermits();
        Charset charset = manager.getEncoding();
        process = manager.start();
        LOGGER.trace("Process launched");
        running = true;
        stdInWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), charset));
        stdOutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset));
        stdErrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), charset));
        startAndWaitForChildThreads();
        waitForProcessStartup();
        executeInitialSubmission();
        LOGGER.trace("Invoking start-up call-back methods...");
        manager.onStartup();
        onExecutorStartup();
        idle = true;
        stateLock.notifyAll();
        LOGGER.trace("Executor set up");
      }
    } finally {
      executeLock.unlock();
    }
  }

  /**
   * Closes the provided stream.
   *
   * @param stream The stream to close.
   */
  private void closeStream(Closeable stream) {
    if (stream != null) {
      try {
        stream.close();
      } catch (IOException e) {
        LOGGER.trace(e.getMessage(), e);
      }
    }
  }

  /**
   * It tears down the executor infrastructure, invokes the appropriate callback methods, and waits for the child threads to terminate.
   *
   * @param returnCode The return code of the process.
   */
  private void tearDownExecutor(int returnCode) {
    LOGGER.trace("Tearing down executor...");
    synchronized (stateLock) {
      terminateForcibly();
      killed = false;
      idle = false;
      startedUp = false;
      running = false;
      process = null;
      stateLock.notifyAll();
      LOGGER.trace("Invoking termination call-back methods...");
      manager.onTermination(returnCode);
      onExecutorTermination();
    }
    LOGGER.trace("Closing streams");
    closeStream(stdInWriter);
    closeStream(stdOutReader);
    closeStream(stdErrReader);
    LOGGER.trace("Waiting for child threads to terminate...");
    try {
      terminationSemaphore.acquire(numOfChildThreads);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.trace(e.getMessage(), e);
    }
    LOGGER.trace("Executor torn down");
  }

  /**
   * Executes the specified command by writing the instruction to the process' standard in and waiting for a signal denoting the completion
   * of the command's execution if the command is expected to produce an output at all.
   *
   * @param command The command to execute.
   * @throws IOException If writing to the process' stream is not possible.
   * @throws InterruptedException If the thread is interrupted while waiting for the command's response output.
   * @throws FailedCommandException If the command fails.
   * @throws DisruptedExecutionException If the process terminates during execution.
   */
  private void executeCommand(Command command)
      throws IOException, InterruptedException, FailedCommandException, DisruptedExecutionException {
    synchronized (stateLock) {
      command.reset();
      this.command = command;
      try {
        String instruction = command.getInstruction();
        LOGGER.trace("Writing instruction \"{}\" to process' standard in", instruction);
        stdInWriter.write(instruction);
        stdInWriter.newLine();
        stdInWriter.flush();
        commandCompleted = !command.generatesOutput();
        while (!commandCompleted) {
          if (!isAlive()) {
            throw new DisruptedExecutionException("Process terminated during execution");
          }
          stateLock.wait();
        }
        if (commandException != null) {
          LOGGER.trace("Command failed");
          throw commandException;
        }
        LOGGER.trace("Command succeeded");
      } finally {
        this.command = null;
        commandException = null;
        commandCompleted = false;
      }
    }
  }

  /**
   * It executes the specified submission by sequentially executing its commands.
   *
   * @param submission The submission to execute.
   * @throws IOException If writing to the process' stream is not possible.
   * @throws InterruptedException If the thread is interrupted while waiting for the command's response output.
   * @throws FailedCommandException If one of the submission's commands fails.
   * @throws DisruptedExecutionException If the process terminates during execution.
   */
  private void executeSubmission(Submission<?> submission)
      throws IOException, InterruptedException, FailedCommandException, DisruptedExecutionException {
    LOGGER.trace("Starting execution of submission");
    submission.onStartedExecution();
    for (Command command : submission.getCommands()) {
      executeCommand(command);
    }
    LOGGER.trace("Submission {} executed", submission);
    submission.onFinishedExecution();
  }

  /**
   * Returns a map of additional child threads to run where the keys are the names of the child threads and the values are the
   * runnable tasks. If no tasks are to be executed as child threads, it should return an empty map.
   *
   * @return A map of child thread tasks.
   */
  protected abstract Map<String, ThrowingRunnable> getAdditionalChildThreads();

  /**
   * A method called after the startup of the process and the set up of the executor.
   */
  protected abstract void onExecutorStartup();

  /**
   * A call-back method invoked after the termination of the underlying process and the stopping of the executor's helper threads.
   */
  protected abstract void onExecutorTermination();

  /**
   * Returns whether the process executor is alive. The process executor is considered alive if its underlying process is alive and has not
   * not received a kill signal.
   *
   * @return Whether the process executor is alive.
   */
  protected boolean isAlive() {
    synchronized (stateLock) {
      return running && !killed;
    }
  }

  /**
   * Attempts to execute the specified submission synchronously by delegating its commands to the underlying process serially and
   * processing the responses of the process. If the process is already busy executing, it returns <code>false</code>, otherwise it
   * proceeds to execute the command and returns <code>true</code>.
   *
   * @param submission The submission to process and execute.
   * @param terminateProcessAfterwards Whether the process should be terminated after the execution of the submission.
   * @return Whether the execution of the submission was completed.
   * @throws FailedCommandException If a command of the submission fails.
   * @throws DisruptedExecutionException If the process terminates during execution or there is any other error that would disrupt the
   * execution of the submission.
   */
  protected boolean tryExecute(Submission<?> submission, boolean terminateProcessAfterwards)
      throws FailedCommandException, DisruptedExecutionException {
    LOGGER.trace("Attempting to execute submission {}", submission);
    if (executeLock.tryLock()) {
      try {
        synchronized (stateLock) {
          boolean wasIdle = idle;
          try {
            if (!isAlive() || !startedUp) {
              throw new DisruptedExecutionException("Process not running and/or started up");
            }
            idle = false;
            stateLock.notifyAll();
            executeSubmission(submission);
            if (terminateProcessAfterwards) {
              LOGGER.trace("Terminating process after successful submission execution");
              terminate();
            }
            return true;
          } catch (IOException e) {
            LOGGER.trace("Error while writing to process stream");
            terminateForcibly();
            throw new DisruptedExecutionException(e);
          } catch (InterruptedException e) {
            LOGGER.trace("Submission execution interrupted");
            terminateForcibly();
            Thread.currentThread().interrupt();
            throw new DisruptedExecutionException(e);
          } finally {
            idle = wasIdle && running;
            stateLock.notifyAll();
          }
        }
      } finally {
        executeLock.unlock();
      }
    }
    return false;
  }

  /**
   * It attempts to terminate the currently running process, if there is one, using the {@link ProcessManager#getTerminationSubmission()}
   * method of the process manager assigned to the executor. This might be ineffective if the process is currently executing a command, is
   * not currently running, the optional termination submission is not defined by the process manager, or the termination submission is
   * unsuccessful.
   *
   * @return Whether the termination submission has been successfully executed.
   */
  protected boolean tryTerminate() {
    LOGGER.trace("Attempting to terminate process using termination submission");
    Optional<Submission<?>> optionalTerminationSubmission = manager.getTerminationSubmission();
    if (optionalTerminationSubmission.isPresent()) {
      try {
        return tryExecute(optionalTerminationSubmission.get(), false);
      } catch (FailedCommandException | DisruptedExecutionException e) {
        LOGGER.trace(e.getMessage(), e);
      }
    } else {
      LOGGER.trace("No termination submission defined");
    }
    return false;
  }

  /**
   * It sends a kill signal to the currently running process, if there is one.
   */
  protected void terminateForcibly() {
    LOGGER.trace("Terminating process forcibly...");
    synchronized (stateLock) {
      if (isAlive()) {
        process.destroyForcibly();
        killed = true;
        stateLock.notifyAll();
        LOGGER.trace("Process killed");
      } else {
        LOGGER.trace("Cannot terminate process as it is already terminated");
      }
    }
  }

  /**
   * It attempts to terminate the process using the termination submission defined by the process manger, if there is one, then proceeds
   * to forcibly shut down the process if it is still alive.
   */
  public void terminate() {
    tryTerminate();
    terminateForcibly();
  }

  @Override
  public void execute(Submission<?> submission) throws FailedCommandException, DisruptedExecutionException {
    if (submission == null) {
      throw new IllegalArgumentException("The submission is null");
    }
    executeLock.lock();
    try {
      tryExecute(submission, false);
    } finally {
      executeLock.unlock();
    }
  }

  @Override
  public void run() {
    runLock.lock();
    try {
      int returnCode = UNEXPECTED_TERMINATION_RETURN_CODE;
      try {
        setUpExecutor();
        returnCode = process.waitFor();
        LOGGER.trace("Process exited with return code {}", returnCode);
      } catch (Exception e) {
        throw new ProcessException(e);
      } finally {
        tearDownExecutor(returnCode);
      }
    } finally {
      runLock.unlock();
    }
  }

  /**
   * An exception thrown if an unexpected error occurs while running or interacting with a process.
   *
   * @author Viktor Csomor
   */
  public static class ProcessException extends RuntimeException {

    /**
     * Constructs a wrapper for the specified exception.
     *
     * @param e The source exception.
     */
    protected ProcessException(Exception e) {
      super(e);
    }

  }

  /**
   * A functional interface for a runnable that may throw checked exceptions and errors.
   *
   * @author Viktor Csomor
   */
  @FunctionalInterface
  protected interface ThrowingRunnable {

    /**
     * Runs a task and may throw an exception or error if an error occurs.
     *
     * @throws Throwable If something goes wrong.
     */
    void run() throws Throwable;

  }

}
