package net.viktorc.pp4j.impl;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.Submission;

/**
 * An implementation of the {@link net.viktorc.pp4j.api.ProcessExecutor} interface that allows for the 
 * running and management of a process based on a {@link net.viktorc.pp4j.api.ProcessManager} instance 
 * to enable the execution of submissions in this process. The process may be launched by invoking the 
 * {@link #start()} method and submissions may be executed in the process using the 
 * {@link #execute(Submission)} method
 * 
 * @author Viktor Csomor
 *
 */
public class StandardProcessExecutor extends AbstractProcessExecutor implements AutoCloseable {

	private final Semaphore startupSemaphore;
	private final Lock runLock;
	
	/**
	 * Constructs a process executor instance using the argument to manage the life-cycle of the process.
	 * 
	 * @param manager The manager of the underlying process.
	 */
	public StandardProcessExecutor(ProcessManager manager) {
		super(manager, Executors.newCachedThreadPool(), false);
		startupSemaphore = new Semaphore(0);
		runLock = new ReentrantLock();
	}
	/**
	 * It launches the underlying process and blocks until it is ready for the execution of submissions.
	 * 
	 * @throws InterruptedException If the thread is interrupted while waiting for the startup to 
	 * complete.
	 * @throws IllegalStateException If the process is already running.
	 */
	public void start() throws InterruptedException {
		if (runLock.tryLock()) {
			try {
				threadPool.submit(this);
			} finally {
				runLock.unlock();
			}
			startupSemaphore.acquire();
		} else
			throw new IllegalStateException("The executor is already running.");
	}
	@Override
	public boolean execute(Submission<?> submission) throws IOException, InterruptedException {
		submissionLock.lock();
		try {
			return super.execute(submission);
		} finally {
			submissionLock.unlock();
		}
	}
	@Override
	public void run() {
		runLock.lock();
		try {
			super.run();
		} finally {
			runLock.unlock();
		}
	}
	@Override
	protected void onExecutorStartup(boolean orderly) {
		startupSemaphore.release();
	}
	@Override
	protected void onExecutorTermination() { }
	@Override
	public void close() throws Exception {
		stop(true);
		threadPool.shutdown();
		threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
	}

}
