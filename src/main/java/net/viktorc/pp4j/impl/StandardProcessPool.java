package net.viktorc.pp4j.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.ProcessExecutor;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.ProcessPool;
import net.viktorc.pp4j.api.Submission;

/**
 * An implementation of the {@link net.viktorc.pp4j.api.ProcessPool} interface for maintaining and managing a pool 
 * of pre-started processes. The processes are executed in instances of an own {@link net.viktorc.pp4j.api.
 * ProcessExecutor} implementation. Each executor is assigned an instance of an implementation of the {@link net.
 * viktorc.pp4j.api.ProcessManager} interface using an implementation of the {@link net.viktorc.pp4j.api.
 * ProcessManagerFactory} interface. The pool accepts submissions in the form of {@link net.viktorc.pp4j.api.
 * Submission} implementations which are executed on any one of the available active process executors maintained 
 * by the pool. While executing a submission, the executor cannot accept further submissions. The submissions are 
 * queued and executed as soon as there is an available executor. The size of the pool is always kept between the 
 * minimum pool size and the maximum pool size (both inclusive). The reserve size specifies the minimum number of 
 * processes that should always be available (there are no guarantees that there actually will be this many available 
 * executors at any given time). It uses <a href="https://www.slf4j.org/">SLF4J</a> for logging.
 * 
 * @author Viktor Csomor
 *
 */
public class StandardProcessPool implements ProcessPool {
	
	/**
	 * If a process cannot be started or an exception occurs which would make it impossible to retrieve the actual 
	 * return code of the process.
	 */
	public static final int UNEXPECTED_TERMINATION_RESULT_CODE = -1;
	/**
	 * The number of milliseconds after which idle process executor instances and the process executor threads are 
	 * evicted if {@link #keepAliveTime} is non-positive.
	 */
	private static final long DEFAULT_EVICT_TIME = 60L*1000;
	
	private final ProcessManagerFactory procManagerFactory;
	private final int minPoolSize;
	private final int maxPoolSize;
	private final int reserveSize;
	private final long keepAliveTime;
	private final boolean verbose;
	private final boolean doTime;
	private final Logger logger;
	private final ExecutorService procExecutorThreadPool;
	private final ExecutorService auxThreadPool;
	private final Queue<StandardProcessExecutor> activeProcExecutors;
	private final StandardProcessExecutorObjectPool procExecutorPool;
	private final LinkedBlockingDeque<InternalSubmission> submissionQueue;
	private final AtomicInteger numOfActiveSubmissions;
	private final CountDownLatch prestartLatch;
	private final Object poolLock;
	private volatile boolean close;

