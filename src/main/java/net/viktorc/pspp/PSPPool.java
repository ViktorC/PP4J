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

/**
 * A class for maintaining and managing a pool of identical pre-started processes.
 * 
 * @author A6714
 *
 */
public class PSPPool implements AutoCloseable {
	
	private final ProcessBuilder builder;
	private final ProcessListener listener;
	private final int minPoolSize;
	private final int maxPoolSize;
	private final int reserveSize;
	private final long keepAliveTime;
	private final boolean verbose;
	private final ThreadPoolExecutor poolExecutor;
	private final ExecutorService commandExecutorService;
	private final List<ProcessManager> activeProcesses;
	private final Queue<Submission> commandQueue;
	private final CountDownLatch startupLatch;
	private final Semaphore submissionSemaphore;
	private final Object poolLock;
	private final AtomicInteger numOfExecutingCommands;
	private final Logger logger;
	private volatile boolean submissionSuccessful;
	private volatile boolean close;
	private ProcessManager spareProcessManager;

	/**
	 * Constructs a pool of identical processes. The initial size of the pool is the minimum pool size. The size of the pool 
	 * is dynamically adjusted based on the number of requests and running processes.
	 * 
	 * @param builder The process builder for building the pooled processes.
	 * @param listener A {@link net.viktorc.pspp.ProcessListener} instance that is added to each process in the pool. It should 
	 * be stateless as the same instance is used for all processes.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @param verbose Whether events relating to the management of the pool should be logged.
	 * @throws IOException If the process cannot be started.
	 * @throws IllegalArgumentException If the builder or the listener is null, or the minimum pool size is less than 0, or the 
	 * maximum pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum 
	 * pool size.
	 */
	public PSPPool(ProcessBuilder builder, ProcessListener listener, int minPoolSize, int maxPoolSize,
			int reserveSize, long keepAliveTime, boolean verbose) throws IOException {
		if (builder == null)
			throw new IllegalArgumentException("The process builder cannot be null.");
		if (listener == null)
			throw new IllegalArgumentException("The process listener cannot be null.");
		if (minPoolSize < 0)
			throw new IllegalArgumentException("The minimum pool size has to be greater than 0.");
		if (maxPoolSize < 1 || maxPoolSize < minPoolSize)
			throw new IllegalArgumentException("The maximum pool size has to be at least 1 and at least as great as the " +
					"minimum pool size.");
		if (reserveSize < 0 || reserveSize > maxPoolSize)
			throw new IllegalArgumentException("The reserve has to be greater than 0 and less than the maximum pool size.");
		this.builder = builder;
		this.listener = listener;
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
	 * @param builder The process builder for building the pooled processes.
	 * @param listener A {@link net.viktorc.pspp.ProcessListener} instance that is added to each process in the pool. It should 
	 * be stateless as the same instance is used for all processes.
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
	public PSPPool(ProcessBuilder builder, ProcessListener listener, int minPoolSize, int maxPoolSize,
			int reserveSize, long keepAliveTime) throws IOException {
		this(builder, listener, minPoolSize, maxPoolSize, reserveSize, keepAliveTime, false);
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
		Submission internalSubmission = new Submission(submission.getCommands(),
				submission.doTerminateProcessAfterwards());
		for (SubmissionListener subListener : submission.getSubmissionListeners())
			internalSubmission.addSubmissionListener(subListener);
		internalSubmission.addSubmissionListener(new SubmissionListener() {
			
			@Override
			public void onStartedProcessing() {
				submissionSuccessful = true;
				submissionSemaphore.release();
				numOfExecutingCommands.incrementAndGet();
			}
			@Override
			public void onFinishedProcessing() {
				numOfExecutingCommands.decrementAndGet();
			}
			
		});
		commandQueue.add(internalSubmission);
		// Notify the main loop that a command has been submitted.
		synchronized (this) {
			notifyAll();
		}
		// Return a Future holding the total execution time including the submission delay.
		return new SubmissionFuture(internalSubmission);
	}

	/**
	 * Logs the number of active, queued, and currently executing processes.
	 */
	private void logPoolStats() {
		logger.info("Total processes: " + poolExecutor.getActiveCount() + "; acitve processes: " + activeProcesses.size() +
				"; submitted commands: " + (numOfExecutingCommands.get() + commandQueue.size()));
	}
	/**
	 * Adds a new {@link net.viktorc.pspp.ProcessManager} instance to the pool and starts it.
	 * 
	 * @throws IOException If a new process cannot be created.
	 * @return Whether the process was successfully started.
	 */
	private boolean startNewProcess() throws IOException {
		ProcessManager procManager;
		if (spareProcessManager != null) {
			procManager = spareProcessManager;
			spareProcessManager = null;
		} else {
			procManager = new ProcessManager(builder, new ProcessListener() {
				
				// The process manager to which the listener is subscribed.
				private ProcessManager manager;
				
				@Override
				public boolean isStartedUp(String output, boolean standard) {
					return listener.isStartedUp(output, standard);
				}
				@Override
				public void onStartup(ProcessManager manager) {
					// Store the reference to the manager for later use in the onTermination method.
					this.manager = manager;
					listener.onStartup(manager);
					activeProcesses.add(manager);
					if (verbose) {
						logger.info("Process manager " + manager + " started executing.");
						logPoolStats();
					}
					startupLatch.countDown();
				}
				@Override
				public boolean terminate(ProcessManager manager) {
					return listener.terminate(manager);
				}
				@Override
				public void onTermination(int resultCode) {
					listener.onTermination(resultCode);
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
				
			}, keepAliveTime, commandExecutorService);
		}
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
		Optional<Submission> optionalSubmission = Optional.empty();
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
				Submission submission = optionalSubmission.get();
				// Execute it in any of the available processes.
				for (ProcessManager manager : activeProcesses) {
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
		for (ProcessManager p : activeProcesses)
			p.close();
		commandExecutorService.shutdown();
		poolExecutor.shutdown();
	}
	
	/**
	 * An implementation of {@link java.util.concurrent.Future} that returns the time it took to process the 
	 * submission.
	 * 
	 * @author Viktor
	 *
	 */
	private class SubmissionFuture implements Future<Long> {
		
		final Submission submission;
		
		/**
		 * Constructs a {@link java.util.concurrent.Future} for the specified submission.
		 * 
		 * @param submission The submission to get a {@link java.util.concurrent.Future} for.
		 */
		SubmissionFuture(Submission submission) {
			this.submission = submission;
		}
		@Override
		public synchronized boolean cancel(boolean mayInterruptIfRunning) {
			commandQueue.remove(submission);
			Future<?> submissionFuture = submission.getFuture();
			if (submissionFuture != null)
				submissionFuture.cancel(true);
			else
				return false;
			synchronized (submission) {
				submission.notifyAll();
			}
			return true;
		}
		@Override
		public Long get() throws InterruptedException, ExecutionException, CancellationException {
			synchronized (submission) {
				while (!submission.isProcessed() && !submission.getFuture().isCancelled()) {
					submission.wait();
				}
			}
			if (submission.getFuture().isCancelled())
				throw new CancellationException();
			return submission.getProcessedTime() - submission.getReceivedTime();
		}
		@Override
		public Long get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
			long timeoutNs = unit.toNanos(timeout);
			long start = System.nanoTime();
			synchronized (submission) {
				while (!submission.isProcessed() && !submission.getFuture().isCancelled() && timeoutNs > 0) {
					submission.wait(timeoutNs/1000000, (int) (timeoutNs%1000000));
					timeoutNs -= (System.nanoTime() - start);
				}
			}
			if (submission.getFuture().isCancelled())
				throw new CancellationException();
			if (timeoutNs <= 0)
				throw new TimeoutException();
			return timeoutNs <= 0 ? null : submission.getProcessedTime() -
					submission.getReceivedTime();
		}
		@Override
		public boolean isCancelled() {
			return submission.getFuture().isCancelled();
		}
		@Override
		public boolean isDone() {
			return submission.isProcessed();
		}
		
	}
	
}
