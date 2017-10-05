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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.ProcessExecutor;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.Submission;

/**
 * An abstract implementation of the {@link net.viktorc.pp4j.api.ProcessExecutor} interface for starting, 
 * managing, and interacting with a process. The life cycle of the associated process is the same as that of 
 * the {@link #run()} method of the instance. The process is not started until this method is called and the 
 * method does not terminate until the process does.
 * 
 * @author Viktor Csomor
 *
 */
public abstract class AbstractProcessExecutor implements ProcessExecutor, Runnable {
	
	/**
	 * If a process cannot be started or an exception occurs which would make it impossible to retrieve the 
	 * actual return code of the process.
	 */
	public static final int UNEXPECTED_TERMINATION_RESULT_CODE = -1;
	
	protected final ProcessManager manager;
	protected final ExecutorService threadPool;
	protected final Lock runLock;
	protected final Lock stopLock;
	protected final Lock submissionLock;
	protected final Object execLock;
	protected final Object processLock;
	protected final Semaphore termSemaphore;
	protected final AtomicInteger threadsToWaitFor;
	protected final Logger logger;
	private Process process;
	private KeepAliveTimer timer;
	private BufferedReader stdOutReader;
	private BufferedReader stdErrReader;
	private BufferedWriter stdInWriter;
	private Command command;
	private long keepAliveTime;
	private boolean doTime;
	private boolean onWait;
	private boolean commandCompleted;
	private boolean startedUp;
	protected volatile boolean running;
	protected volatile boolean stop;
	
