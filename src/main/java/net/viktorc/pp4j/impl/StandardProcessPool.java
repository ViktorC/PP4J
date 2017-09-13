package net.viktorc.pp4j.impl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
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
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
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
 * of pre-started processes. The processes are executed in instances of an own 
 * {@link net.viktorc.pp4j.api.ProcessExecutor} implementation. Each executor is assigned an instance of an 
 * implementation of the {@link net.viktorc.pp4j.api.ProcessManager} interface using an implementation of the 
 * {@link net.viktorc.pp4j.api.ProcessManagerFactory} interface. The pool accepts submissions in the form of 
 * {@link net.viktorc.pp4j.api.Submission} implementations which are executed on any one of the available active 
 * process executors maintained by the pool. While executing a submission, the executor cannot accept further 
 * submissions. The submissions are queued and executed as soon as there is an available executor. The size of the 
 * pool is always kept between the minimum pool size and the maximum pool size (both inclusive). The reserve size 
 * specifies the minimum number of processes that should always be available (there are no guarantees that there 
 * actually will be this many available executors at any given time). It uses 
 * <a href="https://www.slf4j.org/">SLF4J</a> for logging.
 * 
 * @author Viktor Csomor
 *
 */
public class StandardProcessPool implements ProcessPool {
	
	/**
	 * The number of milliseconds after which idle process executor instances and process executor threads are 
	 * evicted from the object pool and the thread pool respectively.
	 */
	protected static final long EVICT_TIME = 60L*1000;
	
	private final ProcessManagerFactory procManagerFactory;
	private final int minPoolSize;
	private final int maxPoolSize;
	private final int reserveSize;
	private final boolean verbose;
	private final StandardProcessPoolExecutor procExecutorThreadPool;
	private final StandardProcessExecutorObjectPool procExecutorPool;
	private final ExecutorService auxThreadPool;
	private final Queue<StandardProcessExecutor> activeProcExecutors;
	private final BlockingDeque<InternalSubmission<?>> submissionQueue;
	private final CountDownLatch prestartLatch;
	private final CountDownLatch poolTermLatch;
	private final Lock shutdownLock;
	private final Lock poolLock;
	private final Logger logger;
	private volatile int numOfSubmissions;
	private volatile boolean shutdown;

