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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
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
  protected final Object runLock;
  protected final Object stateLock;
  protected final Lock executeLock;
  protected final AtomicInteger numOfChildThreads;
  protected final Semaphore terminationSemaphore;

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
    runLock = new Object();
    stateLock = new Object();
    executeLock = new ReentrantLock(true);
    numOfChildThreads = new AtomicInteger();
    terminationSemaphore = new Semaphore(0);
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
   * Keeps listening to the specified output stream until the end of the stream is reached or the stream is closed.
   *
   * @param reader The buffered reader to use to listen to the steam.
   * @param error Whether it is the standard error or the standard out stream of the process.
   */
  private void readStreamAndProcessOutput(BufferedReader reader, boolean error) {
    numOfChildThreads.incrementAndGet();
    LOGGER.trace("Starting listening to standard {} stream", error ? "error" : "out");
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        LOGGER.trace("Output \"{}\" printed to standard {} stream", line, error ? "error" : "out");
        synchronized (stateLock) {
          if (!startedUp) {
            startedUp = manager.isStartedUp(line, error);
            LOGGER.trace("Output denotes process start-up completion: {}", startedUp);
            if (startedUp) {
              stateLock.notifyAll();
            }
          } else if (command != null && !commandCompleted) {
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
      }
    } catch (Exception e) {
      LOGGER.trace("Error while reading from stream and/or processing output", e);
      terminateForcibly();
    } finally {
      LOGGER.trace("Stopping listening to standard {} stream", error ? "error" : "out");
      terminationSemaphore.release();
    }
  }

  /**
   * Keeps measuring the time spent in an idle state and terminates the process once this time reaches the specified amount.
   *
   * @param keepAliveTime The number of milliseconds of idleness after which the process is to be terminated.
   */
  private void timeIdlenessAndTerminateProcess(long keepAliveTime) {
    numOfChildThreads.incrementAndGet();
    LOGGER.trace("Starting idleness timer");
    try {
      synchronized (stateLock) {
        while (isAlive()) {
          LOGGER.trace("Waiting for process to go idle");
          while (isAlive() && !idle) {
            stateLock.wait();
          }
          if (isAlive()) {
            LOGGER.trace("Process going idle...");
            long waitTime = keepAliveTime;
            long startTime = System.currentTimeMillis();
            while (isAlive() && idle && waitTime > 0) {
              stateLock.wait(keepAliveTime);
              waitTime -= System.currentTimeMillis() - startTime;
            }
            if (isAlive() && idle && executeLock.tryLock()) {
              try {
                LOGGER.trace("Attempting to terminate process due to prolonged idleness");
                terminate();
              } finally {
                executeLock.unlock();
              }
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.trace("Error while timing idleness and/or terminating process", e);
      terminateForcibly();
    } finally {
      LOGGER.trace("Stopping idleness timer");
      terminationSemaphore.release();
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
    executeLock.lock();
    LOGGER.trace("Setting up executor...");
    try {
      synchronized (stateLock) {
        numOfChildThreads.set(0);
        terminationSemaphore.drainPermits();
        Charset charset = manager.getEncoding();
        process = manager.start();
        LOGGER.trace("Process launched");
        running = true;
        stdInWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), charset));
        stdOutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset));
        stdErrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), charset));
        threadPool.execute(() -> readStreamAndProcessOutput(stdOutReader, false));
        threadPool.execute(() -> readStreamAndProcessOutput(stdErrReader, true));
        manager.getKeepAliveTime().ifPresent(keepAliveTime -> threadPool.execute(() -> timeIdlenessAndTerminateProcess(keepAliveTime)));
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
        executeInitialSubmission();
        LOGGER.trace("Invoking start-up call-back methods...");
        manager.onStartup();
        onExecutorStartup();
        idle = true;
        LOGGER.trace("Executor set up");
      }
    } finally {
      executeLock.unlock();
    }
  }

  /**
   * It tears down the executor infrastructure, invokes the appropriate callback methods, and waits for the child threads to terminate.
   *
   * @param returnCode The return code of the process.
   */
  private void tearDownExecutor(int returnCode) {
    LOGGER.trace("Tearing down executor");
    synchronized (stateLock) {
      killed = false;
      idle = false;
      startedUp = false;
      running = false;
      process = null;
      stateLock.notifyAll();
      LOGGER.trace("Invoking termination call-back methods");
      manager.onTermination(returnCode);
      onExecutorTermination();
    }
    LOGGER.trace("Closing streams");
    closeStream(stdInWriter);
    closeStream(stdOutReader);
    closeStream(stdErrReader);
    LOGGER.trace("Waiting for child threads to terminate");
    try {
      terminationSemaphore.acquire(numOfChildThreads.get());
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
    synchronized (stateLock) {
      LOGGER.trace("Starting execution of submission");
      submission.onStartedExecution();
      for (Command command : submission.getCommands()) {
        executeCommand(command);
      }
      LOGGER.trace("Submission {} executed", submission);
      submission.onFinishedExecution();
    }
  }

  /**
   * A method called after the startup of the process and the set up of the executor.
   */
  protected abstract void onExecutorStartup();

  /**
   * A call-back method invoked after the termination of the underlying process and the stopping of the executor's helper threads.
   */
  protected abstract void onExecutorTermination();

  /**
   * Returns whether the the underlying process is alive.
   *
   * @return Whether the process is alive.
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
    executeLock.lock();
    try {
      tryExecute(submission, false);
    } finally {
      executeLock.unlock();
    }
  }

  @Override
  public void run() {
    synchronized (runLock) {
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

}
