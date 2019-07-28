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

  protected final ProcessManager manager;
  protected final ExecutorService threadPool;
  protected final Logger logger;
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
  private boolean running;
  private boolean startedUp;
  private boolean idle;
  private boolean killed;
  private boolean commandCompleted;

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
    logger = LoggerFactory.getLogger(getClass());
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
        logger.trace(e.getMessage(), e);
      }
    }
  }

  /**
   * Keeps listening to the specified output stream until the end of the stream is reached or the stream is closed.
   *
   * @param reader The buffered reader to use to listen to the steam.
   * @param error Whether it is the standard error or the standard out stream of the process.
   */
  private void listenToProcessStream(BufferedReader reader, boolean error) {
    numOfChildThreads.incrementAndGet();
    logger.trace("Starting listening to standard {} stream", error ? "error" : "out");
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        logger.trace("Output \"{}\" printed to standard {} stream", line, error ? "error" : "out");
        synchronized (stateLock) {
          if (!startedUp) {
            startedUp = manager.isStartedUp(line, error);
            logger.trace("Output denotes process start-up completion: {}", startedUp);
            if (startedUp) {
              stateLock.notifyAll();
            }
          } else if (command != null && !commandCompleted) {
            commandCompleted = command.isProcessed(line, error);
            logger.trace("Output denotes command completion: {}", commandCompleted);
            if (commandCompleted) {
              stateLock.notifyAll();
            }
          }
        }
      }
    } catch (IOException e) {
      logger.trace("Error while reading from process stream", e);
      terminateForcibly();
    } finally {
      logger.trace("Stopping listening to standard {} stream", error ? "error" : "out");
      terminationSemaphore.release();
    }
  }

  /**
   * Keeps measuring the time spent in an idle state and terminates the process once this time reaches the specified amount.
   *
   * @param keepAliveTime The number of milliseconds of idleness after which the process is to be terminated.
   */
  private void timeIdleProcess(long keepAliveTime) {
    numOfChildThreads.incrementAndGet();
    logger.trace("Starting idleness timer");
    try {
      synchronized (stateLock) {
        while (isAlive()) {
          idle = true;
          logger.trace("Process going idle");
          long waitTime = keepAliveTime;
          long startTime = System.currentTimeMillis();
          while (isAlive() && idle && waitTime > 0) {
            stateLock.wait(keepAliveTime);
            waitTime -= System.currentTimeMillis() - startTime;
          }
          if (isAlive() && idle && executeLock.tryLock()) {
            try {
              logger.trace("Attempting to terminate process due to prolonged idleness");
              terminate();
            } finally {
              executeLock.unlock();
            }
          }
        }
      }
    } catch (InterruptedException e) {
      logger.trace("Process keep alive timer interrupted", e);
      terminateForcibly();
      Thread.currentThread().interrupt();
    } finally {
      logger.trace("Stopping idleness timer");
      terminationSemaphore.release();
    }
  }

  /**
   * Starts up the process, sets up the executor infrastructure, invokes the appropriate callback methods, and executes the start-up
   * submission if there is one.
   *
   * @throws IOException If the process cannot be started.
   * @throws InterruptedException If the executing thread is interrupted while waiting for the process to start up.
   */
  private void setUpExecutor() throws IOException, InterruptedException {
    executeLock.lock();
    logger.trace("Setting up executor");
    try {
      synchronized (stateLock) {
        numOfChildThreads.set(0);
        terminationSemaphore.drainPermits();
        Charset charset = manager.getEncoding();
        process = manager.start();
        logger.trace("Process launched");
        running = true;
        stdInWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), charset));
        stdOutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset));
        stdErrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), charset));
        threadPool.execute(() -> listenToProcessStream(stdOutReader, false));
        threadPool.execute(() -> listenToProcessStream(stdErrReader, true));
        logger.trace("Waiting for process to start up");
        while (!startedUp) {
          if (killed) {
            logger.trace("Process killed before it could start up");
            return;
          }
          stateLock.wait();
        }
        logger.trace("Process started up");
        manager.getKeepAliveTime().ifPresent(keepAliveTime -> threadPool.execute(() -> timeIdleProcess(keepAliveTime)));
        logger.trace("Executing initial submission");
        manager.getInitSubmission().ifPresent(this::execute);
        logger.trace("Invoking start-up call-back methods");
        manager.onStartup();
        onExecutorStartup();
        logger.trace("Executor set up");
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
    logger.trace("Tearing down executor");
    synchronized (stateLock) {
      killed = false;
      idle = false;
      startedUp = false;
      running = false;
      process = null;
      stateLock.notifyAll();
      logger.trace("Invoking termination call-back methods");
      manager.onTermination(returnCode);
      onExecutorTermination();
    }
    logger.trace("Closing streams");
    closeStream(stdInWriter);
    closeStream(stdOutReader);
    closeStream(stdErrReader);
    logger.trace("Waiting for child threads to terminate");
    try {
      terminationSemaphore.acquire(numOfChildThreads.get());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.trace(e.getMessage(), e);
    }
    logger.trace("Executor torn down");
  }

  /**
   * Executes the specified command by writing the instruction to the process' standard in and waiting for a signal denoting the completion
   * of the command's execution if the command is expected to produce an output at all.
   *
   * @param command The command to execute.
   * @return Whether the command's execution was successful.
   * @throws IOException If writing to the process' stream is not possible.
   * @throws InterruptedException If the thread is interrupted while waiting for the command's response output.
   */
  private boolean executeCommand(Command command) throws IOException, InterruptedException {
    command.reset();
    this.command = command;
    try {
      String instruction = command.getInstruction();
      logger.trace("Writing instruction \"{}\" to process' standard in", instruction);
      stdInWriter.write(instruction);
      stdInWriter.newLine();
      stdInWriter.flush();
      commandCompleted = !command.generatesOutput();
      while (!commandCompleted) {
        if (!isAlive()) {
          logger.trace("Abort command execution due to process termination");
          return false;
        }
        stateLock.wait();
      }
      logger.trace("Command completed");
      return true;
    } finally {
      this.command = null;
      commandCompleted = false;
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
   * Executes the specified submission synchronously by delegating its commands to the underlying process serially and processing the
   * responses of the process.
   *
   * @param submission The submission to process and execute.
   * @param terminateProcessAfterwards Whether the process should be terminated after the execution of the submission.
   * @return Whether the execution of the submission has completed successfully.
   */
  protected boolean execute(Submission<?> submission, boolean terminateProcessAfterwards) {
    logger.trace("Attempting to execute submission {}", submission);
    if (executeLock.tryLock()) {
      try {
        synchronized (stateLock) {
          if (!isAlive() || !startedUp) {
            logger.trace("Process not ready for submissions");
            return false;
          }
          idle = false;
          stateLock.notifyAll();
          logger.trace("Starting execution of submission");
          submission.onStartedProcessing();
          for (Command command : submission.getCommands()) {
            if (!executeCommand(command)) {
              return false;
            }
          }
          logger.trace("Submission {} executed", submission);
          submission.onFinishedProcessing();
          if (terminateProcessAfterwards) {
            logger.trace("Terminating process after successful submission execution");
            terminate();
          }
          return true;
        }
      } catch (IOException e) {
        logger.trace("Error while writing to process stream", e);
        terminateForcibly();
        return false;
      } catch (InterruptedException e) {
        logger.trace("Submission execution interrupted", e);
        terminateForcibly();
        Thread.currentThread().interrupt();
        return false;
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
    logger.trace("Attempting to terminate process using termination submission");
    if (executeLock.tryLock()) {
      try {
        Optional<Submission<?>> optionalTerminationSubmission = manager.getTerminationSubmission();
        if (optionalTerminationSubmission.isPresent()) {
          synchronized (stateLock) {
            if (isAlive()) {
              return execute(optionalTerminationSubmission.get());
            } else {
              logger.trace("Cannot execute termination submission as process is already terminated");
            }
          }
        } else {
          logger.trace("No termination submission found");
        }
      } catch (Exception e) {
        logger.trace("Error attempting to terminate process", e);
      } finally {
        executeLock.unlock();
      }
    }
    return false;
  }

  /**
   * It sends a kill signal to the currently running process, if there is one.
   */
  public void terminateForcibly() {
    logger.trace("Terminating process forcibly...");
    synchronized (stateLock) {
      if (isAlive()) {
        process.destroyForcibly();
        killed = true;
        stateLock.notifyAll();
        logger.trace("Process killed");
      } else {
        logger.trace("Cannot terminate process as it is already terminated");
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
  public boolean execute(Submission<?> submission) {
    return execute(submission, false);
  }

  @Override
  public void run() {
    synchronized (runLock) {
      int returnCode = UNEXPECTED_TERMINATION_RETURN_CODE;
      try {
        setUpExecutor();
        returnCode = process.waitFor();
        logger.trace("Process exited with return code {}", returnCode);
      } catch (Exception e) {
        throw new ProcessException(e);
      } finally {
        tearDownExecutor(returnCode);
      }
    }
  }

}