	/**
	 * Constructs an executor for the specified process using <code>threadPool</code> to provide the threads 
	 * required for listening to the out streams of the process and ensuring that the process is terminated once 
	 * it times out if the {@link net.viktorc.pp4j.api.ProcessManager#getKeepAliveTime()} method of <code>
	 * manager</code> returns a positive value.
	 * 
	 * @param manager The <code>ProcessManager</code> implementation instance to manage the life-cycle of the 
	 * underlying process.
	 * @param threadPool The thread pool to use for running the helper threads required for the running of 
	 * the process and the execution of submissions; i.e.
	 * @param verbose Whether events related to the life-cycle of the process should be logged.
	 */
	protected AbstractProcessExecutor(ProcessManager manager, ExecutorService threadPool, boolean verbose) {
		this.manager = manager;
		this.threadPool = threadPool;
		runLock = new ReentrantLock(true);
		stopLock = new ReentrantLock(true);
		submissionLock = new ReentrantLock();
		execLock = new Object();
		processLock = new Object();
		termSemaphore = new Semaphore(0);
		threadsToWaitFor = new AtomicInteger(0);
		logger = verbose ? LoggerFactory.getLogger(getClass()) : NOPLogger.NOP_LOGGER;
	}
	/**
	 * Starts listening to an out stream of the process using the specified reader.
	 * 
	 * @param reader The buffered reader to use to listen to the steam.
	 * @param standard Whether it is the standard out or the standard error stream of the process.
	 */
	private void startListeningToProcess(BufferedReader reader, boolean standard) {
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty())
					continue;
				// Make sure that the submission executor thread is waiting.
				synchronized (execLock) {
					if (startedUp) {
						/* Before processing a new line, make sure that the submission executor 
						 * thread is notified that the line signaling the completion of the command 
						 * has been processed. */
						while (commandCompleted && onWait)
							execLock.wait();
						if (command != null) {
							// Process the next line.
							commandCompleted = command.isProcessed(line, standard);
							if (commandCompleted)
								execLock.notifyAll();
						}
					} else {
						startedUp = manager.isStartedUp(line, standard);
						if (startedUp)
							execLock.notifyAll();
					}
				}
			}
		} catch (IOException | InterruptedException e) {
			throw new ProcessException(e);
		} finally {
			termSemaphore.release();
		}
	}
	/**
	 * It prompts the currently running process, if there is one, to terminate. Once the process has been 
	 * successfully terminated, subsequent calls are ignored and return true unless the process is started 
	 * again.
	 * 
	 * @param forcibly Whether the process should be killed forcibly or using the 
	 * {@link net.viktorc.pp4j.api.ProcessManager#terminateGracefully(ProcessExecutor)} method of the 
	 * {@link net.viktorc.pp4j.api.ProcessManager} instance assigned to the executor. The latter might be 
	 * ineffective if the process is currently executing a command or has not started up.
	 * @return Whether the process was successfully terminated.
	 */
	protected boolean stop(boolean forcibly) {
		stopLock.lock();
		try {
			if (stop)
				return true;
			synchronized (execLock) {
				boolean success = true;
				if (running) {
					if (!forcibly)
						success = manager.terminateGracefully(this);
					else {
						synchronized (processLock) {
							if (process != null)
								process.destroy();
						}
					}
				}
				if (success) {
					stop = true;
					execLock.notifyAll();
				}
				return success;
			}
		} finally {
			stopLock.unlock();
		}
	}
	/**
	 * Executes the specified submission synchronously by delegating its commands to the underlying process 
	 * serially and processing the responses of the process.
	 * 
	 * @param submission The submission to process and execute.
	 * @param terminateProcessAfterwards Whether the process should be terminated after the execution of the 
	 * submission.
	 * @return Whether the execution of the submission has completed successfully.
	 * @throws IOException If there is an error while writing to the standard in of the process.
	 * @throws InterruptedException If the thread is interrupted during the execution.
	 * @throws NullPointerException If <code>submission</code> is <code>null</code>.
	 */
	protected boolean execute(Submission<?> submission, boolean terminateProcessAfterwards)
			throws IOException, InterruptedException {
		if (submissionLock.tryLock()) {
			// Make sure that the reader thread can only process output lines if this one is ready and waiting.
			synchronized (execLock) {
				boolean success = false;
				try {
					/* If the process has terminated or the ProcessExecutor has been stopped while acquiring 
					 * the execLock, return. */
					if (!running || stop)
						return success;
					// Stop the timer as the process is not idle anymore.
					if (doTime)
						timer.stop();
					if (stop)
						return success;
					submission.onStartedProcessing();
					List<Command> commands = submission.getCommands();
					List<Command> processedCommands = commands.size() > 1 ?
							new ArrayList<>(commands.size() - 1) : null;
					for (int i = 0; i < commands.size(); i++) {
						command = commands.get(i);
						if (i != 0 && !command.doExecute(new ArrayList<>(processedCommands)))
							continue;
						commandCompleted = !command.generatesOutput();
						stdInWriter.write(command.getInstruction());
						stdInWriter.newLine();
						stdInWriter.flush();
						while (running && !stop && !commandCompleted) {
							onWait = true;
							execLock.wait();
						}
						// Let the readers know that the command may be considered effectively processed.
						onWait = false;
						execLock.notifyAll();
						/* If the process has terminated or the ProcessExecutor has been stopped, return false 
						 * to signal failure. */
						if (!commandCompleted)
							return success;
						if (i < commands.size() - 1)
							processedCommands.add(command);
					}
					command = null;
					if (running && !stop && terminateProcessAfterwards && stopLock.tryLock()) {
						try {
							if (!stop(false))
								stop(true);
						} finally {
							stopLock.unlock();
						}
					}
					success = true;
					return success;
				} finally {
					try {
						if (success)
							submission.onFinishedProcessing();
					} finally {
						command = null;
						onWait = false;
						execLock.notifyAll();
						if (running && !stop && doTime)
							timer.start();
						submissionLock.unlock();
					}
				}
			}
		}
		return false;
	}
	@Override
	public void execute(Submission<?> submission) {
		try {
			if (!execute(submission, false))
				throw new ProcessException("The process is not available for submissions.");
		} catch (IOException | InterruptedException e) {
			throw new ProcessException(e);
		}
	}
	@Override
	public void run() {
		synchronized (runLock) {
			termSemaphore.drainPermits();
			int rc = UNEXPECTED_TERMINATION_RESULT_CODE;
			long lifeTime = 0;
			long startupTime = 0;
			try {
				// Startup block.
				boolean orderly = false;
				submissionLock.lock();
				try {
					synchronized (execLock) {
						if (stop)
							return;
						running = true;
						command = null;
						keepAliveTime = manager.getKeepAliveTime();
						doTime = keepAliveTime > 0;
						timer = doTime && timer == null ? new KeepAliveTimer() : timer;
						threadsToWaitFor.set(doTime ? 3 : 2);
						// Start the process.
						synchronized (processLock) {
							startupTime = System.currentTimeMillis();
							process = manager.start();
						}
						lifeTime = System.currentTimeMillis();
						Charset chars = manager.getEncoding();
						stdOutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), chars));
						stdErrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), chars));
						stdInWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), chars));
						// Handle the startup; check if the process is to be considered immediately started up.
						startedUp = manager.startsUpInstantly();
						threadPool.submit(() -> startListeningToProcess(stdOutReader, true));
						threadPool.submit(() -> startListeningToProcess(stdErrReader, false));
						while (!startedUp) {
							execLock.wait();
							if (stop)
								return;
						}
						manager.onStartup(this);
						if (stop)
							return;
						startupTime = System.currentTimeMillis() - startupTime;
						logger.debug(String.format("Startup time in executor %s: %.3f", this,
								((float) startupTime)/1000));
						if (doTime) {
							// Start the timer.
							threadPool.submit(timer);
							timer.start();
						}
						orderly = true;
					}
				} finally {
					onExecutorStartup(orderly);
					/* If the startup was not orderly, e.g. the process was stopped prematurely or an exception 
					 * was thrown, release as many permits as there are slave threads to ensure that the 
					 * semaphore does not block in the finally clause. */
					if (!orderly)
						termSemaphore.release(threadsToWaitFor.get());
					submissionLock.unlock();
				}
				/* If the startup failed, the process might not be initialized. Otherwise, wait for the process 
				 * to terminate. */
				if (orderly)
					rc = process.waitFor();
			} catch (Exception e) {
				throw new ProcessException(e);
			} finally {
				// Stop the timer.
				if (doTime)
					timer.stop();
				// Make sure the process itself has terminated.
				synchronized (processLock) {
					if (process != null) {
						if (process.isAlive()) {
							process.destroyForcibly();
							try {
								process.waitFor();
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							}
						}
						process = null;
					}
				}
				lifeTime = lifeTime == 0 ? 0 : System.currentTimeMillis() - lifeTime;
				logger.debug(String.format("Process runtime in executor %s: %.3f", this,
						((float) lifeTime)/1000));
				// Make sure that there are no submission currently being executed...
				submissionLock.lock();
				try {
					// Set running to false...
					synchronized (execLock) {
						running = false;
						execLock.notifyAll();
					}
					/* Make sure that the timer sees the new value of running and the timer thread can 
					 * terminate. */
					if (doTime)
						timer.stop();
					onExecutorTermination();
				} finally {
					submissionLock.unlock();
				}
				// Wait for all the slave threads to finish.
				try {
					termSemaphore.acquire(threadsToWaitFor.get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				// Try to shutdown all the streams.
				if (stdOutReader != null) {
					try {
						stdOutReader.close();
					} catch (IOException e) {
						// Ignore it.
					}
				}
				if (stdErrReader != null) {
					try {
						stdErrReader.close();
					} catch (IOException e) {
						// Ignore it.
					}
				}
				if (stdInWriter != null) {
					try {
						stdInWriter.close();
					} catch (IOException e) {
						// Ignore it.
					}
				}
				// The process life cycle is over.
				try {
					manager.onTermination(rc, lifeTime);
				} finally {
					synchronized (execLock) {
						stop = false;
					}
				}
			}
		}
	}
	/**
	 * A method called after the startup of the process and the set up of the executor but before it is 
	 * declared ready for submissions.
	 * 
	 * @param orderly Whether the executor startup has been orderly or not. If it has not been orderly, the 
	 * executor is shut down.
	 */
	protected abstract void onExecutorStartup(boolean orderly);
	/**
	 * A call-back method invoked after the termination of the underlying process and the stopping of the 
	 * executor's helper threads.
	 */
	protected abstract void onExecutorTermination();
	
	/**
	 * A simple timer that stops the process after <code>keepAliveTime</code> milliseconds unless the process 
	 * is inactive or the timer is cancelled. It also enables the timer to be restarted using the same thread.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class KeepAliveTimer implements Runnable {

		boolean go;
		
		/**
		 * Restarts the timer.
		 */
		synchronized void start() {
			go = true;
			notifyAll();
		}
		/**
		 * Stops the timer.
		 */
		synchronized void stop() {
			go = false;
			notifyAll();
		}
		@Override
		public synchronized void run() {
			try {
				while (running && !stop) {
					while (!go) {
						wait();
						if (!running || stop)
							return;
					}
					long waitTime = keepAliveTime;
					while (go && waitTime > 0) {
						long start = System.currentTimeMillis();
						wait(waitTime);
						if (!running || stop)
							return;
						waitTime -= (System.currentTimeMillis() - start);
					}
					/* Normally, the timer should not be running while a submission is being processed, i.e. 
					 * if the timer gets to this point with go set to true, submissionLock should be available to 
					 * the timer thread. However, if the execute method acquires the submissionLock right after the 
					 * timer's wait time elapses, it will not be able to disable the timer until it enters 
					 * the wait method in the next cycle and gives up its intrinsic lock. Therefore, the 
					 * first call of the stop method of the StandardProcessExecutor would fail due to the 
					 * lock held by the thread running the execute method, triggering the forcible shutdown 
					 * of the process even though it is not idle. To avoid this behavior, first the submissionLock 
					 * is attempted to be acquired to ensure that the process is indeed idle. */
					if (go && submissionLock.tryLock()) {
						try {
							if (!AbstractProcessExecutor.this.stop(false))
								AbstractProcessExecutor.this.stop(true);
						} finally {
							submissionLock.unlock();
						}
					}
				}
			} catch (InterruptedException e) {
				// Just let the thread terminate.
			} catch (Exception e) {
				throw new ProcessException(e);
			} finally {
				go = false;
				termSemaphore.release();
			}
		}
		
	}
	
}