	/**
	 * Constructs a pool of processes. The initial size of the pool is the minimum pool size or the reserve size 
	 * depending on which one is greater. This constructor blocks until the initial number of processes start up. 
	 * The size of the pool is dynamically adjusted based on the pool parameters and the rate of incoming 
	 * submissions.
	 * 
	 * @param procManagerFactory A {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance that is used to 
	 * build {@link net.viktorc.pp4j.api.ProcessManager} instances that manage the processes' life cycle in the 
	 * pool.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
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
			int reserveSize, boolean verbose) throws InterruptedException {
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
		this.verbose = verbose;
		procExecutorThreadPool = new StandardProcessPoolExecutor();
		procExecutorPool = new StandardProcessExecutorObjectPool();
		int actualMinSize = Math.max(minPoolSize, reserveSize);
		/* One normal process requires minimum 2 auxiliary threads (stdout listener, submission handler), 3 if 
		 * the stderr is not redirected to stdout (stderr listener), and 4 if keepAliveTime is positive (timer). */
		auxThreadPool = new ThreadPoolExecutor(2*actualMinSize, Integer.MAX_VALUE, EVICT_TIME,
				TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
				new CustomizedThreadFactory(this + "-auxThreadPool"));
		submissionQueue = new LinkedBlockingDeque<>();
		activeProcExecutors = new LinkedBlockingQueue<>();
		prestartLatch = new CountDownLatch(actualMinSize);
		poolTermLatch = new CountDownLatch(1);
		shutdownLock = new ReentrantLock();
		poolLock = new ReentrantLock(true);
		logger = verbose ? LoggerFactory.getLogger(getClass()) : NOPLogger.NOP_LOGGER;
		for (int i = 0; i < actualMinSize && !shutdown; i++) {
			poolLock.lock();
			try {
				startNewProcess();
			} finally {
				poolLock.unlock();
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
		return "Processes: " + activeProcExecutors.size() + "; submissions: " + numOfSubmissions;
	}
	/**
	 * Returns whether a new {@link StandardProcessExecutor} instance should be started.
	 * 
	 * @return Whether the process pool should be extended.
	 */
	private boolean doExtendPool() {
		return !shutdown && (activeProcExecutors.size() < minPoolSize || (activeProcExecutors.size() <
				Math.min(maxPoolSize, numOfSubmissions + reserveSize)));
	}
	/**
	 * Starts a new process by borrowing a {@link net.viktorc.pp4j.impl.StandardProcessPool.StandardProcessExecutor} 
	 * instance from the pool and executing it.
	 * 
	 * @return Whether the process was successfully started.
	 */
	private boolean startNewProcess() {
		StandardProcessExecutor executor = null;
		try {
			executor = procExecutorPool.borrowObject();
		} catch (Exception e) {
			return false;
		}
		procExecutorThreadPool.execute(executor);
		activeProcExecutors.add(executor);
		logger.debug("Process executor {} started.{}", executor, System.lineSeparator() + getPoolStats());
		return true;
	}
	/**
	 * Waits until all submissions are completed and calls {@link #syncForceShutdown()}.
	 */
	private void syncShutdown() {
		synchronized (submissionQueue) {
			logger.info("Waiting for submissions to complete...");
			try {
				while (numOfSubmissions > 0)
					submissionQueue.wait();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		syncForceShutdown();
	}
	/**
	 * Kills all the processes, shuts down the pool, and waits for termination.
	 */
	private void syncForceShutdown() {
		shutdownLock.lock();
		try {
			poolLock.lock();
			try {
				while (prestartLatch.getCount() != 0)
					prestartLatch.countDown();
				logger.debug("Shutting down process executors...");
				for (StandardProcessExecutor executor : activeProcExecutors) {
					if (!executor.stop(true)) // This should never happen.
						logger.error("Process executor {} could not be stopped.", executor);
				}
				logger.debug("Shutting down thread pools...");
				auxThreadPool.shutdown();
				procExecutorThreadPool.shutdown();
				procExecutorPool.close();
			} finally {
				poolLock.unlock();
			}
			try {
				auxThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
				procExecutorThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
				for (InternalSubmission<?> submission : submissionQueue)
					submission.setException(new Exception("The process pool has been shut down."));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			logger.info("Process pool shut down.");
			poolTermLatch.countDown();
		} finally {
			shutdownLock.unlock();
		}
	}
	@Override
	public ProcessManagerFactory getProcessManagerFactory() {
		return procManagerFactory;
	}
	@Override
	public <T> Future<T> submit(Submission<T> submission) {
		if (submission == null)
			throw new IllegalArgumentException("The submission cannot be null or empty.");
		InternalSubmission<T> internalSubmission;
		synchronized (submissionQueue) {
			if (shutdown)
				throw new RejectedExecutionException("The pool has already been shut down.");
			numOfSubmissions++;
			internalSubmission = new InternalSubmission<>(submission);
			submissionQueue.addLast(internalSubmission);
		}
		// If necessary, adjust the pool size given the new submission.
		poolLock.lock();
		try {
			if (doExtendPool())
				startNewProcess();
		} finally {
			poolLock.unlock();
		}
		logger.info("Submission {} received.{}", internalSubmission, System.lineSeparator() +
				getPoolStats());
		// Return a Future holding the total execution time including the submission delay.
		return new InternalSubmissionFuture<T>(internalSubmission);
	}
	@Override
	public void shutdown() {
		if (!shutdown && shutdownLock.tryLock()) {
			try {
				shutdown = true;
				(new Thread(this::syncShutdown)).start();
			} finally {
				shutdownLock.unlock();
			}
		}
	}
	@Override
	public List<Submission<?>> forceShutdown() {
		List<Submission<?>> queuedSubmissions = new ArrayList<>();
		synchronized (submissionQueue) {
			shutdown = true;
			/* Using the stream here resulted in strange behaviour that had the LBDSpliterator 
			 * freeze forever (not waiting on any lock)  presumably in response to concurrent 
			 * modification. */
			for (InternalSubmission<?> s : submissionQueue)
				queuedSubmissions.add(s.origSubmission);
		}
		if (poolTermLatch.getCount() != 0 && shutdownLock.tryLock()) {
			try {
				(new Thread(this::syncForceShutdown)).start();
			} finally {
				shutdownLock.unlock();
			}
		}
		return queuedSubmissions;
	}
	@Override
	public boolean isShutdown() {
		return shutdown;
	}
	@Override
	public boolean isTerminated() {
		return poolTermLatch.getCount() == 0;
	}
	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return poolTermLatch.await(timeout, unit);
	}
	@Override
	public String toString() {
		return String.format("stdProcPool@%s", Integer.toHexString(hashCode()));
	}
	
	/**
	 * An implementation of the {@link net.viktorc.pp4j.api.Submission} interface for wrapping other instances 
	 * of the interface into a class that allows for waiting for the completion of the submission, the 
	 * cancellation thereof, and the tracking of the time its processing took.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class InternalSubmission<T> implements Submission<T> {
		
		final Submission<T> origSubmission;
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
		InternalSubmission(Submission<T> originalSubmission) {
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
		public T getResult() throws ExecutionException {
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
	 * An implementation of {@link java.util.concurrent.Future} that can be used to cancel, wait on, and retrieve 
	 * the return value of a {@link net.viktorc.pp4j.impl.InternalSubmission} instance.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class InternalSubmissionFuture<T> implements Future<T> {
		
		final InternalSubmission<T> submission;
		
		/**
		 * Constructs a {@link java.util.concurrent.Future} for the specified submission.
		 * 
		 * @param submission The submission to get a {@link java.util.concurrent.Future} for.
		 */
		InternalSubmissionFuture(InternalSubmission<T> submission) {
			this.submission = submission;
		}
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			synchronized (submission.lock) {
				/* If the submission has already been cancelled or if it has already been processed, don't do 
				 * anything and return false. */
				if (submission.cancelled || submission.processed)
					return false;
				/* If it is already being processed and mayInterruptIfRunning is true, interrupt the executor 
				 * thread. */
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
		public T get() throws InterruptedException, ExecutionException, CancellationException {
			// Wait until the submission is processed, or cancelled, or fails.
			synchronized (submission.lock) {
				while (!submission.processed && !submission.cancelled && submission.exception == null)
					submission.lock.wait();
				if (submission.cancelled)
					throw new CancellationException(String.format("Submission %s cancelled.", submission));
				if (submission.exception != null)
					throw new ExecutionException(submission.exception);
				return submission.getResult();
			}
		}
		@Override
		public T get(long timeout, TimeUnit unit)
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
				return submission.getResult();
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
	 * An implementation of the {@link net.viktorc.pp4j.api.ProcessExecutor} interface for starting, managing, 
	 * and interacting with a process. The life cycle of the associated process is the same as that of the 
	 * {@link #run()} method of the instance. The process is not started until this method is called and the 
	 * method does not terminate until the process does.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class StandardProcessExecutor implements ProcessExecutor, Runnable {
		
		/**
		 * If a process cannot be started or an exception occurs which would make it impossible to retrieve the 
		 * actual return code of the process.
		 */
		public static final int UNEXPECTED_TERMINATION_RESULT_CODE = -1;
		
		final ProcessManager manager;
		final Lock runLock;
		final Lock stopLock;
		final Lock submissionLock;
		final Object execLock;
		final Object processLock;
		final Object subThreadLock;
		final Semaphore termSemaphore;
		Process process;
		KeepAliveTimer timer;
		BufferedReader stdOutReader;
		BufferedReader stdErrReader;
		BufferedWriter stdInWriter;
		Thread subThread;
		Command command;
		long keepAliveTime;
		boolean terminating;
		boolean doTime;
		boolean onWait;
		boolean commandCompleted;
		boolean startedUp;
		volatile boolean running;
		volatile boolean stop;
		
		/**
		 * Constructs an executor for the specified process using two threads to listen to the out streams of 
		 * the process, one for listening to the submission queue, and one thread for ensuring that the process 
		 * is terminated once it times out if <code>keepAliveTime</code> is greater than 0.
		 */
		StandardProcessExecutor() {
			manager = procManagerFactory.newProcessManager();
			runLock = new ReentrantLock(true);
			stopLock = new ReentrantLock(true);
			submissionLock = new ReentrantLock();
			execLock = new Object();
			processLock = new Object();
			subThreadLock = new Object();
			termSemaphore = new Semaphore(0);
		}
		/**
		 * Starts listening to an out stream of the process using the specified reader.
		 * 
		 * @param reader The buffered reader to use to listen to the steam.
		 * @param standard Whether it is the standard out or the standard error stream of the process.
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
		 * Starts waiting on the blocking queue of submissions executing available ones one at a time.
		 */
		void startHandlingSubmissions() {
			synchronized (subThreadLock) {
				subThread = Thread.currentThread();
			}
			try {
				while (running && !stop) {
					InternalSubmission<?> submission = null;
					boolean submissionRetrieved = false;
					boolean completed = false;
					try {
						/* Wait until the startup phase is over and the mainLock is available to avoid retrieving 
						 * a submission only to find that it cannot be executed and thus has to be put back into 
						 * the queue. */
						submissionLock.lock();
						submissionLock.unlock();
						// Wait for an available submission.
						submission = submissionQueue.takeFirst();
						submissionRetrieved = true;
						submission.setThread(subThread);
						if (submission.isCancelled()) {
							submission = null;
							continue;
						}
						/* It can happen of course that in the mean time, the mainLock has been stolen (for 
						 * terminating the process) or that the process is already terminated, and thus the 
						 * execute method fails. In this case, the submission is put back into the queue. */
						if (execute(submission)) {
							logger.info(String.format("Submission %s processed; delay: %.3f; " +
									"execution time: %.3f.%n%s", submission, (float) ((double)
									(submission.submittedTime - submission.receivedTime)/1000000000),
									(float) ((double) (submission.processedTime -
									submission.submittedTime)/1000000000), getPoolStats()));
							submission = null;
						}
						completed = true;
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
						/* If the execute method failed and there was no exception thrown, put the submission 
						 * back into the queue at the front. */
						if (submission != null && !submission.isCancelled()) {
							submission.setThread(null);
							submissionQueue.addFirst(submission);
						} else if (submissionRetrieved) {
							// If the pool is shutdown and there are no more submissions left, signalize it.
							synchronized (submissionQueue) {
								numOfSubmissions--;
								if (shutdown && numOfSubmissions == 0)
									submissionQueue.notifyAll();
							}
						}
						// If the submission did not complete due to an error or cancellation, kill the process.
						if (!completed) {
							submissionLock.lock();
							try {
								stop(true);
							} finally {
								submissionLock.unlock();
							}
						}
					}
				}
			} finally {
				synchronized (subThreadLock) {
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
		 * {@link net.viktorc.pp4j.api.ProcessManager#terminateGracefully(ProcessExecutor)} method of the 
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
		@Override
		public boolean execute(Submission<?> submission)
				throws IOException, InterruptedException, CancellationException {
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
						if (running && !stop && !terminating && submission.doTerminateProcessAfterwards() &&
								stopLock.tryLock()) {
							/* Prevent infinite loops in case a terminal-submission is used for terminating the 
							 * process */
							terminating = true;
							try {
								if (!stop(false))
									stop(true);
							} finally {
								terminating = false;
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
		public void run() {
			synchronized (runLock) {
				termSemaphore.drainPermits();
				int rc = UNEXPECTED_TERMINATION_RESULT_CODE;
				long lifeTime = 0;
				long startupTime = 0;
				try {
					submissionLock.lock();
					boolean orderly = false;
					try {
						// Start the process
						synchronized (execLock) {
							if (stop)
								return;
							running = true;
							command = null;
							keepAliveTime = manager.getKeepAliveTime();
							doTime = keepAliveTime > 0;
							timer = doTime && timer == null ? new KeepAliveTimer() : timer;
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
							auxThreadPool.submit(() -> startListeningToProcess(stdOutReader, true));
							auxThreadPool.submit(() -> startListeningToProcess(stdErrReader, false));
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
						 * was thrown, release as many permits as there are slave threads to ensure that the 
						 * semaphore does not block in the finally clause; also count down on the pre-start latch 
						 * to avoid having the pool wait on the failed startup. */
						if (!orderly) {
							termSemaphore.release(doTime ? 4 : 3);
							prestartLatch.countDown();
						}
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
						/* And interrupt the submission handler thread to avoid having it stuck waiting for 
						 * submissions forever in case the queue is empty. */
						synchronized (subThreadLock) {
							if (subThread != null)
								subThread.interrupt();
						}
						/* Make sure that the timer sees the new value of running and the timer thread can 
						 * terminate. */
						if (doTime)
							timer.stop();
					} finally {
						submissionLock.unlock();
					}
					// Wait for all the slave threads to finish.
					try {
						termSemaphore.acquire(doTime ? 4 : 3);
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
		@Override
		public String toString() {
			return String.format("%s-stdProcExecutor@%s", StandardProcessPool.this,
					Integer.toHexString(hashCode()));
		}
		
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
								if (!StandardProcessExecutor.this.stop(false))
									StandardProcessExecutor.this.stop(true);
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
	
	/**
	 * A sub-class of {@link org.apache.commons.pool2.impl.GenericObjectPool} for the pooling of 
	 * {@link net.viktorc.pp4j.impl.StandardProcessPool.StandardProcessExecutor} instances.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class StandardProcessExecutorObjectPool extends GenericObjectPool<StandardProcessExecutor> {

		/**
		 * Constructs an object pool instance to facilitate the reuse of 
		 * {@link net.viktorc.pp4j.impl.StandardProcessPool.StandardProcessExecutor} instances. The pool 
		 * does not block if there are no available objects, it accommodates <code>maxPoolSize</code> 
		 * objects, and if there are more than <code>Math.max(minPoolSize, reserveSize)</code> idle objects 
		 * in the pool, excess idle objects are eligible for eviction after <code>EVICT_TIME</code> 
		 * milliseconds. The eviction thread runs at the above specified intervals and performs at most 
		 * <code>maxPoolSize - Math.max(minPoolSize, reserveSize)</code> evictions per run.
		 */
		StandardProcessExecutorObjectPool() {
			super(new PooledObjectFactory<StandardProcessExecutor>() {

				@Override
				public PooledObject<StandardProcessExecutor> makeObject() throws Exception {
					return new DefaultPooledObject<StandardProcessExecutor>(new StandardProcessExecutor());
				}
				@Override
				public void activateObject(PooledObject<StandardProcessExecutor> p) { /* No-operation. */ }
				@Override
				public boolean validateObject(PooledObject<StandardProcessExecutor> p) {
					return true;
				}
				@Override
				public void passivateObject(PooledObject<StandardProcessExecutor> p) { /* No-operation. */ }
				@Override
				public void destroyObject(PooledObject<StandardProcessExecutor> p) { /* No-operation. */ }
			});
			setBlockWhenExhausted(false);
			setMaxTotal(maxPoolSize);
			setMaxIdle(Math.max(minPoolSize, reserveSize));
			long evictTime = EVICT_TIME;
			setTimeBetweenEvictionRunsMillis(evictTime);
			setSoftMinEvictableIdleTimeMillis(evictTime);
			setNumTestsPerEvictionRun(maxPoolSize - Math.max(maxPoolSize, reserveSize));
		}
		
	}
	
	/**
	 * An implementation the {@link java.util.concurrent.ThreadFactory} interface that provides more descriptive 
	 * thread  names and extends the {@link java.lang.Thread.UncaughtExceptionHandler} of the created threads by 
	 * logging the uncaught exceptions if the enclosing {@link net.viktorc.pp4j.impl.StandardProcessPool} 
	 * instance is verbose. It also attempts to shut down the enclosing pool if an exception is thrown in one of 
	 * the threads it created.
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
					e.printStackTrace();
					logger.error(e.getMessage(), e);
					StandardProcessPool.this.forceShutdown();
				}
			});
			return t;
		}
		
	}
	
	/**
	 * A sub-class of {@link java.util.concurrent.ThreadPoolExecutor} for the execution of 
	 * {@link net.viktorc.pp4j.impl.StandardProcessPool.StandardProcessExecutor} instances. It utilizes an 
	 * extension of the {@link java.util.concurrent.LinkedTransferQueue} and an implementation of the 
	 * {@link java.util.concurrent.RejectedExecutionHandler} as per Robert Tupelo-Schneck's 
	 * <a href="https://stackoverflow.com/a/24493856">answer</a> to a StackOverflow question to facilitate a 
	 * queueing logic that has the pool first increase the number of its threads and only really queue tasks 
	 * once the maximum pool size has been reached.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class StandardProcessPoolExecutor extends ThreadPoolExecutor {
		
		/**
		 * Constructs thread pool for the execution of 
		 * {@link net.viktorc.pp4j.impl.StandardProcessPool.StandardProcessExecutor} instances. If there are 
		 * more than <code>Math.max(minPoolSize, reserveSize)</code> idle threads in the pool, excess threads 
		 * are evicted after <code>EVICT_TIME</code> milliseconds.
		 */
		StandardProcessPoolExecutor() {
			super(Math.max(minPoolSize, reserveSize), maxPoolSize, EVICT_TIME, TimeUnit.MILLISECONDS,
					new LinkedTransferQueue<Runnable>() {
				
						private static final long serialVersionUID = 1L;

						@Override
						public boolean offer(Runnable r) {
							/* If there is at least one thread waiting on the queue, delegate the runnablePart immediately; 
							 * else decline it and force the pool to create a new thread for running the runnablePart. */
							return tryTransfer(r);
						}
					}, new CustomizedThreadFactory(StandardProcessPool.this + "-procExecThreadPool"),
					new RejectedExecutionHandler() {
						
						@Override
						public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
							try {
								/* If there are no threads waiting on the queue (all of them are busy executing) 
								 * and the maximum pool size has been reached, when the queue declines the offer, 
								 * the pool will not create any more threads but call this handler instead. This 
								 * handler 'forces' the declined runnablePart on the queue, ensuring that it is not 
								 * rejected. */
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
			procExecutorPool.returnObject(executor);
			logger.debug("Process executor {} stopped.{}", executor, System.lineSeparator() + getPoolStats());
			// A process has terminated. Extend the pool if necessary by using the pooled executors.
			poolLock.lock();
			try {
				if (doExtendPool())
					startNewProcess();
			} finally {
				poolLock.unlock();
			}
		}
		
	}
	
}