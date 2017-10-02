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
 * 
 * 
 * @author Viktor Csomor
 *
 */
public class StandardProcessExecutor extends AbstractProcessExecutor implements AutoCloseable {

	private final Semaphore startupSemaphore;
	private final Lock runLock;
	
	/**
	 * 
	 * @param manager
	 */
	public StandardProcessExecutor(ProcessManager manager) {
		super(manager, Executors.newCachedThreadPool(), false);
		startupSemaphore = new Semaphore(0);
		runLock = new ReentrantLock();
	}
	/**
	 * 
	 * 
	 * @throws InterruptedException 
	 */
	public void start() throws InterruptedException {
		if (runLock.tryLock()) {
			try {
				threadPool.submit(this);
				startupSemaphore.acquire();
			} finally {
				runLock.unlock();
			}
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
