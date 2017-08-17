package net.viktorc.pp4j.impl;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.Submission;

/**
 * An implementation of the {@link net.viktorc.pp4j.api.Submission} interface for wrapping other instances 
 * of the interface into a class that allows for waiting for the completion of the submission, the 
 * cancellation thereof, and the tracking of the time its processing took.
 * 
 * @author Viktor Csomor
 *
 */
public class InternalSubmission implements Submission {
	
	final Submission origSubmission;
	final long receivedTime;
	final Object lock;
	Thread thread;
	Exception exception;
	volatile long submittedTime;
	volatile long processedTime;
	volatile boolean processed;
	volatile boolean cancelled;
	
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param origSubmission The submission to wrap into an internal submission with extended features.
	 * @throws IllegalArgumentException If the submission is null.
	 */
	InternalSubmission(Submission originalSubmission) {
		if (originalSubmission == null)
			throw new IllegalArgumentException("The submission cannot be null.");
		this.origSubmission = originalSubmission;
		receivedTime = System.nanoTime();
		lock = new Object();
	}
	/**
	 * Returns a reference to the wrapped submission instance.
	 * 
	 * @return The original submission wrapped by this instance.
	 */
	public Submission getOriginalSubmission() {
		return origSubmission;
	}
	/**
	 * Sets the thread that is executing the submission.
	 * 
	 * @param t The thread that executes the submission.
	 */
	void setThread(Thread t) {
		synchronized (lock) {
			thread = t;
		}
	}
	/**
	 * Sets the exception thrown during the execution of the submission if there was any.
	 * 
	 * @param e The exception thrown during the execution of the submission.
	 */
	void setException(Exception e) {
		// Notify the InternalSubmissionFuture that an exception was thrown while processing the submission.
		synchronized (lock) {
			exception = e;
			lock.notifyAll();
		}
	}
	/**
	 * Returns whether the <code>cancelled</code> flag of the submission has been set to true.
	 * 
	 * @return Whether the submission has been cancelled.
	 */
	boolean isCancelled() {
		synchronized (lock) {
			return cancelled;
		}
	}
	/**
	 * Sets the <code>cancelled</code> flag of the submission to true.
	 */
	void cancel() {
		synchronized (lock) {
			cancelled = true;
			lock.notifyAll();
		}
	}
	@Override
	public List<Command> getCommands() {
		return origSubmission.getCommands();
	}
	@Override
	public boolean doTerminateProcessAfterwards() {
		return origSubmission.doTerminateProcessAfterwards();
	}
	@Override
	public Object getResult() throws ExecutionException {
		return origSubmission.getResult();
	}
	@Override
	public void onStartedProcessing() {
		// If it is the first time the submission is submitted to a process...
		if (submittedTime == 0) {
			submittedTime = System.nanoTime();
			origSubmission.onStartedProcessing();
		}
	}
	@Override
	public void onFinishedProcessing() {
		origSubmission.onFinishedProcessing();
		processedTime = System.nanoTime();
		// Notify the InternalSubmissionFuture that the submission has been processed.
		synchronized (lock) {
			processed = true;
			lock.notifyAll();
		}
	}
	@Override
	public String toString() {
		return String.format("{commands:[%s],terminate:%s}@%s", String.join(",", origSubmission.getCommands()
				.stream().map(c -> "\"" + c.getInstruction() + "\"").collect(Collectors.toList())),
				Boolean.toString(origSubmission.doTerminateProcessAfterwards()),
				Integer.toHexString(hashCode()));
	}
	
}