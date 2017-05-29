package net.viktorc.pspp;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A class for maintaining and managing a pool of identical pre-started processes.
 * 
 * @author A6714
 *
 */
public class PSPPool implements AutoCloseable {
	
	private final ProcessManagerFactory managerFactory;
	private final int minPoolSize;
	private final int maxPoolSize;
	private final int reserveSize;
	private final long keepAliveTime;
	private final boolean verbose;
	private final ProcessShellExecutor processExecutor;
	private final ExecutorService taskExecutorService;
	private final List<ProcessShell> activeShells;
	private final Queue<InternalSubmission> submissionQueue;
	private final CountDownLatch startupLatch;
	private final Semaphore submissionSemaphore;
	private final Object poolLock;
	private final AtomicInteger numOfExecutingSubmissions;
	private final Logger logger;
	private volatile boolean submissionSuccessful;
	private volatile boolean close;
	private ProcessShell spareShell;

	/**
	 * Constructs a pool of identical processes. The initial size of the pool is the minimum pool size. The size of the pool 
	 * is dynamically adjusted based on the number of requests and running processes.
	 * 
	 * @param managerFactory A {@link net.viktorc.pspp.ProcessManagerFactory} instance that is used to build 
	 * {@link net.viktorc.pspp.ProcessManager} instances that manage the processes' life cycle in the pool.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @param verbose Whether events relating to the management of the pool should be logged.
	 * @throws IOException If the process cannot be started.
	 * @throws IllegalArgumentException If the manager factory is null, or the minimum pool size is less than 0, or the 
	 * maximum pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum 
	 * pool size.
	 */
	public PSPPool(ProcessManagerFactory managerFactory, int minPoolSize, int maxPoolSize,int reserveSize, long keepAliveTime,
			boolean verbose) throws IOException {
		if (managerFactory == null)
			throw new IllegalArgumentException("The process manager factory cannot be null.");
		if (minPoolSize < 0)
			throw new IllegalArgumentException("The minimum pool size has to be greater than 0.");
		if (maxPoolSize < 1 || maxPoolSize < minPoolSize)
			throw new IllegalArgumentException("The maximum pool size has to be at least 1 and at least as great as the " +
					"minimum pool size.");
		if (reserveSize < 0 || reserveSize > maxPoolSize)
			throw new IllegalArgumentException("The reserve has to be greater than 0 and less than the maximum pool size.");
		this.managerFactory = managerFactory;
		this.minPoolSize = minPoolSize;
		this.maxPoolSize = maxPoolSize;
		this.reserveSize = reserveSize;
		this.keepAliveTime = keepAliveTime;
		int actualMinSize = Math.max(minPoolSize, reserveSize);
		processExecutor = new ProcessShellExecutor(actualMinSize, maxPoolSize, keepAliveTime > 0 ? keepAliveTime : Long.MAX_VALUE,
				keepAliveTime > 0 ? TimeUnit.MILLISECONDS : TimeUnit.DAYS, new SynchronousQueue<>());
		taskExecutorService = Executors.newCachedThreadPool();
		activeShells = new CopyOnWriteArrayList<>();
		submissionQueue = new ConcurrentLinkedQueue<>();
		startupLatch = new CountDownLatch(actualMinSize);
		submissionSemaphore = new Semaphore(0);
		poolLock = new Object();
		numOfExecutingSubmissions = new AtomicInteger(0);
		logger = Logger.getAnonymousLogger();
		this.verbose = verbose;
		// Ensure that exceptions thrown by runnables submitted to the pools do not go unnoticed if the instance is verbose.
		if (verbose) {
			processExecutor.setThreadFactory(new VerboseThreadFactory(processExecutor.getThreadFactory(),
					"Error while running process."));
			ThreadPoolExecutor commandExecutor = (ThreadPoolExecutor) taskExecutorService;
			commandExecutor.setThreadFactory(new VerboseThreadFactory(commandExecutor.getThreadFactory(),
					"Error while interacting with the process."));
		}
		for (int i = 0; i < actualMinSize; i++) {
			synchronized (poolLock) {
				startNewProcess(null);
			}
		}
		// Wait for the processes in the initial pool to start up.
		try {
			startupLatch.await();
		} catch (InterruptedException e) {
			if (verbose)
				logger.log(Level.SEVERE, "Error while waiting for the pool to start up.", e);
			Thread.currentThread().interrupt();
		}
		// Start the thread responsible for submitting commands.
		(new Thread(this::mainLoop)).start();
	}
	/**
	 * Constructs a pool of identical processes. The initial size of the pool is the minimum pool size. The size of the pool 
	 * is dynamically adjusted based on the number of requests and running processes.
	 * 
	 * @param managerFactory A {@link net.viktorc.pspp.ProcessManagerFactory} instance that is used to build 
	 * {@link net.viktorc.pspp.ProcessManager} instances that manage the processes' life cycle in the pool.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @throws IOException If the process cannot be started.
	 * @throws IllegalArgumentException If the manager factory is null, or the minimum pool size is less than 0, or the 
	 * maximum pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum 
	 * pool size.
	 */
	public PSPPool(ProcessManagerFactory managerFactory, int minPoolSize, int maxPoolSize,int reserveSize, long keepAliveTime)
			throws IOException {
		this(managerFactory, minPoolSize, maxPoolSize, reserveSize, keepAliveTime, false);
	}
	/**
	 * Executes the command(s) on any of the available processes in the pool. It blocks until the command could successfully be 
	 * submitted for execution.
	 * 
	 * @param submission The command submission including all information necessary for executing and processing the command(s).
	 * @return A {@link java.util.concurrent.Future} instance of the time it took to execute the command including the submission 
	 * delay in nanoseconds.
	 * @throws IllegalArgumentException If the submission is null.
	 */
	public Future<Long> submit(Submission submission) {
		if (submission == null)
			throw new IllegalArgumentException("The submission cannot be null or empty.");
		InternalSubmission internalSubmission = new InternalSubmission(submission);
		submissionQueue.add(internalSubmission);
		// Notify the main loop that a command has been submitted.
		synchronized (this) {
			notifyAll();
		}
		// Return a Future holding the total execution time including the submission delay.
		return new InternalSubmissionFuture(internalSubmission);
	}

