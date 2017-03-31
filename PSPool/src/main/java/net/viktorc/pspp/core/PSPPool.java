package net.viktorc.pspp.core;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class for maintaining a pool of identical pre-started processes.
 * 
 * @author A6714
 *
 */
public class PSPPool implements AutoCloseable {
	
	private final String[] processCommands;
	private final ProcessListener listener;
	private final int minPoolSize;
	private final int maxPoolSize;
	private final int queueSize;
	private final long keepAliveTime;
	private final Object submitLock;
	private final AtomicInteger submittedProcesses;
	private final BlockingQueue<ProcessManager> activeProcesses;
	private final BlockingQueue<Runnable> queuedProcesses;
	private final ThreadPoolExecutor executor;
	private final Logger logger;
	private final CountDownLatch latch;
	private volatile boolean verbose;
	private volatile boolean close;

	/**
	 * Constructs a pool of identical processes. The initial size of the pool is the minimum pool size. The size of the pool 
	 * is dynamically adjusted based on the number of requests and running processes.
	 * 
	 * @param processCommands The commands to start the process that will be pooled.
	 * @param listener A {@link #ProcessListener} instance that is added to each process in the pool. It should be stateless 
	 * as the same instance is used for all processes.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param queueSize The number of tasks the queue can accommodate. If it is 0 or less, the queue will not be limited.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @throws IOException If the process cannot be started.
	 * @throws IllegalArgumentException If minPoolSize is less than 1 or maxPoolSize is less than minPoolSize.
	 */
	public PSPPool(String[] processCommands, ProcessListener listener, int minPoolSize, int maxPoolSize,
			int queueSize, long keepAliveTime) throws IOException, IllegalArgumentException {
		if (minPoolSize < 1)
			throw new IllegalArgumentException("The minimum pool size has to be at least 1.");
		if (maxPoolSize < minPoolSize)
			throw new IllegalArgumentException("The maximum pool size has to be greater than the minimum pool size.");
		this.processCommands = processCommands;
		this.listener = listener;
		this.minPoolSize = minPoolSize;
		this.maxPoolSize = maxPoolSize;
		this.queueSize = queueSize;
		this.keepAliveTime = keepAliveTime;
		this.verbose = verbose;
		logger = Logger.getAnonymousLogger();
		submitLock = new Object();
		submittedProcesses = new AtomicInteger(0);
		activeProcesses = new LinkedBlockingQueue<>();
		queuedProcesses = queueSize > 0 ? new LinkedBlockingQueue<>(queueSize) : new LinkedBlockingQueue<>();
		executor = new ThreadPoolExecutor(minPoolSize, maxPoolSize, keepAliveTime, TimeUnit.MILLISECONDS, queuedProcesses);
		latch = new CountDownLatch(minPoolSize);
		for (int i = 0; i < minPoolSize; i++)
			submitNewProcess();
		// Wait for the processes in the initial pool to start up.
		try {
			latch.await();
			// Allow the start up locks in the managers to be released.
			Thread.sleep(100);
		} catch (InterruptedException e) {
			if (verbose)
				logger.log(Level.SEVERE, "Error while waiting for the pool to start up.", e);
		}
	}
	/**
	 * Constructs a pool of identical processes. The initial size of the pool is the minimum pool size. The size of the pool 
	 * is dynamically adjusted based on the number of requests and running processes.
	 * 
	 * @param processCommands The commands to start the process that will be pooled.
	 * @param listener A {@link #ProcessListener} instance that is added to each process in the pool. It should be stateless 
	 * as the same instance is used for all processes.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param queueSize The number of tasks the queue can accommodate. If it is less than 0, the queue will not be limited.
	 * @throws IOException If the process cannot be started.
	 * @throws IllegalArgumentException If minPoolSize is less than 1 or maxPoolSize is less than minPoolSize.
	 */
	public PSPPool(String[] processCommands, ProcessListener listener, int minPoolSize, int maxPoolSize, int queueSize)
			throws IOException, IllegalArgumentException {
		this(processCommands, listener, minPoolSize, maxPoolSize, queueSize, 0);
	}
	/**
	 * Constructs a pool of identical processes. The initial size of the pool is the minimum pool size. The size of the pool 
	 * is dynamically adjusted based on the number of requests and running processes.
	 * 
	 * @param processCommands The commands to start the process that will be pooled.
	 * @param listener A {@link #ProcessListener} instance that is added to each process in the pool. It should be stateless 
	 * as the same instance is used for all processes.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @throws IOException If the process cannot be started.
	 * @throws IllegalArgumentException If minPoolSize is less than 1 or maxPoolSize is less than minPoolSize.
	 */
	public PSPPool(String[] processCommands, ProcessListener listener, int minPoolSize, int maxPoolSize, long keepAliveTime)
			throws IOException, IllegalArgumentException {
		this(processCommands, listener, minPoolSize, maxPoolSize, -1, keepAliveTime);
	}
	/**
	 * Constructs a pool of identical processes. The initial size of the pool is the minimum pool size. The size of the pool 
	 * is dynamically adjusted based on the number of requests and running processes.
	 * 
	 * @param processCommands The commands to start the process that will be pooled.
	 * @param listener A {@link #ProcessListener} instance that is added to each process in the pool. It should be stateless 
	 * as the same instance is used for all processes.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @throws IOException If the process cannot be started.
	 * @throws IllegalArgumentException If minPoolSize is less than 1 or maxPoolSize is less than minPoolSize.
	 */
	public PSPPool(String[] processCommands, ProcessListener listener, int minPoolSize, int maxPoolSize)
			throws IOException, IllegalArgumentException {
		this(processCommands, listener, minPoolSize, maxPoolSize, -1, 0);
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
	 * Adds a new {@link #ProcessManager} instance to the process pool.
	 * 
	 * @throws IOException If the process cannot be started.
	 */
	private void submitNewProcess() throws IOException {
		submittedProcesses.incrementAndGet();
		ProcessManager p = new ProcessManager(processCommands, keepAliveTime);
		if (listener != null)
			p.addListener(listener);
		p.addListener(new ProcessListener() {
			
			@Override
			public void onStarted(ProcessManager manager) {
				activeProcesses.add(manager);
				if (verbose) {
					logger.info("Process manager #" + Long.toHexString(manager.getId()) + " started executing.");
					logger.info("Active processes: " + activeProcesses.size() + "; queued processes: " + queuedProcesses.size() + ".");
				}
				latch.countDown();
			}
			@Override
			public void onTermination(ProcessManager manager, int resultCode) {
				activeProcesses.remove(manager);
				if (verbose) {
					logger.info("Process manager #" + Long.toHexString(manager.getId()) + " stopped executing.");
					logger.info("Active processes: " + activeProcesses.size() + "; queued processes: " + queuedProcesses.size() + ".");
				}
				if (!close && submittedProcesses.get() <= minPoolSize)
					executor.execute(manager);
				else {
					try {
						p.close();
						if (verbose)
							logger.info("Shutting down process manager #" + Long.toHexString(manager.getId()) + ".");
						submittedProcesses.decrementAndGet();
					} catch (Exception e) {
						if (verbose)
							logger.log(Level.SEVERE, "Error while shutting down process manager #" +
									Long.toHexString(manager.getId()) + ".", e);
					}
				}
			}
		});
		executor.execute(p);
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
	 * @return A {@link #java.util.concurrent.Future<?> Future} instance for the submitted command.
	 */
	public Future<?> executeCommand(String command, CommandListener commandListener, boolean cancelProcessAfterwards) {
		boolean submittedNewProcess = false;
		while (true) {
			for (ProcessManager p : activeProcesses) {
				try {
					Future<?> future = p.sendCommand(command, commandListener, cancelProcessAfterwards);
					if (future != null)
						return future;
				} catch (IOException e) {
					if (verbose)
						logger.log(Level.SEVERE, "Error while sending the command '" + command + "' to process manager #" +
								Long.toHexString(p.getId()) + ".", e);
				}
			}
			if (!submittedNewProcess) {
				synchronized (submitLock) {
					if (queueSize < 1 || submittedProcesses.get() < maxPoolSize + queueSize) {
						try {
							submitNewProcess();
							submittedNewProcess = true;
						} catch (IOException e) {
							if (verbose)
								logger.log(Level.SEVERE, "Error while submitting a new process.", e);
						}
					}
				}
			}
		}
	}
	@Override
	public void close() throws Exception {
		close = true;
		for (ProcessManager p : activeProcesses) {
			p.clearListeners();
			p.close();
		}
		executor.shutdown();
	}
	
}
