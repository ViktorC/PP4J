package net.viktorc.pspp.core;

import java.util.concurrent.Future;

/**
 * A class that holds all information necessary for executing commands in {@link #ProcessManager} instances.
 * 
 * @author Viktor
 *
 */
public class CommandSubmission {
	
	private final String command;
	private final CommandListener listener;
	private final boolean cancelAfterwards;
	private final long creationTime;
	private Long submissionTime;
	private Long processedTime;
	private boolean processed;
	private volatile Future<?> future;
	
	/**
	 * Constructs an instance.
	 * 
	 * @param command The command to write to the process' standard in.
	 * @param listener An instance of {@link #CommandListener} for consuming the subsequent outputs of the process and 
	 * for determining whether the process has finished processing the command and is ready for new commands based on 
	 * these outputs. If it is null, the process manager will not accept any other command for the rest of the current 
	 * progress' life cycle and the cancelAfterwards parameter is rendered ineffective.
	 * @param cancelAfterwards Whether the process should be cancelled after the execution of the command.
	 */
	public CommandSubmission(String command, CommandListener listener, boolean cancelAfterwards) {
		this.command = command;
		this.listener = listener;
		this.cancelAfterwards = cancelAfterwards;
		creationTime = System.nanoTime();
	}
	/**
	 * Returns the command to write to the process' standard in.
	 * 
	 * @return The command to write to the process' standard in.
	 */
	public String getCommand() {
		return command;
	}
	/**
	 * Returns the {@link #CommandListener} instance for consuming the subsequent outputs of the process and for 
	 * determining whether the process has finished processing the command and is ready for new commands based on 
	 * these outputs.
	 * 
	 * @return The {@link #CommandListener} instance associated with the command.
	 */
	public CommandListener getListener() {
		return listener;
	}
	/**
	 * Returns whether the process should be cancelled after the execution of the command.
	 * 
	 * @return Whether the process should be cancelled after the execution of the command.
	 */
	public boolean doCancelAfterwards() {
		return cancelAfterwards;
	}
	/**
	 * Returns the time when the instance was constructed in nanoseconds.
	 * 
	 * @return The time when the instance was constructed in nanoseconds.
	 */
	public long getCreationTime() {
		return creationTime;
	}
	/**
	 * Returns the time when the command was submitted in nanoseconds or null if it has not been 
	 * submitted yet.
	 * 
	 * @return The time when the command was submitted in nanoseconds or null.
	 */
	public Long getSubmissionTime() {
		return submissionTime;
	}
	/**
	 * Returns the time when the command was processed in nanoseconds or null if it has not been 
	 * processed yet.
	 * 
	 * @return The time when the command was processed in nanoseconds or null.
	 */
	public Long getProcessedTime() {
		return processedTime;
	}
	/**
	 * Returns whether the command has already been processed.
	 * 
	 * @return Whether the command has already been processed.
	 */
	public boolean isProcessed() {
		return processed;
	}
	/**
	 * Returns the {@link #Future} instance associated with the submission or null if it has not been
	 * submitted yet.
	 * 
	 * @return The {@link #Future} instance associated with the command or null.
	 */
	Future<?> getFuture() {
		return future;
	}
	/**
	 * Sets the {@link #Future} instance associated with the submission and the submission time. If the 
	 * {@link #Future} instance is not null, subsequent calls are ignored.
	 * 
	 * @param future The {@link #Future} instance associated with the submission.
	 */
	synchronized void setFuture(Future<?> future) {
		if (this.future == null) {
			this.future = future;
			submissionTime = System.nanoTime();
		}
	}
	/**
	 * Sets the command submission's state to 'processed'. Subsequent calls are ignored.
	 */
	synchronized void setProcessedToTrue() {
		if (!processed) {
			processed = true;
			processedTime = System.nanoTime();
		}
	}
	
}