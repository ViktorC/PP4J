package net.viktorc.pp4j.impl;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An implementation of {@link java.util.concurrent.Future} that can be used to cancel, wait on, and retrieve 
 * the return value of a {@link net.viktorc.pp4j.impl.InternalSubmission} instance.
 * 
 * @author Viktor Csomor
 *
 */
public class InternalSubmissionFuture implements Future<Object> {
	
	final InternalSubmission submission;
	
	/**
	 * Constructs a {@link java.util.concurrent.Future} for the specified submission.
	 * 
	 * @param submission The submission to get a {@link java.util.concurrent.Future} for.
	 */
	InternalSubmissionFuture(InternalSubmission submission) {
		this.submission = submission;
	}
	/**
	 * Returns the submission the instance is based on.
	 * 
	 * @return The submission the instance is backed by.
	 */
	InternalSubmission getSubmission() {
		return submission;
	}
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		synchronized (submission.lock) {
			/* If the submission has already been cancelled or if it has already been processed, don't do 
			 * anything and return false. */
			if (submission.cancelled || submission.processed)
				return false;
			/* If it is already being processed and mayInterruptIfRunning is true, interrupt the executor 
			 * thread. */
			if (submission.thread != null) {
				if (mayInterruptIfRunning) {
					submission.cancel();
					submission.thread.interrupt();
				}
				// If mayInterruptIfRunning is false, don't let the submission be cancelled.
			} else
				// If the processing of the submission has not commenced yet, cancel it.
				submission.cancel();
			return submission.cancelled;
		}
	}
	@Override
	public Object get() throws InterruptedException, ExecutionException, CancellationException {
		// Wait until the submission is processed, or cancelled, or fails.
		synchronized (submission.lock) {
			while (!submission.processed && !submission.cancelled && submission.exception == null)
				submission.lock.wait();
			if (submission.cancelled)
				throw new CancellationException(String.format("Submission %s cancelled.", submission));
			if (submission.exception != null)
				throw new ExecutionException(submission.exception);
			return submission.getResult();
		}
	}
	@Override
	public Object get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
		// Wait until the submission is processed, or cancelled, or fails, or the method times out.
		synchronized (submission.lock) {
			long timeoutNs = unit.toNanos(timeout);
			long start = System.nanoTime();
			while (!submission.processed && !submission.cancelled && submission.exception == null &&
					timeoutNs > 0) {
				submission.lock.wait(timeoutNs/1000000, (int) (timeoutNs%1000000));
				timeoutNs -= (System.nanoTime() - start);
			}
			if (submission.cancelled)
				throw new CancellationException(String.format("Submission %s cancelled.", submission));
			if (submission.exception != null)
				throw new ExecutionException(submission.exception);
			if (timeoutNs <= 0)
				throw new TimeoutException(String.format("Submission %s timed out.", submission));
			return submission.getResult();
		}
	}
	@Override
	public boolean isCancelled() {
		return submission.cancelled;
	}
	@Override
	public boolean isDone() {
		return submission.processed;
	}
	
}