	/**
	 * Constructs a pool of processes. The initial size of the pool is the minimum pool size or the reserve size 
	 * depending on which one is greater. This constructor blocks until the initial number of processes start up. 
	 * The size of the pool is dynamically adjusted based on the pool parameters and the rate of incoming submissions.
	 * 
	 * @param procManagerFactory A {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance that is used to build 
	 * {@link net.viktorc.pp4j.api.ProcessManager} instances that manage the processes' life cycle in the pool.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is 0 or less, 
	 * the life-cycle of the processes will not be limited.
	 * @param verbose Whether the events related to the management of the process pool should be logged. Setting 
	 * this parameter to <code>true</code> does not guarantee that logging will be performed as logging depends on 
	 * the SLF4J binding and the logging configurations, but setting it to <code>false</code> guarantees that no 
	 * logging will be performed by the constructed instance.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start 
	 * up.
	 * @throws IllegalArgumentException If the manager factory is null, or the minimum pool size is less than 0, or 
	 * the maximum pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater 
	 * than the maximum pool size.
	 */
	public StandardProcessPool(ProcessManagerFactory procManagerFactory, int minPoolSize, int maxPoolSize,
			int reserveSize, long keepAliveTime, boolean verbose) throws InterruptedException {
		if (procManagerFactory == null)
			throw new IllegalArgumentException("The process manager factory cannot be null.");
		if (minPoolSize < 0)
			throw new IllegalArgumentException("The minimum pool size has to be greater than 0.");
		if (maxPoolSize < 1 || maxPoolSize < minPoolSize)
			throw new IllegalArgumentException("The maximum pool size has to be at least 1 and at least as great " + 
					"as the minimum pool size.");
		if (reserveSize < 0 || reserveSize > maxPoolSize)
			throw new IllegalArgumentException("The reserve has to be at least 0 and less than the maximum pool " +
					"size.");
		this.procManagerFactory = procManagerFactory;
		this.minPoolSize = minPoolSize;
		this.maxPoolSize = maxPoolSize;
		this.reserveSize = reserveSize;
		this.keepAliveTime = Math.max(0, keepAliveTime);
		this.verbose = verbose;
		doTime = keepAliveTime > 0;
		logger = verbose ? LoggerFactory.getLogger(getClass()) : NOPLogger.NOP_LOGGER;
		procExecutorThreadPool = new StandardProcessPoolExecutor();
		int actualMinSize = Math.max(minPoolSize, reserveSize);
		/* If keepAliveTime is positive, one process requires 4 auxiliary threads (std_out listener, err_out listener,
		 * submission handler, timer); if it is not, only 3 are required. */
		auxThreadPool = new ThreadPoolExecutor(doTime ? 4*actualMinSize : 3*actualMinSize, Integer.MAX_VALUE,
				doTime ? keepAliveTime : DEFAULT_EVICT_TIME, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
				new CustomizedThreadFactory(this + "-auxThreadPool"));
		activeProcExecutors = new LinkedBlockingQueue<>();
		procExecutorPool = new StandardProcessExecutorObjectPool();
		submissionQueue = new LinkedBlockingDeque<>();
		numOfActiveSubmissions = new AtomicInteger(0);
		prestartLatch = new CountDownLatch(actualMinSize);
		poolLock = new Object();
		for (int i = 0; i < actualMinSize && !close; i++) {
			synchronized (poolLock) {
				startNewProcess(null);
			}
		}
		// Wait for the processes in the initial pool to start up.
		prestartLatch.await();
		logger.info("Pool started up.");
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
	 * Returns the number of milliseconds after which idle processes should be terminated. If it is 0 or less, 
	 * the processes are never terminated due to a timeout.
	 * 
	 * @return The number of milliseconds after which idle processes should be terminated.
	 */
	public long getKeepAliveTime() {
		return keepAliveTime;
	}
	/**
	 * Returns whether events relating to the management of the processes held by the pool are logged to the 
	 * console.
	 * 
	 * @return Whether the pool is verbose.
	 */
	public boolean isVerbose() {
		return verbose;
	}
	/**
	 * Returns the number of running processes currently held in the pool.
	 * 
	 * @return The number of running processes.
	 */
	public int getNumOfProcesses() {
		return activeProcExecutors.size();
	}
	/**
	 * Returns the number of submissions currently being executed in the pool.
	 * 
	 * @return The number of submissions currently being executed in the pool.
	 */
	public int getNumOfExecutingSubmissions() {
		return numOfActiveSubmissions.get();
	}
	/**
	 * Returns the number of submissions queued and waiting for execution.
	 * 
	 * @return The number of queued submissions.
	 */
	public int getNumOfQueuedSubmissions() {
		return submissionQueue.size();
	}
	/**
	 * Returns the number of active, queued, and currently executing processes as string.
	 * 
	 * @return A string of statistics concerning the size of the process pool.
	 */
	private String getPoolStats() {
		return "Processes: " + activeProcExecutors.size() + "; active submission: " +
				numOfActiveSubmissions.get() + "; queued submission: " + submissionQueue.size();
	}
	/**
	 * Returns whether a new process {@link net.viktorc.pp4j.StandardProcessExecutor} instance should be started.
	 * 
	 * @return Whether the process pool should be extended.
	 */
	private boolean doExtendPool() {
		return !close && (activeProcExecutors.size() < minPoolSize || (activeProcExecutors.size() <
				Math.min(maxPoolSize, numOfActiveSubmissions.get() + submissionQueue.size() + reserveSize)));
	}
	/**
	 * Starts a new process by executing the provided {@link net.viktorc.pp4j.StandardProcessExecutor}. If it is null, 
	 * it borrows an instance from the pool.
	 * 
	 * @param executor An optional {@link net.viktorc.pp4j.StandardProcessExecutor} instance to re-start in case one 
	 * is available.
	 * @return Whether the process was successfully started.
	 */
	private boolean startNewProcess(StandardProcessExecutor executor) {
		if (executor == null) {
			try {
				executor = procExecutorPool.borrowObject();
			} catch (Exception e) {
				return false;
			}
		}
		procExecutorThreadPool.execute(executor);
		activeProcExecutors.add(executor);
		logger.debug("Process executor {} started.{}", executor, System.lineSeparator() + getPoolStats());
		return true;
	}
	@Override
	public ProcessManagerFactory getProcessManagerFactory() {
		return procManagerFactory;
	}
	@Override
	public Future<Long> submit(Submission submission) {
		if (close)
			throw new IllegalStateException("The pool has already been shut down.");
		if (submission == null)
			throw new IllegalArgumentException("The submission cannot be null or empty.");
		InternalSubmission internalSubmission = new InternalSubmission(submission);
		submissionQueue.addLast(internalSubmission);
		// If necessary, adjust the pool size given the new submission.
		synchronized (poolLock) {
			if (doExtendPool())
				startNewProcess(null);
		}
		logger.info("Submission {} received.{}", internalSubmission, System.lineSeparator() +
				getPoolStats());
		// Return a Future holding the total execution time including the submission delay.
		return new InternalSubmissionFuture(internalSubmission);
	}
	@Override
	public synchronized void shutdown() {
		synchronized (poolLock) {
			if (close)
				throw new IllegalStateException("The pool has already been shut down.");
			logger.info("Initiating shutdown...");
			close = true;
			while (prestartLatch.getCount() != 0)
				prestartLatch.countDown();
			logger.debug("Shutting down process executors...");
			for (StandardProcessExecutor executor : activeProcExecutors) {
				if (!executor.stop(true)) {
					// This should never happen.
					logger.error("Process executor {} could not be stopped.", executor);
				}
			}
			logger.debug("Shutting down thread pools...");
			auxThreadPool.shutdown();
			procExecutorThreadPool.shutdown();
			procExecutorPool.close();
		}
		try {
			auxThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
			procExecutorThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
			for (InternalSubmission submission : submissionQueue)
				submission.setException(new Exception("The process pool has been shut down."));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		logger.info("Process pool shut down.");
	}
	@Override
	public String toString() {
		return String.format("stdProcPool@%s", Integer.toHexString(hashCode()));
	}
	
	/**
	 * An implementation of the {@link net.viktorc.pp4j.InternalSubmission} interface to keep track of the number of 
	 * commands being executed at a time and to establish a mechanism for cancelling submitted commands via the 
	 * {@link java.util.concurrent.Future} returned by the 
	 * {@link net.viktorc.pp4j.impl.StandardProcessPool#submit(Submission)} method.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class InternalSubmission implements Submission {
		
		final Submission origSubmission;
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
		 * @param origSubmission The submission to wrap into an internal submission with extended features.
		 * @throws IllegalArgumentException If the submission is null.
		 */
		InternalSubmission(Submission originalSubmission) {
			if (originalSubmission == null)
				throw new IllegalArgumentException("The submission cannot be null.");
			this.origSubmission = originalSubmission;
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
			// Notify the InternalSubmissionFuture that an exception was thrown while processing the submission.
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
		public boolean doTerminateProcessAfterwards() {
			return origSubmission.doTerminateProcessAfterwards();
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
			// Notify the InternalSubmissionFuture that the submission has been processed.
			synchronized (lock) {
				processed = true;
				lock.notifyAll();
			}
		}
		@Override
		public String toString() {
			return String.format("{commands:[%s],terminate:%s}@%s", String.join(",", origSubmission.getCommands()
					.stream().map(c -> "\"" + c.getInstruction() + "\"").collect(Collectors.toList())),
					Boolean.toString(origSubmission.doTerminateProcessAfterwards()),
					Integer.toHexString(hashCode()));
		}
		
	}
	
	/**
	 * An implementation of {@link java.util.concurrent.Future} that returns the time it took to process the 
	 * submission in nanoseconds.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class InternalSubmissionFuture implements Future<Long> {
		
		final InternalSubmission submission;
		
		/**
		 * Constructs a {@link java.util.concurrent.Future} for the specified submission.
		 * 
		 * @param submission The submission to get a {@link java.util.concurrent.Future} for.
		 */
		InternalSubmissionFuture(InternalSubmission submission) {
			this.submission = submission;
		}
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			synchronized (submission.lock) {
				/* If the submission has already been cancelled or if it has already been processed, don't do 
				 * anything and return false. */
				if (submission.cancelled || submission.processed)
					return false;
				// If it is already being processed and mayInterruptIfRunning is true, interrupt the executor thread.
				if (submission.thread != null) {
					if (mayInterruptIfRunning) {
						submission.cancel();
						submission.thread.interrupt();
					}
					// If mayInterruptIfRunning is false, don't let the submission be cancelled.
				} else
					// If the processing of the submission has not commenced yet, cancel it.
					submission.cancel();
				return submission.cancelled;
			}
		}
		@Override
		public Long get() throws InterruptedException, ExecutionException, CancellationException {
			// Wait until the submission is processed, or cancelled, or fails.
			synchronized (submission.lock) {
				while (!submission.processed && !submission.cancelled && submission.exception == null)
					submission.lock.wait();
				if (submission.cancelled)
					throw new CancellationException(String.format("Submission %s cancelled.", submission));
				if (submission.exception != null)
					throw new ExecutionException(submission.exception);
				return submission.processedTime - submission.receivedTime;
			}
		}
		@Override
		public Long get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
			// Wait until the submission is processed, or cancelled, or fails, or the method times out.
			synchronized (submission.lock) {
				long timeoutNs = unit.toNanos(timeout);
				long start = System.nanoTime();
				while (!submission.processed && !submission.cancelled && submission.exception == null &&
						timeoutNs > 0) {
					submission.lock.wait(timeoutNs/1000000, (int) (timeoutNs%1000000));
					timeoutNs -= (System.nanoTime() - start);
				}
				if (submission.cancelled)
					throw new CancellationException(String.format("Submission %s cancelled.", submission));
				if (submission.exception != null)
					throw new ExecutionException(submission.exception);
				if (timeoutNs <= 0)
					throw new TimeoutException(String.format("Submission %s timed out.", submission));
				return timeoutNs <= 0 ? null : (submission.processedTime - submission.receivedTime);
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
	 * An implementation of the {@link net.viktorc.pp4j.api.ProcessExecutor} interface for starting, managing, and 
	 * interacting with a process. The life cycle of the associated process is the same as that of the {@link #run()} 
	 * method of the instance. The process is not started until this method is called and the method does not terminate 
	 * until the process does.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class StandardProcessExecutor implements ProcessExecutor, Runnable {
		
		final ProcessManager manager;
		final KeepAliveTimer timer;
		final Semaphore termSemaphore;
		final ReentrantLock subLock;
		final ReentrantLock stopLock;
		final Object runLock;
		final Object execLock;
		final Object threadLock;
		final Object processLock;
		Process process;
		BufferedReader stdOutReader;
		BufferedReader errOutReader;
		BufferedWriter stdInWriter;
		Thread subThread;
		Command command;
		boolean commandProcessed;
		boolean startedUp;
		volatile boolean running;
		volatile boolean stop;
		
		/**
		 * Constructs an executor for the specified process using two threads to listen to the out streams of the 
		 * process, one for listening to the submission queue, and one thread for ensuring that the process is 
		 * terminated once it times out if <code>keepAliveTime</code> is greater than 0.
		 */
		StandardProcessExecutor() {
			timer = doTime ? new KeepAliveTimer() : null;
			this.manager = procManagerFactory.newProcessManager();
			termSemaphore = new Semaphore(0);
			subLock = new ReentrantLock();
			stopLock = new ReentrantLock();
			runLock = new Object();
			execLock = new Object();
			threadLock = new Object();
			processLock = new Object();
		}
		/**
		 * Starts listening to an out stream of the process using the specified reader.
		 * 
		 * @param reader The buffered reader to use to listen to the steam.
		 * @param standard Whether it is the standard out or the error out stream of the process.
		 */
		void startListeningToProcess(BufferedReader reader, boolean standard) {
			try {
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty())
						continue;
					// Make sure that the submission executor thread is waiting.
					synchronized (execLock) {
						if (startedUp) {
							commandProcessed = command == null || command.onNewOutput(line, standard);
							if (commandProcessed)
								execLock.notifyAll();
						} else {
							startedUp = manager.isStartedUp(line, standard);
							if (startedUp)
								execLock.notifyAll();
						}
					}
				}
			} catch (IOException e) {
				throw new ProcessException(e);
			} finally {
				termSemaphore.release();
			}
		}
		/**
		 * Starts waiting on the blocking queue of submissions executing available ones one at a time.
		 */
		void startHandlingSubmissions() {
			synchronized (threadLock) {
				subThread = Thread.currentThread();
			}
			try {
				while (running && !stop) {
					InternalSubmission submission = null;
					boolean submissionRetrieved = false;
					try {
						/* Wait until the startup phase is over and the mainLock is available to avoid retrieving 
						 * a submission only to find that it cannot be executed and thus has to be put back into 
						 * the queue. */
						subLock.lock();
						subLock.unlock();
						// Wait for an available submission.
						submission = submissionQueue.takeFirst();
						// Increment the counter for active submissions to keep track of the number of busy processes.
						numOfActiveSubmissions.incrementAndGet();
						submissionRetrieved = true;
						submission.setThread(subThread);
						if (submission.isCancelled()) {
							submission = null;
							continue;
						}
						/* It can happen of course that in the mean time, the mainLock has been stolen (for terminating 
						 * the process) or that the process is already terminated, and thus the execute method fails. 
						 * In this case, the submission is put back into the queue. */
						if (execute(submission)) {
							logger.info(String.format("Submission %s processed; delay: %.3f; execution time: %.3f.%n%s",
									submission, (float) ((double) (submission.submittedTime - submission.receivedTime)/
									1000000000), (float) ((double) (submission.processedTime - submission.submittedTime)/
									1000000000), getPoolStats()));
							submission = null;
						}
					} catch (InterruptedException e) {
						// Next round (unless the process is stopped).
						continue;
					} catch (Exception e) {
						// Signal the exception to the future and do not put the submission back into the queue.
						if (submission != null) {
							logger.warn(String.format("Exception while executing submission %s.%n%s", 
									submission, getPoolStats()), e);
							submission.setException(e);
							submission = null;
						}
					} finally {
						/* If the execute method failed and there was no exception thrown, put the submission back 
						 * into the queue at the front. */
						if (submission != null) {
							if (!submission.isCancelled()) {
								submission.setThread(null);
								submissionQueue.addFirst(submission);
							}
						}
						// Decrement the active submissions counter if it was incremented in this cycle.
						if (submissionRetrieved)
							numOfActiveSubmissions.decrementAndGet();
					}
				}
			} finally {
				synchronized (threadLock) {
					subThread = null;
				}
				termSemaphore.release();
			}
		}
		/**
		 * It prompts the currently running process, if there is one, to terminate. Once the process has been 
		 * successfully terminated, subsequent calls are ignored and return true unless the process is started 
		 * again.
		 * 
		 * @param forcibly Whether the process should be killed forcibly or using the 
		 * {@link net.viktorc.pp4j.api.ProcessManager#terminate(ProcessExecutor)} method of the 
		 * {@link net.viktorc.pp4j.api.ProcessManager} instance assigned to the executor. The latter might be 
		 * ineffective if the process is currently executing a command or has not started up.
		 * @return Whether the process was successfully terminated.
		 */
		boolean stop(boolean forcibly) {
			stopLock.lock();
			try {
				if (stop)
					return true;
				synchronized (execLock) {
					boolean success = true;
					if (running) {
						if (!forcibly)
							success = manager.terminate(this);
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
		@Override
		public boolean execute(Submission submission)
				throws IOException, InterruptedException, CancellationException {
			if (subLock.tryLock()) {
				// Make sure that the reader thread can only process output lines if this one is ready and waiting.
				synchronized (execLock) {
					boolean success = false;
					try {
						/* If the process has terminated or the ProcessExecutor has been stopped while acquiring the 
						 * execLock, return. */
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
							commandProcessed = !command.generatesOutput();
							stdInWriter.write(command.getInstruction());
							stdInWriter.newLine();
							stdInWriter.flush();
							while (running && !stop && !commandProcessed)
								execLock.wait();
							/* If the process has terminated or the ProcessExecutor has been stopped, return false 
							 * to signal failure. */
							if (!commandProcessed)
								return success;
							if (i < commands.size() - 1)
								processedCommands.add(command);
							command = null;
						}
						if (running && !stop && submission.doTerminateProcessAfterwards() &&
								stopLock.tryLock()) {
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
							if (running && !stop && doTime)
								timer.start();
							subLock.unlock();
						}
					}
				}
			}
			return false;
		}
		@Override
		public void run() {
			synchronized (runLock) {
				termSemaphore.drainPermits();
				int rc = UNEXPECTED_TERMINATION_RESULT_CODE;
				long lifeTime = 0;
				try {
					subLock.lock();
					boolean orderly = false;
					try {
						// Start the process
						synchronized (execLock) {
							if (stop)
								return;
							running = true;
							command = null;
							// Start the process.
							synchronized (processLock) {
								process = manager.start();
							}
							lifeTime = System.currentTimeMillis();
							stdOutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
							errOutReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
							stdInWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
							// Handle the startup.
							startedUp = manager.startsUpInstantly();
							auxThreadPool.submit(() -> startListeningToProcess(stdOutReader, true));
							auxThreadPool.submit(() -> startListeningToProcess(errOutReader, false));
							while (!startedUp) {
								execLock.wait();
								if (stop)
									return;
							}
							manager.onStartup(this);
							if (stop)
								return;
							// Start accepting submissions.
							auxThreadPool.submit(this::startHandlingSubmissions);
							if (doTime) {
								// Start the timer.
								auxThreadPool.submit(timer);
								timer.start();
							}
							orderly = true;
							prestartLatch.countDown();
						}
					} finally {
						/* If the startup was not orderly, e.g. the process was stopped prematurely or an exception 
						 * was thrown, release as many permits as there are slave threads to ensure that the semaphore 
						 * does not block in the finally clause; also count down on the pre-start latch to avoid 
						 * having the pool wait on the failed startup. */
						if (!orderly) {
							termSemaphore.release(doTime ? 4 : 3);
							prestartLatch.countDown();
						}
						subLock.unlock();
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
								process.destroy();
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
					subLock.lock();
					try {
						// Set running to false...
						synchronized (execLock) {
							running = false;
							execLock.notifyAll();
						}
						/* And interrupt the submission handler thread to avoid having it stuck waiting for 
						 * submissions forever in case the queue is empty. */
						synchronized (threadLock) {
							if (subThread != null)
								subThread.interrupt();
						}
						// Make sure that the timer sees the new value of running and the timer thread can terminate.
						if (doTime)
							timer.stop();
					} finally {
						subLock.unlock();
					}
					// Wait for all the slave threads to finish.
					try {
						termSemaphore.acquire(doTime ? 4 : 3);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					// Try to close all the streams.
					if (stdOutReader != null) {
						try {
							stdOutReader.close();
						} catch (IOException e) {
							// Ignore it.
						}
					}
					if (errOutReader != null) {
						try {
							errOutReader.close();
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
		@Override
		public String toString() {
			return String.format("%s-stdProcExecutor@%s", StandardProcessPool.this,
					Integer.toHexString(hashCode()));
		}
		
		/**
		 * A simple timer that stops the process after <code>keepAliveTime</code> milliseconds unless the process is 
		 * inactive or the timer is cancelled. It also enables the timer to be restarted using the same thread.
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
						/* Normally, the timer should not be running while a submission is being processed, i.e. if 
						 * the timer gets to this point with go set to true, subLock should be available to the 
						 * timer thread. However, if the execute method acquires the subLock right after the 
						 * timer's wait time elapses, it will not be able to disable the timer until it enters the 
						 * wait method in the next cycle and gives up its intrinsic lock. Therefore, the first call 
						 * of the stop method of the StandardProcessExecutor would fail due to the lock held by the 
						 * thread running the execute method, triggering the forcible shutdown of the process even 
						 * though it is not idle. To avoid this behavior, first the subLock is attempted to be 
						 * acquired to ensure that the process is indeed idle. */
						if (go && subLock.tryLock()) {
							try {
								if (!StandardProcessExecutor.this.stop(false))
									StandardProcessExecutor.this.stop(true);
							} finally {
								subLock.unlock();
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
	
	/**
	 * A sub-class of {@link org.apache.commons.pool2.impl.GenericObjectPool} for the pooling of 
	 * {@link net.viktorc.pp4j.StandardProcessExecutor} instances.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class StandardProcessExecutorObjectPool extends GenericObjectPool<StandardProcessExecutor> {

		/**
		 * Constructs an object pool instance to facilitate the reuse of {@link net.viktorc.pp4j.StandardProcessExecutor} 
		 * instances. The pool does not block if there are no available objects, it accommodates <code>maxPoolSize</code> 
		 * objects, and if there are more than <code>Math.max(minPoolSize, reserveSize)</code> idle objects in the pool, 
		 * excess idle objects are eligible for eviction after <code>keepAliveTime</code> milliseconds, or if it is 
		 * non-positive, after <code>DEFAULT_EVICT_TIME</code> milliseconds. The eviction thread runs at the above 
		 * specified intervals and performs at most <code>maxPoolSize - minPoolSize</code> evictions per run.
		 */
		StandardProcessExecutorObjectPool() {
			super(new PooledObjectFactory<StandardProcessExecutor>() {

				@Override
				public PooledObject<StandardProcessExecutor> makeObject() throws Exception {
					return new DefaultPooledObject<StandardProcessExecutor>(new StandardProcessExecutor());
				}
				@Override
				public void activateObject(PooledObject<StandardProcessExecutor> p) {
					// No-operation.
				}
				@Override
				public boolean validateObject(PooledObject<StandardProcessExecutor> p) {
					return true;
				}
				@Override
				public void passivateObject(PooledObject<StandardProcessExecutor> p) {
					// No-operation
				}
				@Override
				public void destroyObject(PooledObject<StandardProcessExecutor> p) {
					// No-operation.
				}
			});
			setBlockWhenExhausted(false);
			setMaxTotal(maxPoolSize);
			setMaxIdle(Math.max(minPoolSize, reserveSize));
			long evictTime = doTime ? keepAliveTime : DEFAULT_EVICT_TIME;
			setTimeBetweenEvictionRunsMillis(evictTime);
			setSoftMinEvictableIdleTimeMillis(evictTime);
			setNumTestsPerEvictionRun(maxPoolSize - minPoolSize);
		}
		
	}
	
	/**
	 * An implementation the {@link java.util.concurrent.ThreadFactory} interface that provides more descriptive thread 
	 * names and extends the {@link java.lang.Thread.UncaughtExceptionHandler} of the created threads by logging the 
	 * uncaught exceptions if the enclosing {@link net.viktorc.pp4j.impl.StandardProcessPool} instance is verbose. It 
	 * also attempts to shut down the enclosing pool if a {@link net.viktorc.pp4j.impl.ProcessException} is thrown in 
	 * one of the threads it created.
	 * 
	 * @author Viktor Csomor
	 *
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
			Thread t = defaultFactory.newThread(r);
			t.setName(t.getName().replaceFirst("pool-[0-9]+", poolName));
			t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
				
				@Override
				public void uncaughtException(Thread t, Throwable e) {
					// Log the exception whether verbose or not.
					logger.error(e.getMessage(), e);
					StandardProcessPool.this.shutdown();
				}
			});
			return t;
		}
		
	}
	
	/**
	 * A sub-class of {@link java.util.concurrent.ThreadPoolExecutor} for the execution of {@link net.viktorc.pp4j.impl.
	 * StandardProcessPool.StandardProcessExecutor} instances. It utilizes an extension of the {@link java.util.
	 * concurrent.LinkedTransferQueue} and an implementation of the {@link java.util.concurrent.RejectedExecutionHandler} 
	 * as per Robert Tupelo-Schneck's answer to a StackOverflow <a href="https://stackoverflow.com/questions/19528304/how
	 * -to-get-the-threadpoolexecutor-to-increase-threads-to-max-before-queueing/19528305#19528305">question</a> to 
	 * facilitate a queueing logic that has the pool first increase the number of its threads and only really queue tasks 
	 * once the maximum pool size has been reached.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class StandardProcessPoolExecutor extends ThreadPoolExecutor {
		
		/**
		 * Constructs thread pool for the execution of {@link net.viktorc.pp4j.StandardProcessExecutor} instances. If 
		 * there are more than <code>Math.max(minPoolSize, reserveSize)</code> idle threads in the pool, excess threads 
		 * are evicted after <code>keepAliveTime</code> milliseconds, or if it is non-positive, after <code>
		 * DEFAULT_EVICT_TIME</code> milliseconds.
		 */
		StandardProcessPoolExecutor() {
			super(Math.max(minPoolSize, reserveSize), maxPoolSize, doTime ? keepAliveTime : DEFAULT_EVICT_TIME,
					TimeUnit.MILLISECONDS, new LinkedTransferQueue<Runnable>() {
				
						private static final long serialVersionUID = 1L;

						@Override
						public boolean offer(Runnable r) {
							/* If there is at least one thread waiting on the queue, delegate the task immediately; 
							 * else decline it and force the pool to create a new thread for running the task. */
							return tryTransfer(r);
						}
					}, new CustomizedThreadFactory(StandardProcessPool.this + "-procExecThreadPool"),
					new RejectedExecutionHandler() {
						
						@Override
						public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
							try {
								/* If there are no threads waiting on the queue (all of them are busy executing) and 
								 * the maximum pool size has been reached, when the queue declines the offer, the pool 
								 * will not create any more threads but call this handler instead. This handler 
								 * 'forces' the declined task on the queue, ensuring that it is not rejected. */
								executor.getQueue().put(r);
							} catch (InterruptedException e) {
								// Should not happen.
								Thread.currentThread().interrupt();
							}
						}
					});
		}
		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			super.afterExecute(r, t);
			StandardProcessExecutor executor = (StandardProcessExecutor) r;
			activeProcExecutors.remove(executor);
			logger.debug("Process executor {} stopped.{}", executor, System.lineSeparator() + getPoolStats());
			/* A process has terminated. Extend the pool if necessary by directly reusing the ProcessExecutor instance. 
			 * If not, return it to the pool. */
			synchronized (poolLock) {
				if (doExtendPool())
					startNewProcess(executor);
				else
					procExecutorPool.returnObject(executor);
			}
		}
		
	}
	
}