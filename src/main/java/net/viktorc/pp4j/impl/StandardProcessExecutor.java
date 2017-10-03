/*
 * Copyright 2017 Viktor Csomor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.viktorc.pp4j.impl;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.viktorc.pp4j.api.ProcessExecutor;
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
public class StandardProcessExecutor extends AbstractProcessExecutor {

	private final Semaphore startupSemaphore;
	private final Lock runLock;
	private boolean started;
	
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
	 * @throws IllegalStateException If this method has already been invoked on this instance before..
	 */
	public void start() throws InterruptedException {
		if (runLock.tryLock()) {
			if (started)
				throw new IllegalStateException("The executor can only run once.");
			try {
				threadPool.submit(this);
				started = true;
			} finally {
				runLock.unlock();
			}
			startupSemaphore.acquire();
		} else
			throw new IllegalStateException("The executor is already running.");
	}
	/**
	 * It prompts the currently running process, if there is one, to terminate. Once the process has been 
	 * successfully terminated, subsequent calls are ignored and return true.
	 * 
	 * @param forcibly Whether the process should be killed forcibly or using the 
	 * {@link net.viktorc.pp4j.api.ProcessManager#terminateGracefully(ProcessExecutor)} method of the 
	 * <code>ProcessManager</code> instance assigned to the executor. The latter might be ineffective if 
	 * the process is currently executing a command or has not started up.
	 * @return Whether the process was successfully terminated.
	 */
	public boolean stop(boolean forcibly) {
		return super.stop(forcibly);
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
	protected void onExecutorTermination() {
		threadPool.shutdown();
	}

}