	/**
	 * Logs the number of active, queued, and currently executing processes.
	 */
	private void logPoolStats() {
		logger.info("Total processes: " + processExecutor.getActiveCount() + "; acitve processes: " + activeShells.size() +
				"; submitted commands: " + (numOfExecutingSubmissions.get() + submissionQueue.size()));
	}
	/**
	 * Returns whether a new process {@link net.viktorc.pspp.ProcessShell} instance should be added to the pool.
	 * 
	 * @return Whether the process pool should be extended.
	 */
	private boolean doExtendPool() {
		return !close && (processExecutor.getActiveCount() < minPoolSize || (processExecutor.getActiveCount() <
				Math.min(maxPoolSize, numOfExecutingSubmissions.get() + submissionQueue.size() + reserveSize)));
	}
	/**
	 * Starts a new process by executing the provided {@link net.viktorc.pspp.ProcessShell}. If it is null, it creates a new 
	 * instance, adds it to the pool, and executes it.
	 * 
	 * @param processShell An optional {@link net.viktorc.pspp.ProcessShell} instance to re-start in case one is available.
	 * @throws IOException If a new process cannot be created.
	 * @return Whether the process was successfully started.
	 */
	private boolean startNewProcess(ProcessShell processShell) throws IOException {
		if (processShell == null) {
			if (spareShell != null) {
				processShell = spareShell;
				spareShell = null;
			} else {
				PooledProcessManager manager = new PooledProcessManager(managerFactory.createNewProcessManager());
				processShell = new ProcessShell(manager, keepAliveTime, taskExecutorService);
				manager.setShell(processShell);
			}
		}
		/* Try to execute the process. It may happen that the count of active processes returned by the pool is not correct 
		 * and in fact the pool has reached its capacity in the mean time. It is ignored for now. !TODO Devise a mechanism 
		 * that takes care of this. */
		try {
			processExecutor.execute(processShell);
			return true;
		} catch (RejectedExecutionException e) {
			spareShell = processShell;
			if (verbose)
				logger.log(Level.WARNING, "Failed to start new process due to the pool having reached its capacity.");
			return false;
		}
	}
	/**
	 * A method that handles the submission of commands from the queue to the processes.
	 */
	private void mainLoop() {
		Optional<InternalSubmission> optionalSubmission = Optional.empty();
		while (!close) {
			try {
				// Wait until there is a command submitted.
				if (!optionalSubmission.isPresent()) {
					synchronized (this) {
						while (!close && !(optionalSubmission = Optional.ofNullable(submissionQueue.peek())).isPresent()) {
							try {
								wait();
							} catch (InterruptedException e) {
								return;
							}
						}
					}
				}
				if (close)
					return;
				InternalSubmission submission = optionalSubmission.get();
				// Execute it in any of the available processes.
				for (ProcessShell shell : activeShells) {
					if (shell.isReady()) {
						Future<?> future = taskExecutorService.submit(() -> {
							try {
								if (shell.execute(submission)) {
									if (verbose)
										logger.info(String.format("Command(s) %s processed; submission delay: %.3f;" +
												" execution time: %.3f.%n", submission,
												(float) ((double) (submission.getSubmittedTime() -
												submission.getReceivedTime())/1000000000),
												(float) ((double) (submission.getProcessedTime() -
												submission.getSubmittedTime())/1000000000)));
								} else {
									submissionSuccessful = false;
									submissionSemaphore.release();
								}
							} catch (IOException e) {
								if (verbose)
									logger.log(Level.SEVERE, "Error while executing command(s) " +
											submission + ".", e);
							}
						});
						submissionSemaphore.acquire();
						if (submissionSuccessful) {
							submission.setFuture(future);
							submissionQueue.remove(submission);
							optionalSubmission = Optional.empty();
							break;
						}
					}
				}
				// Extend the pool if needed.
				synchronized (poolLock) {
					if (doExtendPool()) {
						try {
							startNewProcess(null);
						} catch (IOException e) {
							if (verbose)
								logger.log(Level.SEVERE, "Error while starting new process.", e);
						}
					}
				}
			} catch (Exception e) {
				if (verbose)
					logger.log(Level.SEVERE, "An error occurred while submitting commands.", e);
			}
		}
	}
	
