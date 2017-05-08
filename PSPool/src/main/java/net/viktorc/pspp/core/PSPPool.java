package net.viktorc.pspp.core;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
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
	private final ThreadPoolExecutor poolExecutor;
	private final List<ProcessManager> activeProcesses;
	private final AtomicInteger totalNumOfProcesses;
	private final Object poolLock;
	private final CountDownLatch latch;
	private final ExecutorService commandExecutor;
	private final BlockingQueue<CommandSubmission> commandQueue;
	private final AtomicInteger numOfExecutingCommands;
	private final Logger logger;
	private volatile boolean verbose;
	private volatile boolean close;

	/**
	 * Constructs a pool of identical processes. The initial size of the pool is the minimum pool size. The size of the pool 
	 * is dynamically adjusted based on the number of requests and running processes.
	 * 
	 * @param builder The process builder for building the pooled processes.
	 * @param listener A {@link #ProcessListener} instance that is added to each process in the pool. It should be stateless 
	 * as the same instance is used for all processes.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @throws IOException If the process cannot be started.
	 * @throws IllegalArgumentException If the minimum pool size is less than 0, or the maximum pool size is less than the 
	 * minimum pool size or 1, or the reserve size is less than 0 or greater than the minimum pool size, or the listener is null.
	 */
	public PSPPool(ProcessBuilder builder, ProcessListener listener, int minPoolSize, int maxPoolSize,
			int reserveSize, long keepAliveTime) throws IOException, IllegalArgumentException {
		if (minPoolSize < 0)
			throw new IllegalArgumentException("The minimum pool size has to be greater than 0.");
		if (maxPoolSize < 1 || maxPoolSize < minPoolSize)
			throw new IllegalArgumentException("The maximum pool size has to be at least 1 and at least as great as the " +
					"minimum pool size.");
		if (reserveSize < 0 || reserveSize > minPoolSize)
			throw new IllegalArgumentException("The reserve has to be greater than 0 and less than the minimum pool size.");
		if (builder == null)
			throw new IllegalArgumentException("The process builder cannot be null.");
		this.builder = builder;
		this.listener = listener;
		this.minPoolSize = minPoolSize;
		this.maxPoolSize = maxPoolSize;
		this.reserveSize = reserveSize;
		this.keepAliveTime = keepAliveTime;
		this.verbose = verbose;
		totalNumOfProcesses = new AtomicInteger(0);
		poolExecutor = new ThreadPoolExecutor(minPoolSize, maxPoolSize, keepAliveTime, TimeUnit.MILLISECONDS,
				new SynchronousQueue<>());
		activeProcesses = new CopyOnWriteArrayList<>();
		poolLock = new Object();
		latch = new CountDownLatch(minPoolSize);
		commandExecutor = Executors.newCachedThreadPool();
		commandQueue = new LinkedBlockingQueue<>();
		numOfExecutingCommands = new AtomicInteger(0);
		logger = Logger.getAnonymousLogger();
		for (int i = 0; i < minPoolSize; i++)
			addNewProccess();
		// Wait for the processes in the initial pool to start up.
		try {
			latch.await();
			// Allow the start up locks in the managers to be released.
			Thread.sleep(100);
		} catch (InterruptedException e) {
			if (verbose)
				logger.log(Level.SEVERE, "Error while waiting for the pool to start up.", e);
		}
		// Start the thread responsible for submitting commands.
		(new Thread(this::mainLoop)).start();
	}
	/**
	 * Sets whether the pool should log to the console.
	 * 
	 * @param on Whether the pool should log to the console.
	 */
	public void setLogging(boolean on) {
		verbose = on;
	}
	/**
	 * Executes the command on any of the available processes in the pool. It blocks until the command could successfully be 
	 * submitted for execution.
	 * 
	 * @param command The command to send to the process' standard in.
	 * @param commandListener The {@link #CommandListener} instance that possibly processes the outputs of 
	 * the process and determines when the process has finished processing the sent command.
	 * @param cancelProcessAfterwards Whether the process that executed the command should be cancelled after 
	 * the execution of the command.
	 * @return The time it took to execute the command including the submission delay in nanoseconds.
	 * @throws IllegalArgumentException If the command is null or empty or if the command listener is null.
	 */
	public Future<Long> submitCommand(String command, CommandListener commandListener, boolean cancelProcessAfterwards) {
		if (command == null)
			throw new IllegalArgumentException("The command cannot be null or empty.");
		if (commandListener == null)
			throw new IllegalArgumentException("The command listener cannot be null.");
		CommandListener listener = new CommandListener() {
			
			@Override
			public boolean onNewStandardOutput(String standardOutput) {
				return commandListener.onNewStandardOutput(standardOutput);
			}
			@Override
			public boolean onNewErrorOutput(String errorOutput) {
				return commandListener.onNewErrorOutput(errorOutput);
			}
			@Override
			public void onSubmitted() {
				commandListener.onSubmitted();
				numOfExecutingCommands.incrementAndGet();
			}
			@Override
			public void onFinished() {
				commandListener.onFinished();
				numOfExecutingCommands.decrementAndGet();
			}
		};
		CommandSubmission submission = new CommandSubmission(command, listener, cancelProcessAfterwards);
		commandQueue.add(submission);
		synchronized (this) {
			notifyAll();
		}
		return new Future<Long>() {

			private boolean cancelled;
			
			@Override
			public synchronized boolean cancel(boolean mayInterruptIfRunning) {
				commandQueue.remove(submission);
				if (mayInterruptIfRunning) {
					Future<?> submissionFuture = submission.getFuture();
					if (submissionFuture != null)
						submissionFuture.cancel(true);
				}
				cancelled = true;
				synchronized (submission) {
					submission.notifyAll();
				}
				return true;
			}
			@Override
			public Long get() throws InterruptedException, ExecutionException {
				while (!submission.isProcessed() && !cancelled) {
					synchronized (submission) {
						submission.wait();
					}
				}
				return cancelled ? null : submission.getProcessedTime() - submission.getCreationTime();
			}
			@Override
			public Long get(long timeout, TimeUnit unit)
					throws InterruptedException, ExecutionException, TimeoutException {
				long timeoutNs = unit.toNanos(timeout);
				long start = System.nanoTime();
				while (!submission.isProcessed() && !cancelled && timeoutNs > 0) {
					synchronized (submission) {
						try {
							submission.wait(timeoutNs/1000000, (int) (timeoutNs%1000000));
							break;
						} catch (InterruptedException e) {
							timeoutNs -= (System.nanoTime() - start);
						}
					}
				}
				return cancelled || timeoutNs <= 0 ? null :
						submission.getProcessedTime() - submission.getCreationTime();
			}
			@Override
			public boolean isCancelled() {
				return cancelled;
			}
			@Override
			public boolean isDone() {
				return submission.isProcessed();
			}
		};
	}
	/**
	 * Logs the number of active, queued, and currently executing processes.
	 */
	private void logPoolStats() {
		logger.info("Total processes: " + totalNumOfProcesses.get() + "; acitve processes: " + activeProcesses.size() +
				"; submitted commands: " + (numOfExecutingCommands.get() + commandQueue.size()));
	}
	/**
	 * Returns whether a new process should be added to the pool.
	 * 
	 * @return Whether the pool should be extended.
	 */
	private boolean doAddNewProcess() {
		return !close && (totalNumOfProcesses.get() <= minPoolSize || (totalNumOfProcesses.get() <=
				Math.min(maxPoolSize, numOfExecutingCommands.get() + commandQueue.size() + reserveSize)));
	}
	/**
	 * Adds a new {@link #ProcessManager} instance to the process pool.
	 * 
	 * @throws IOException If the process cannot be started.
	 */
	private void addNewProccess() throws IOException {
		totalNumOfProcesses.incrementAndGet();
		ProcessManager p = new ProcessManager(builder, keepAliveTime);
		if (listener != null)
			p.addListener(listener);
		p.addListener(new ProcessListener() {
			
			@Override
			public void onStarted(ProcessManager manager) {
				activeProcesses.add(manager);
				if (verbose) {
					logger.info("Process manager " + manager + " started executing.");
					logPoolStats();
				}
				latch.countDown();
			}
			@Override
			public void onTermination(ProcessManager manager, int resultCode) {
				activeProcesses.remove(manager);
				if (verbose) {
					logger.info("Process manager " + manager + " stopped executing.");
					logPoolStats();
				}
				synchronized (poolLock) {
					if (doAddNewProcess())
						poolExecutor.execute(manager);
					else {
						try {
							p.close();
							if (verbose)
								logger.info("Shutting down process manager " + manager + ".");
							totalNumOfProcesses.decrementAndGet();
						} catch (Exception e) {
							if (verbose)
								logger.log(Level.SEVERE, "Error while shutting down process manager " + manager + ".", e);
						}
					}
				}
			}
		});
		poolExecutor.execute(p);
	}
	/**
	 * A method that handles the submission of commands from the queue to the processes.
	 */
	private void mainLoop() {
		Optional<CommandSubmission> optionalSubmission = Optional.empty();
		while (!close) {
			try {
				if (!optionalSubmission.isPresent()) {
					while (!(optionalSubmission = Optional.ofNullable(commandQueue.peek())).isPresent()) {
						synchronized (this) {
							try {
								wait();
							} catch (InterruptedException e) {
								if (close)
									return;
							}
						}
					}
				}
				CommandSubmission submission = optionalSubmission.get();
				for (ProcessManager manager : activeProcesses) {
					if (manager.isReady()) {
						submission.setFuture(commandExecutor.submit(() -> {
							try {
								if (manager.executeCommand(submission)) {
									if (verbose)
										logger.info(String.format("Command %s processed; submission took: %.3f;" +
												" execution took %.3f.%n", submission.getCommand(),
												(float) ((double) (submission.getSubmissionTime() -
												submission.getCreationTime())/1000000000),
												(float) ((double) (submission.getProcessedTime() -
												submission.getSubmissionTime())/1000000000)));
								}
							} catch (IOException e) {
								if (verbose)
									logger.log(Level.SEVERE, "Error while submitting command " +
											submission.getCommand() + ".", e);
							}
						}));
						commandQueue.remove(submission);
						optionalSubmission = Optional.empty();
						break;
					}
				}
				synchronized (poolLock) {
					if (doAddNewProcess()) {
						try {
							addNewProccess();
						} catch (IOException e) {
							if (verbose)
								logger.log(Level.SEVERE, "Error while submitting a new process.", e);
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
		commandExecutor.shutdown();
		for (ProcessManager p : activeProcesses) {
			p.clearListeners();
			p.close();
		}
		poolExecutor.shutdown();
	}
	
}
