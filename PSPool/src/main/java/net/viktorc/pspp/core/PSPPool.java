package net.viktorc.pspp.core;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
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

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

/**
 * A class for maintaining and managing a pool of identical pre-started processes.
 * 
 * @author A6714
 *
 */
public class PSPPool implements AutoCloseable {
	
	private final int minPoolSize;
	private final int maxPoolSize;
	private final int reserveSize;
	private final GenericObjectPool<ProcessManager> managerPool;
	private final ThreadPoolExecutor poolExecutor;
	private final List<ProcessManager> activeProcesses;
	private final Object poolLock;
	private final CountDownLatch startupLatch;
	private final ExecutorService commandExecutor;
	private final Queue<CommandSubmission> commandQueue;
	private final Semaphore submissionSemaphore;
	private final AtomicInteger numOfExecutingCommands;
	private final Logger logger;
	private volatile boolean submissionSuccessful;
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
	 * minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum pool size, or the listener is null.
	 */
	public PSPPool(ProcessBuilder builder, ProcessListener listener, int minPoolSize, int maxPoolSize,
			int reserveSize, long keepAliveTime) throws IOException, IllegalArgumentException {
		if (minPoolSize < 0)
			throw new IllegalArgumentException("The minimum pool size has to be greater than 0.");
		if (maxPoolSize < 1 || maxPoolSize < minPoolSize)
			throw new IllegalArgumentException("The maximum pool size has to be at least 1 and at least as great as the " +
					"minimum pool size.");
		if (reserveSize < 0 || reserveSize > maxPoolSize)
			throw new IllegalArgumentException("The reserve has to be greater than 0 and less than the maximum pool size.");
		if (builder == null)
			throw new IllegalArgumentException("The process builder cannot be null.");
		this.minPoolSize = minPoolSize;
		this.maxPoolSize = maxPoolSize;
		this.reserveSize = reserveSize;
		this.verbose = verbose;
		managerPool = new GenericObjectPool<>(new PooledObjectFactory<ProcessManager>() {

			@Override
			public PooledObject<ProcessManager> makeObject() throws Exception {
				ProcessManager manager = new ProcessManager(builder, keepAliveTime);
				if (listener != null)
					manager.addListener(listener);
				manager.addListener(new ProcessListener() {
					
					@Override
					public void onStarted(ProcessManager manager) {
						activeProcesses.add(manager);
						if (verbose) {
							logger.info("Process manager " + manager + " started executing.");
							logPoolStats();
						}
						startupLatch.countDown();
					}
					@Override
					public void onTermination(int resultCode) {
						activeProcesses.remove(manager);
						if (verbose) {
							logger.info("Process manager " + manager + " stopped executing.");
							logPoolStats();
						}
						synchronized (poolLock) {
							if (doStartNewProcess(true)) {
								startNewProcess();
							}
						}
						try {
							managerPool.returnObject(manager);
						} catch (Exception e) {
							if (verbose)
								logger.log(Level.SEVERE, "Error deactivating process manager " + manager + ".");
						}
					}
				});
				return new DefaultPooledObject<ProcessManager>(manager);
			}
			@Override
			public void activateObject(PooledObject<ProcessManager> p) throws Exception { }
			@Override
			public boolean validateObject(PooledObject<ProcessManager> p) {
				return !p.getObject().isRunning();
			}
			@Override
			public void passivateObject(PooledObject<ProcessManager> p) throws Exception { }
			@Override
			public void destroyObject(PooledObject<ProcessManager> p) throws Exception {
				ProcessManager manager = p.getObject();
				manager.clearListeners();
				manager.close();
			}
			
		});
		int actualMinSize = Math.max(minPoolSize, reserveSize);
		managerPool.setMinIdle(actualMinSize);
		poolExecutor = new ThreadPoolExecutor(actualMinSize, maxPoolSize, keepAliveTime > 0 ? keepAliveTime : Long.MAX_VALUE,
				keepAliveTime > 0 ? TimeUnit.MILLISECONDS : TimeUnit.DAYS, new SynchronousQueue<>());
		activeProcesses = new CopyOnWriteArrayList<>();
		poolLock = new Object();
		startupLatch = new CountDownLatch(actualMinSize);
		commandExecutor = Executors.newCachedThreadPool();
		commandQueue = new ConcurrentLinkedQueue<>();
		submissionSemaphore = new Semaphore(0);
		numOfExecutingCommands = new AtomicInteger(0);
		logger = Logger.getAnonymousLogger();
		try {
			managerPool.preparePool();
		} catch (Exception e) {
			if (verbose)
				logger.log(Level.SEVERE, "Error while preparing the process managers.", e);
		}
		for (int i = 0; i < actualMinSize; i++) {
			synchronized (poolLock) {
				startNewProcess();
			}
		}
		// Wait for the processes in the initial pool to start up.
		try {
			startupLatch.await();
			// Allow the start up locks in the managers to be released.
			Thread.sleep(10);
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
	 * Logs the number of active, queued, and currently executing processes.
	 */
	private void logPoolStats() {
		logger.info("Total processes: " + poolExecutor.getActiveCount() + "; acitve processes: " + activeProcesses.size() +
				"; submitted commands: " + (numOfExecutingCommands.get() + commandQueue.size()));
	}
	/**
	 * Returns whether a new process should be started.
	 * 
	 * @param alreadyInPool Whether the manager whose process is to be started is already in the pool.
	 * @return Whether a new process should be started.
	 */
	private boolean doStartNewProcess(boolean alreadyInPool) {
		int adjustment = alreadyInPool ? 0 : 1;
		return !close && (poolExecutor.getActiveCount() + adjustment <= minPoolSize || (poolExecutor.getActiveCount() +
				adjustment <= Math.min(maxPoolSize, numOfExecutingCommands.get() + commandQueue.size() + reserveSize)));
	}
	/**
	 * Adds a new {@link #ProcessManager} instance to the process pool.
	 * 
	 * @throws IOException If the process cannot be started.
	 * @throws RejectedExecutionException If the process manager cannot be added to the pool.
	 */
	private void startNewProcess() {
		try {
			poolExecutor.execute(managerPool.borrowObject());
		} catch (Exception e) {
			if (verbose)
				logger.log(Level.WARNING, "Attempt to extend the pool failed.", e);
		}
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
						Future<?> future = commandExecutor.submit(() -> {
							try {
								if (manager.executeCommand(submission)) {
									if (verbose)
										logger.info(String.format("Command \"%s\" processed; submission delay: %.3f;" +
												" execution time: %.3f.%n", submission.getCommand(),
												(float) ((double) (submission.getSubmissionTime() -
												submission.getCreationTime())/1000000000),
												(float) ((double) (submission.getProcessedTime() -
												submission.getSubmissionTime())/1000000000)));
								} else {
									submissionSuccessful = false;
									submissionSemaphore.release();
								}
							} catch (IOException e) {
								if (verbose)
									logger.log(Level.SEVERE, "Error while submitting command " +
											submission.getCommand() + ".", e);
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
				synchronized (poolLock) {
					if (doStartNewProcess(false))
						startNewProcess();
				}
			} catch (Exception e) {
				if (verbose)
					logger.log(Level.SEVERE, "An error occurred while submitting commands.", e);
			}
		}
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
	public Future<Long> submitCommand(CommandSubmission submission) {
		if (submission == null)
			throw new IllegalArgumentException("The command cannot be null or empty.");
		CommandListener listener = new CommandListener() {
			
			@Override
			public boolean onNewStandardOutput(String standardOutput) {
				return submission.getListener().onNewStandardOutput(standardOutput);
			}
			@Override
			public boolean onNewErrorOutput(String errorOutput) {
				return submission.getListener().onNewErrorOutput(errorOutput);
			}
			@Override
			public void onSubmitted() {
				submissionSuccessful = true;
				submissionSemaphore.release();
				submission.getListener().onSubmitted();
				numOfExecutingCommands.incrementAndGet();
			}
			@Override
			public void onFinished() {
				submission.getListener().onFinished();
				numOfExecutingCommands.decrementAndGet();
			}
			
		};
		CommandSubmission internalSubmission = new CommandSubmission(submission.getCommand(), listener, submission.doCancelAfterwards());
		commandQueue.add(internalSubmission);
		synchronized (this) {
			notifyAll();
		}
		return new Future<Long>() {

			private boolean cancelled;
			
			@Override
			public synchronized boolean cancel(boolean mayInterruptIfRunning) {
				commandQueue.remove(internalSubmission);
				if (mayInterruptIfRunning) {
					Future<?> submissionFuture = internalSubmission.getFuture();
					if (submissionFuture != null)
						submissionFuture.cancel(true);
				}
				cancelled = true;
				synchronized (internalSubmission) {
					internalSubmission.notifyAll();
				}
				return true;
			}
			@Override
			public Long get() throws InterruptedException, ExecutionException {
				while (!internalSubmission.isProcessed() && !cancelled) {
					synchronized (internalSubmission) {
						internalSubmission.wait();
					}
				}
				return cancelled ? null : internalSubmission.getProcessedTime() - internalSubmission.getCreationTime();
			}
			@Override
			public Long get(long timeout, TimeUnit unit)
					throws InterruptedException, ExecutionException, TimeoutException {
				long timeoutNs = unit.toNanos(timeout);
				long start = System.nanoTime();
				while (!internalSubmission.isProcessed() && !cancelled && timeoutNs > 0) {
					synchronized (internalSubmission) {
						try {
							internalSubmission.wait(timeoutNs/1000000, (int) (timeoutNs%1000000));
							break;
						} catch (InterruptedException e) {
							timeoutNs -= (System.nanoTime() - start);
						}
					}
				}
				return cancelled || timeoutNs <= 0 ? null :
						internalSubmission.getProcessedTime() - internalSubmission.getCreationTime();
			}
			@Override
			public boolean isCancelled() {
				return cancelled;
			}
			@Override
			public boolean isDone() {
				return internalSubmission.isProcessed();
			}
			
		};
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