	@Override
	public void close() throws Exception {
		close = true;
		synchronized (this) {
			notifyAll();
		}
		for (ProcessShell shell : activeShells)
			shell.stop(true);
		processExecutor.shutdown();
		taskExecutorService.shutdown();
	}
	
	/**
	 * A sub-class of {@link java.util.concurrent.ThreadPoolExecutor} for the execution of {@link net.viktorc.pspp.ProcessShell} 
	 * instances.
	 * 
	 * @author A6714
	 *
	 */
	private class ProcessShellExecutor extends ThreadPoolExecutor {

		/**
		 * Constructs a pool according to the specified parameters. </br>
		 * 
		 * See {@link java.util.concurrent.ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)}.
		 */
		ProcessShellExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
				BlockingQueue<Runnable> workQueue) {
			super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
		}
		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			super.afterExecute(r, t);
			ProcessShell shell = (ProcessShell) r;
			if (verbose) {
				logger.info("Process shell " + shell + " stopped executing.");
				logPoolStats();
			}
			boolean doExtend = false;
			synchronized (poolLock) {
				if (doExtendPool()) {
					doExtend = true;
					try {
						startNewProcess(shell);
					} catch (IOException e) {
						if (verbose)
							logger.log(Level.SEVERE, "Error while starting new process.", e);
					}
				}
			}
			if (!doExtend) {
				try {
					shell.close();
					if (verbose)
						logger.info("Shutting down process shell " + shell + ".");
				} catch (Exception e) {
					if (verbose)
						logger.log(Level.SEVERE, "Error while shutting down process shell " + shell + ".", e);
				}
			}
		}
		
	}
	
	/**
	 * An implementation the {@link java.util.concurrent.ThreadFactory} interface that extends the 
	 * {@link java.lang.Thread.UncaughtExceptionHandler} of the created threads by logging the exceptions (throwables).
	 * 
	 * @author A6714
	 *
	 */
	private class VerboseThreadFactory implements ThreadFactory {

		final ThreadFactory originalFactory;
		final String executionErrorMessage;
		
		/**
		 * Constructs an instance according to the specified parameters.
		 * 
		 * @param originalFactory The default {@link java.util.concurrent.ThreadFactory} of the pool.
		 * @param executionErrorMessage The error message to log in case an exception is thrown while 
		 * executing the {@link java.lang.Runnable}.
		 */
		VerboseThreadFactory(ThreadFactory originalFactory, String executionErrorMessage) {
			this.originalFactory = originalFactory;
			this.executionErrorMessage = executionErrorMessage;
		}
		@Override
		public Thread newThread(Runnable r) {
			Thread t = originalFactory.newThread(r);
			UncaughtExceptionHandler handler = t.getUncaughtExceptionHandler();
			t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
				
				@Override
				public void uncaughtException(Thread t, Throwable e) {
					if (!(close && e.getCause() instanceof RejectedExecutionException))
						logger.log(Level.SEVERE, executionErrorMessage, e);
					handler.uncaughtException(t, e);
				}
			});
			return t;
		}
		
	}
	
	/**
	 * An implementation of the {@link net.viktorc.pspp.ProcessManager} interface for managing the life cycle of 
	 * individual pooled processes.
	 * 
	 * @author A6714
	 *
	 */
	private class PooledProcessManager implements ProcessManager {
		
		final ProcessManager originalManager;
		ProcessShell processShell;
		
		/**
		 * Constructs a wrapper around the specified process manager.
		 * 
		 * @param originalManager The original process manager.
		 */
		PooledProcessManager(ProcessManager originalManager) {
			this.originalManager = originalManager;
		}
		/**
		 * Sets the process shell to which the manager is assigned to.
		 * 
		 * @param shell The process shell to which the manager is assigned to.
		 */
		void setShell(ProcessShell shell) {
			this.processShell = shell;
		}
		@Override
		public Process start() throws IOException {
			return originalManager.start();
		}
		@Override
		public boolean startsUpInstantly() {
			return originalManager.startsUpInstantly();
		}
		@Override
		public boolean isStartedUp(String output, boolean standard) {
			return originalManager.isStartedUp(output, standard);
		}
		@Override
		public void onStartup(ProcessShell shell) {
			originalManager.onStartup(shell);
			if (verbose) {
				logger.info("Process shell " + shell + " started executing.");
				logPoolStats();
			}
			startupLatch.countDown();
			activeShells.add(shell);
		}
		@Override
		public boolean terminate(ProcessShell shell) {
			return originalManager.terminate(shell);
		}
		@Override
		public void onTermination(int resultCode) {
			originalManager.onTermination(resultCode);
			if (processShell != null)
				activeShells.remove(processShell);
		}
		
	}
	
	/**
	 * An implementation of the {@link net.viktorc.pspp.Submission} interface for wrapping submissions into 'internal' 
	 * submissions to keep track of the number of commands being executed at a time and to establish a mechanism for 
	 * canceling submitted commands via the {@link java.util.concurrent.Future} returned by the 
	 * {@link net.viktorc.pspp.PSPPool#submit(Submission) submit} method.
	 * 
	 * @author A6714
	 *
	 */
	private class InternalSubmission implements Submission {
		
		final Submission originalSubmission;
		final long receivedTime;
		Long submittedTime;
		Long processedTime;
		boolean processed;
		volatile Future<?> future;
		
		/**
		 * Constructs an instance according to the specified parameters.
		 * 
		 * @param originalSubmission The submission to wrap into an internal submission with extended features.
		 * @throws IllegalArgumentException If the submission is null.
		 */
		InternalSubmission(Submission originalSubmission) {
			if (originalSubmission == null)
				throw new IllegalArgumentException("The submission cannot be null.");
			this.originalSubmission = originalSubmission;
			receivedTime = System.nanoTime();
		}
		/**
		 * Returns the time when the instance was constructed in nanoseconds.
		 * 
		 * @return The time when the instance was constructed in nanoseconds.
		 */
		long getReceivedTime() {
			return receivedTime;
		}
		/**
		 * Returns the time when the command was submitted in nanoseconds or null if it has not been 
		 * submitted yet.
		 * 
		 * @return The time when the command was submitted in nanoseconds or null.
		 */
		Long getSubmittedTime() {
			return submittedTime;
		}
		/**
		 * Returns the time when the command was processed in nanoseconds or null if it has not been 
		 * processed yet.
		 * 
		 * @return The time when the command was processed in nanoseconds or null.
		 */
		Long getProcessedTime() {
			return processedTime;
		}
		/**
		 * Returns whether the command has already been processed.
		 * 
		 * @return Whether the command has already been processed.
		 */
		boolean isProcessed() {
			return processed;
		}
		/**
		 * Returns the {@link java.util.concurrent.Future} instance associated with the submission or null if it has not been
		 * submitted yet.
		 * 
		 * @return The {@link java.util.concurrent.Future} instance associated with the command or null.
		 */
		Future<?> getFuture() {
			return future;
		}
		/**
		 * Sets the {@link java.util.concurrent.Future} instance associated with the submission and the submission time.
		 * 
		 * @param future The {@link java.util.concurrent.Future} instance associated with the submission.
		 */
		void setFuture(Future<?> future) {
			submittedTime = System.nanoTime();
			this.future = future;
		}
		@Override
		public List<Command> getCommands() {
			return originalSubmission.getCommands();
		}
		@Override
		public boolean doTerminateProcessAfterwards() {
			return originalSubmission.doTerminateProcessAfterwards();
		}
		@Override
		public boolean isCancelled() {
			return originalSubmission.isCancelled() || (future != null && future.isCancelled()) || close;
		}
		@Override
		public void onStartedProcessing() {
			originalSubmission.onStartedProcessing();
			submissionSuccessful = true;
			submissionSemaphore.release();
			numOfExecutingSubmissions.incrementAndGet();
		}
		@Override
		public void onFinishedProcessing() {
			originalSubmission.onFinishedProcessing();
			processedTime = System.nanoTime();
			processed = true;
			synchronized (this) {
				notifyAll();
			}
			numOfExecutingSubmissions.decrementAndGet();
		}
		@Override
		public String toString() {
			return String.join("; ", originalSubmission.getCommands().stream().map(c -> c.getInstruction()).collect(Collectors.toList()));
		}
		
	}
	
	/**
	 * An implementation of {@link java.util.concurrent.Future} that returns the time it took to process the 
	 * submission.
	 * 
	 * @author A6714
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
		public synchronized boolean cancel(boolean mayInterruptIfRunning) {
			submissionQueue.remove(submission);
			Future<?> future = submission.getFuture();
			if (future != null && future.cancel(mayInterruptIfRunning)) {
				synchronized (submission) {
					submission.notifyAll();
				}
				return true;
			}
			return false;
		}
		@Override
		public Long get() throws InterruptedException, ExecutionException, CancellationException {
			synchronized (submission) {
				while (!submission.isProcessed() && !submission.isCancelled()) {
					submission.wait();
				}
			}
			if (submission.isCancelled())
				throw new CancellationException();
			return submission.getProcessedTime() - submission.getReceivedTime();
		}
		@Override
		public Long get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
			long timeoutNs = unit.toNanos(timeout);
			long start = System.nanoTime();
			synchronized (submission) {
				while (!submission.isProcessed() && !submission.isCancelled() && timeoutNs > 0) {
					submission.wait(timeoutNs/1000000, (int) (timeoutNs%1000000));
					timeoutNs -= (System.nanoTime() - start);
				}
			}
			if (submission.isCancelled())
				throw new CancellationException();
			if (timeoutNs <= 0)
				throw new TimeoutException();
			return timeoutNs <= 0 ? null : submission.getProcessedTime() -
					submission.getReceivedTime();
		}
		@Override
		public boolean isCancelled() {
			return submission.isCancelled();
		}
		@Override
		public boolean isDone() {
			return submission.isProcessed();
		}
		
	}
	
}
