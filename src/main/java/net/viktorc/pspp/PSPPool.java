package net.viktorc.pspp;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
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
public class PSPPool {
	
	private final ProcessManagerFactory managerFactory;
	private final int minPoolSize;
	private final int maxPoolSize;
	private final int reserveSize;
	private final long keepAliveTime;
	private final boolean verbose;
	private final Thread mainLoop;
	private final ProcessShellExecutor processExecutor;
	private final ExecutorService taskExecutorService;
	private final Queue<ProcessShell> allShells;
	private final Queue<ProcessShell> activeShells;
	private final Queue<ProcessShell> spareShells;
	private final BlockingQueue<InternalSubmission> submissions;
	private final AtomicInteger numOfExecutingSubmissions;
	private final CountDownLatch prestartLatch;
	private final Object poolLock;
	private final Logger logger;
	private volatile boolean close;

	/**
	 * Constructs a pool of processes. The initial size of the pool is the minimum pool size or the reserve size depending on which 
	 * one is greater. This constructor blocks until the initial number of processes start up. The size of the pool is dynamically 
	 * adjusted based on the pool parameters and the rate of incoming submissions.
	 * 
	 * @param managerFactory A {@link net.viktorc.pspp.ProcessManagerFactory} instance that is used to build 
	 * {@link net.viktorc.pspp.ProcessManager} instances that manage the processes' life cycle in the pool.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @param verbose Whether events relating to the management of the pool should be logged.
	 * @throws IllegalArgumentException If the manager factory is null, or the minimum pool size is less than 0, or the 
	 * maximum pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum 
	 * pool size.
	 */
	public PSPPool(ProcessManagerFactory managerFactory, int minPoolSize, int maxPoolSize,int reserveSize, long keepAliveTime,
			boolean verbose) {
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
		mainLoop = new Thread(this::mainLoop);
		int actualMinSize = Math.max(minPoolSize, reserveSize);
		processExecutor = new ProcessShellExecutor(actualMinSize, maxPoolSize, keepAliveTime > 0 ? keepAliveTime : Long.MAX_VALUE,
				keepAliveTime > 0 ? TimeUnit.MILLISECONDS : TimeUnit.DAYS, new SynchronousQueue<>());
		taskExecutorService = Executors.newCachedThreadPool();
		allShells = new LinkedBlockingQueue<>();
		activeShells = new LinkedBlockingQueue<>();
		spareShells = new LinkedList<>();
		submissions = new LinkedBlockingQueue<>();
		numOfExecutingSubmissions = new AtomicInteger(0);
		prestartLatch = new CountDownLatch(actualMinSize);
		poolLock = new Object();
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
			prestartLatch.await();
		} catch (InterruptedException e) {
			if (verbose)
				logger.log(Level.SEVERE, "Thread interrupted while waiting for the pool to start up.", e);
			Thread.currentThread().interrupt();
			return;
		}
		// Start the thread responsible for submitting commands.
		mainLoop.start();
	}
	/**
	 * Constructs a pool of processes. The initial size of the pool is the minimum pool size or the reserve size depending on which 
	 * one is greater. This constructor blocks until the initial number of processes start up. The size of the pool is dynamically 
	 * adjusted based on the pool parameters and the rate of incoming submissions.
	 * 
	 * @param managerFactory A {@link net.viktorc.pspp.ProcessManagerFactory} instance that is used to build 
	 * {@link net.viktorc.pspp.ProcessManager} instances that manage the processes' life cycle in the pool.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @throws IllegalArgumentException If the manager factory is null, or the minimum pool size is less than 0, or the 
	 * maximum pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum 
	 * pool size.
	 */
	public PSPPool(ProcessManagerFactory managerFactory, int minPoolSize, int maxPoolSize,int reserveSize, long keepAliveTime) {
		this(managerFactory, minPoolSize, maxPoolSize, reserveSize, keepAliveTime, false);
	}
	/**
	 * Returns the number of active, queued, and currently executing processes as string.
	 * 
	 * @return A string of statistics concerning the size of the process pool.
	 */
	private String getPoolStats() {
		return "Total processes: " + allShells.size() + "; acitve processes: " + activeShells.size() +
				"; submitted commands: " + (numOfExecutingSubmissions.get() + submissions.size());
	}
	/**
	 * Returns whether a new process {@link net.viktorc.pspp.ProcessShell} instance should be added to the pool.
	 * 
	 * @return Whether the process pool should be extended.
	 */
	private boolean doExtendPool() {
		return !close && (allShells.size() < minPoolSize || (allShells.size() < Math.min(maxPoolSize,
				numOfExecutingSubmissions.get() + submissions.size() + reserveSize)));
	}
	/**
	 * Starts a new process by executing the provided {@link net.viktorc.pspp.ProcessShell}. If it is null, it creates a new 
	 * instance, adds it to the pool, and executes it.
	 * 
	 * @param processShell An optional {@link net.viktorc.pspp.ProcessShell} instance to re-start in case one is available.
	 * @return Whether the process was successfully started.
	 */
	private boolean startNewProcess(ProcessShell processShell) {
		if (processShell == null) {
			ProcessShell spareShell = spareShells.poll();
			if (spareShell != null)
				processShell = spareShell;
			else {
				PooledProcessManager manager = new PooledProcessManager(managerFactory.createNewProcessManager());
				processShell = new ProcessShell(manager, keepAliveTime, taskExecutorService);
				manager.processShell = processShell;
			}
		}
		/* Try to execute the process. It may happen that the count of active processes is not correct and in fact the pool 
		 * has reached its capacity in the mean time. It is ignored for now. !TODO Devise a mechanism that takes care of this. */
		try {
			processExecutor.execute(processShell);
			allShells.add(processShell);
			return true;
		} catch (RejectedExecutionException e) {
			if (spareShells.size() < reserveSize)
				spareShells.add(processShell);
			if (verbose)
				logger.log(Level.WARNING, "Failed to start new process due to the pool having reached its capacity.");
			return false;
		}
	}
	/**
	 * A method that handles the submission of commands from the queue to the processes.
	 */
	private void mainLoop() {
		InternalSubmission nextSubmission = null;
		while (!close) {
			try {
				// Wait until there is a command submitted.
				if (nextSubmission == null)
					nextSubmission = submissions.take();
				InternalSubmission submission = nextSubmission;
				// Execute it in any of the available processes.
				for (ProcessShell shell : activeShells) {
					if (shell.isReady()) {
						Future<?> future = taskExecutorService.submit(() -> {
							try {
								submission.submitted = false;
								submission.semaphore.drainPermits();
								if (shell.execute(submission)) {
									if (verbose)
										logger.info(String.format("Command(s) %s processed; submission delay: %.3f;" +
												" execution time: %.3f.%n", submission,
												(float) ((double) (submission.submittedTime -
												submission.receivedTime)/1000000000),
												(float) ((double) (submission.processedTime -
												submission.submittedTime)/1000000000)));
								} else
									submission.semaphore.release();
							} catch (Throwable t) {
								if (verbose)
									logger.log(Level.SEVERE, "Error while executing command(s) " +
											submission + ".", t);
								if (submission.semaphore.availablePermits() == 0)
									submission.semaphore.release();
								synchronized (submission) {
									submission.throwable = t;
									submission.notifyAll();
								}
							}
						});
						submission.semaphore.acquire();
						if (submission.submitted) {
							synchronized (submission) {
								submission.future = future;	
							}
							submissions.remove(submission);
							nextSubmission = null;
							break;
						}
					}
				}
				// Extend the pool if needed.
				synchronized (poolLock) {
					if (doExtendPool())
						startNewProcess(null);
				}
			} catch (InterruptedException e) {
				return;
			} catch (Exception e) {
				if (verbose)
					logger.log(Level.SEVERE, "An error occurred while submitting commands.", e);
			}
		}
	}
	/**
	 * Executes the submission on any of the available processes in the pool.
	 * 
	 * @param submission The submission including all information necessary for executing and processing the command(s).
	 * @return A {@link java.util.concurrent.Future} instance of the time it took to execute the command including the submission 
	 * delay in nanoseconds.
	 * @throws IllegalArgumentException If the submission is null.
	 */
	public Future<Long> submit(Submission submission) {
		if (submission == null)
			throw new IllegalArgumentException("The submission cannot be null or empty.");
		InternalSubmission internalSubmission = new InternalSubmission(submission);
		submissions.add(internalSubmission);
		// Return a Future holding the total execution time including the submission delay.
		return new InternalSubmissionFuture(internalSubmission);
	}
	/**
	 * Attempts to shut the process pool including all its processes down.
	 */
	public void shutdown() {
		if (verbose)
			logger.info("Initiating shutdown...");
		close = true;
		mainLoop.interrupt();
		synchronized (poolLock) {
			for (ProcessShell shell : allShells)
				shell.stop(true);
		}
		processExecutor.shutdown();
		taskExecutorService.shutdown();
		if (verbose)
			logger.info("Pool shut down.");
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
		protected void beforeExecute(Thread t, Runnable r) {
			super.beforeExecute(t, r);
			if (verbose)
				logger.info("Process shell " + (ProcessShell) r + " starting execution." + System.lineSeparator() +
						getPoolStats());
		}
		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			super.afterExecute(r, t);
			ProcessShell shell = (ProcessShell) r;
			allShells.remove(shell);
			if (verbose)
				logger.info("Process shell " + shell + " stopped executing." + System.lineSeparator() +
						getPoolStats());
			boolean doExtend = false;
			synchronized (poolLock) {
				if (doExtendPool()) {
					doExtend = true;
					startNewProcess(shell);
				}
			}
			if (!doExtend) {
				try {
					shell.close();
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
					logger.log(Level.SEVERE, executionErrorMessage, e);
					if (handler != null)
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
			prestartLatch.countDown();
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
		final Semaphore semaphore;
		volatile Long submittedTime;
		volatile Long processedTime;
		volatile boolean cancel;
		volatile boolean submitted;
		volatile boolean processed;
		volatile Future<?> future;
		volatile Throwable throwable;
		
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
			semaphore = new Semaphore(0);
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
		public synchronized boolean isCancelled() {
			return originalSubmission.isCancelled() || cancel;
		}
		@Override
		public void onStartedProcessing() {
			submittedTime = System.nanoTime();
			submitted = true;
			semaphore.release();
			originalSubmission.onStartedProcessing();
			numOfExecutingSubmissions.incrementAndGet();
		}
		@Override
		public void onFinishedProcessing() {
			originalSubmission.onFinishedProcessing();
			processedTime = System.nanoTime();
			synchronized (this) {
				processed = true;
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
		public boolean cancel(boolean mayInterruptIfRunning) {
			synchronized (submission) {
				if (submission.isCancelled() || (submission.future != null &&
						submission.future.cancel(mayInterruptIfRunning))) {
					submission.cancel = true;
					submission.notifyAll();
					submissions.remove(submission);
					return true;
				} else
					return false;
			}
		}
		@Override
		public Long get() throws InterruptedException, ExecutionException, CancellationException {
			synchronized (submission) {
				while (!submission.processed && !submission.isCancelled() && submission.throwable == null)
					submission.wait();
			}
			if (submission.isCancelled())
				throw new CancellationException();
			if (submission.throwable != null)
				throw new ExecutionException(submission.throwable);
			return submission.processedTime - submission.receivedTime;
		}
		@Override
		public Long get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
			long timeoutNs = unit.toNanos(timeout);
			long start = System.nanoTime();
			synchronized (submission) {
				while (!submission.processed && !submission.isCancelled() && submission.throwable == null &&
						timeoutNs > 0) {
					submission.wait(timeoutNs/1000000, (int) (timeoutNs%1000000));
					timeoutNs -= (System.nanoTime() - start);
				}
			}
			if (submission.isCancelled())
				throw new CancellationException();
			if (submission.throwable != null)
				throw new ExecutionException(submission.throwable);
			if (timeoutNs <= 0)
				throw new TimeoutException();
			return timeoutNs <= 0 ? null : submission.processedTime - submission.receivedTime;
		}
		@Override
		public boolean isCancelled() {
			return submission.isCancelled();
		}
		@Override
		public boolean isDone() {
			return submission.processed;
		}
		
	}
	
}
