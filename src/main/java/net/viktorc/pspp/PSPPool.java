package net.viktorc.pspp;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
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
	
	private final ProcessManager handler;
	private final int minPoolSize;
	private final int maxPoolSize;
	private final int reserveSize;
	private final long keepAliveTime;
	private final boolean verbose;
	private final ThreadPoolExecutor poolExecutor;
	private final ExecutorService commandExecutorService;
	private final List<ProcessShell> activeProcesses;
	private final Queue<InternalSubmission> commandQueue;
	private final CountDownLatch startupLatch;
	private final Semaphore submissionSemaphore;
	private final Object poolLock;
	private final AtomicInteger numOfExecutingCommands;
	private final Logger logger;
	private volatile boolean submissionSuccessful;
	private volatile boolean close;
	private ProcessShell spareProcessManager;

	/**
	 * Constructs a pool of identical processes. The initial size of the pool is the minimum pool size. The size of the pool 
	 * is dynamically adjusted based on the number of requests and running processes.
	 * 
	 * @param handler A {@link net.viktorc.pspp.ProcessManager} instance that handles each process' life cycle in the pool. It 
	 * should be stateless as the same instance is used for all processes.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @param verbose Whether events relating to the management of the pool should be logged.
	 * @throws IOException If the process cannot be started.
	 * @throws IllegalArgumentException If the handler is null, or the minimum pool size is less than 0, or the 
	 * maximum pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum 
	 * pool size.
	 */
	public PSPPool(ProcessManager handler, int minPoolSize, int maxPoolSize,int reserveSize, long keepAliveTime, boolean verbose)
			throws IOException {
		if (handler == null)
			throw new IllegalArgumentException("The process handler cannot be null.");
		if (minPoolSize < 0)
			throw new IllegalArgumentException("The minimum pool size has to be greater than 0.");
		if (maxPoolSize < 1 || maxPoolSize < minPoolSize)
			throw new IllegalArgumentException("The maximum pool size has to be at least 1 and at least as great as the " +
					"minimum pool size.");
		if (reserveSize < 0 || reserveSize > maxPoolSize)
			throw new IllegalArgumentException("The reserve has to be greater than 0 and less than the maximum pool size.");
		this.handler = handler;
		this.minPoolSize = minPoolSize;
		this.maxPoolSize = maxPoolSize;
		this.reserveSize = reserveSize;
		this.keepAliveTime = keepAliveTime;
		int actualMinSize = Math.max(minPoolSize, reserveSize);
		poolExecutor = new ThreadPoolExecutor(actualMinSize, maxPoolSize, keepAliveTime > 0 ? keepAliveTime : Long.MAX_VALUE,
				keepAliveTime > 0 ? TimeUnit.MILLISECONDS : TimeUnit.DAYS, new SynchronousQueue<>());
		commandExecutorService = Executors.newCachedThreadPool();
		activeProcesses = new CopyOnWriteArrayList<>();
		commandQueue = new ConcurrentLinkedQueue<>();
		startupLatch = new CountDownLatch(actualMinSize);
		submissionSemaphore = new Semaphore(0);
		poolLock = new Object();
		numOfExecutingCommands = new AtomicInteger(0);
		logger = Logger.getAnonymousLogger();
		this.verbose = verbose;
		for (int i = 0; i < actualMinSize; i++) {
			synchronized (poolLock) {
				startNewProcess();
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
	 * @param handler A {@link net.viktorc.pspp.ProcessManager} instance that handles each process' life cycle in the pool. It 
	 * should be stateless as the same instance is used for all processes.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @throws IOException If the process cannot be started.
	 * @throws IllegalArgumentException If the builder or the listener is null, or the minimum pool size is less than 0, or the 
	 * maximum pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum 
	 * pool size.
	 */
	public PSPPool(ProcessManager handler, int minPoolSize, int maxPoolSize,int reserveSize, long keepAliveTime)
			throws IOException {
		this(handler, minPoolSize, maxPoolSize, reserveSize, keepAliveTime, false);
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
		commandQueue.add(internalSubmission);
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
		logger.info("Total processes: " + poolExecutor.getActiveCount() + "; acitve processes: " + activeProcesses.size() +
				"; submitted commands: " + (numOfExecutingCommands.get() + commandQueue.size()));
	}
	/**
	 * Adds a new {@link net.viktorc.pspp.ProcessShell} instance to the pool and starts it.
	 * 
	 * @throws IOException If a new process cannot be created.
	 * @return Whether the process was successfully started.
	 */
	private boolean startNewProcess() throws IOException {
		ProcessShell procManager;
		if (spareProcessManager != null) {
			procManager = spareProcessManager;
			spareProcessManager = null;
		} else
			procManager = new ProcessShell(new PooledProcessHandler(), keepAliveTime, commandExecutorService);
		/* Try to execute the process. It may happen that the count of active processes returned by the pool is not correct 
		 * and in fact the pool has reached its capacity in the mean time. It is ignored for now. !TODO Devise a mechanism 
		 * that takes care of this. */
		try {
			poolExecutor.execute(procManager);
			return true;
		} catch (RejectedExecutionException e) {
			// If the process cannot be submitted to the pool, store it in a variable to avoid wasting the instance.
			spareProcessManager = procManager;
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
						while (!(optionalSubmission = Optional.ofNullable(commandQueue.peek())).isPresent()) {
							try {
								wait();
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							}
						}
					}
				}
				if (close)
					return;
				InternalSubmission submission = optionalSubmission.get();
				// Execute it in any of the available processes.
				for (ProcessShell manager : activeProcesses) {
					if (manager.isReady()) {
						Future<?> future = commandExecutorService.submit(() -> {
							try {
								if (manager.execute(submission)) {
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
							commandQueue.remove(submission);
							optionalSubmission = Optional.empty();
							break;
						}
					}
				}
				// Extend the pool if needed.
				synchronized (poolLock) {
					if (!close && (poolExecutor.getActiveCount() < minPoolSize || (poolExecutor.getActiveCount() <
							Math.min(maxPoolSize, numOfExecutingCommands.get() + commandQueue.size() + reserveSize)))) {
						try {
							startNewProcess();
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
		for (ProcessShell p : activeProcesses)
			p.close();
		commandExecutorService.shutdown();
		poolExecutor.shutdown();
	}
	
	/**
	 * An implementation of the {@link net.viktorc.pspp.ProcessManager} interface for managing the life cycle of 
	 * individual pooled processes.
	 * 
	 * @author A6714
	 *
	 */
	private class PooledProcessHandler implements ProcessManager {
		
		// The process manager to which the listener is subscribed.
		private ProcessShell manager;
		
		@Override
		public Process start() throws IOException {
			return handler.start();
		}
		@Override
		public boolean startsUpInstantly() {
			return handler.startsUpInstantly();
		}
		@Override
		public boolean isStartedUp(String output, boolean standard) {
			return handler.isStartedUp(output, standard);
		}
		@Override
		public void onStartup(ProcessShell manager) {
			// Store the reference to the manager for later use in the onTermination method.
			this.manager = manager;
			handler.onStartup(manager);
			activeProcesses.add(manager);
			if (verbose) {
				logger.info("Process manager " + manager + " started executing.");
				logPoolStats();
			}
			startupLatch.countDown();
		}
		@Override
		public boolean terminate(ProcessShell manager) {
			return handler.terminate(manager);
		}
		@Override
		public void onTermination(int resultCode) {
			handler.onTermination(resultCode);
			if (manager == null)
				return;
			activeProcesses.remove(manager);
			if (verbose) {
				logger.info("Process manager " + manager + " stopped executing.");
				logPoolStats();
			}
			try {
				manager.close();
				if (verbose)
					logger.info("Shutting down process manager " + manager + ".");
			} catch (Exception e) {
				if (verbose)
					logger.log(Level.SEVERE, "Error while shutting down process manager " + manager + ".", e);
			}
			synchronized (poolLock) {
				// Consider that the pool size is about to decrease as this process terminates.
				if (!close && ((maxPoolSize > minPoolSize ? poolExecutor.getActiveCount() <= minPoolSize :
						poolExecutor.getActiveCount() < minPoolSize) || (poolExecutor.getActiveCount() <
						Math.min(maxPoolSize, numOfExecutingCommands.get() + commandQueue.size() + reserveSize + 1)))) {
					try {
						startNewProcess();
					} catch (IOException e) {
						if (verbose)
							logger.log(Level.SEVERE, "Error while starting new process.", e);
					}
				}
			}
		}
		
	}
	
	/**
	 * An implementation of the {@link net.viktorc.pspp.Submission} interface for wrapping submissions into 'internal' submissions 
	 * to keep track of the number of commands being executed at a time and to establish a mechanism for canceling submitted commands 
	 * via the {@link java.util.concurrent.Future} returned by the {@link net.viktorc.pspp.PSPPool#submit(Submission) submit} 
	 * method.
	 * 
	 * @author A6714
	 *
	 */
	private class InternalSubmission implements Submission {
		
		final Submission submission;
		final long receivedTime;
		Long submittedTime;
		Long processedTime;
		boolean processed;
		volatile Future<?> future;
		
		/**
		 * Constructs an instance according to the specified parameters.
		 * 
		 * @param submission The submission to wrap into an internal submission with extended features.
		 * @throws IllegalArgumentException If the submission is null.
		 */
		InternalSubmission(Submission submission) {
			if (submission == null)
				throw new IllegalArgumentException("The submission cannot be null.");
			this.submission = submission;
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
			return submission.getCommands();
		}
		@Override
		public boolean doTerminateProcessAfterwards() {
			return submission.doTerminateProcessAfterwards();
		}
		@Override
		public boolean isCancelled() {
			return submission.isCancelled() || (future != null && future.isCancelled());
		}
		@Override
		public void onStartedProcessing() {
			submission.onStartedProcessing();
			submissionSuccessful = true;
			submissionSemaphore.release();
			numOfExecutingCommands.incrementAndGet();
		}
		@Override
		public void onFinishedProcessing() {
			submission.onFinishedProcessing();
			processedTime = System.nanoTime();
			processed = true;
			synchronized (this) {
				notifyAll();
			}
			numOfExecutingCommands.decrementAndGet();
		}
		@Override
		public String toString() {
			return String.join("; ", submission.getCommands().stream().map(c -> c.getInstruction()).collect(Collectors.toList()));
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
			commandQueue.remove(submission);
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
