package net.viktorc.pspp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * A class that holds all information necessary for executing and processing commands in {@link net.viktorc.pspp.ProcessManager} instances.
 * 
 * @author A6714
 *
 */
public class CommandSubmission {
	
	private final List<Command> commands;
	private final boolean terminateProcessAfterwards;
	private final Queue<SubmissionListener> submissionListeners;
	private final long receivedTime;
	private Long submittedTime;
	private Long processedTime;
	private boolean processed;
	private volatile boolean cancel;
	private volatile Future<?> future;
	
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param commands A list of commands to execute. It should not contain null references.
	 * @param terminateProcessAfterwards Whether the process should be terminated after the execution of the commands.
	 * @throws IllegalArgumentException If the commands are null or empty or contain at least one null reference.
	 */
	public CommandSubmission(List<Command> commands, boolean terminateProcessAfterwards) {
		if (commands == null || commands.isEmpty())
			throw new IllegalArgumentException("The commands cannot be null.");
		if (commands.isEmpty())
			throw new IllegalArgumentException("The commands cannot be empty.");
		if (!commands.stream().filter(c -> c == null).collect(Collectors.toList()).isEmpty())
			throw new IllegalArgumentException("The commands cannot include null references.");
		this.commands = new ArrayList<>(commands);
		this.terminateProcessAfterwards = terminateProcessAfterwards;
		submissionListeners = new ConcurrentLinkedQueue<>();
		receivedTime = System.nanoTime();
	}
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param command A command to execute.
	 * @param terminateProcessAfterwards Whether the process should be terminated after the execution of the command.
	 * @throws IllegalArgumentException If the command is null.
	 */
	public CommandSubmission(Command command, boolean terminateProcessAfterwards) {
		this(Arrays.asList(command), terminateProcessAfterwards);
	}
	/**
	 * Returns the commands to execute.
	 * 
	 * @return The commands to execute.
	 */
	public List<Command> getCommands() {
		return new ArrayList<>(commands);
	}
	/**
	 * Returns whether the process should be terminated after the execution of the command.
	 * 
	 * @return Whether the process should be terminated after the execution of the command.
	 */
	public boolean doTerminateProcessAfterwards() {
		return terminateProcessAfterwards;
	}
	/**
	 * Subscribes a listener to the submission instance. Listeners added to the submission after it 
	 * has already been submitted have no effect.
	 * 
	 * @param listener The listener to subscribe to the submission.
	 */
	public void addSubmissionListener(SubmissionListener listener) {
		submissionListeners.add(listener);
	}
	/**
	 * Removes the specified listener from the list of subscribed listeners if found. The value returned 
	 * denotes whether the listener was subscribed. Removing listeners after the submission has already 
	 * been submitted has no effect.
	 * 
	 * @param listener The listener to remove.
	 * @return Whether the listener was subscribed and thus removed.
	 */
	public boolean removeSubmissionListener(SubmissionListener listener) {
		return submissionListeners.remove(listener);
	}
	/**
	 * Returns a list of the subscribed submission listeners.
	 * 
	 * @return A list of the listeners subscribed to the submission instance.
	 */
	List<SubmissionListener> getSubmissionListeners() {
		return new ArrayList<>(submissionListeners);
	}
	/**
	 * Prompts the {@link net.viktorc.pspp.ProcessManager} handling the submission to ignore the commands in the submission that 
	 * have not yet been written to the standard in of the process. Canceling the submission does not affect the command 
	 * currently being processed.
	 */
	void cancel() {
		cancel = true;
	}
	/**
	 * Returns whether the submission has been cancelled.
	 * 
	 * @return Whether the submission has been cancelled.
	 */
	boolean isCancelled() {
		return cancel;
	}
	/**
	 * Returns the time when the instance was constructed in nanoseconds.
	 * 
	 * @return The time when the instance was constructed in nanoseconds.
	 */
	long getReceivedTime() {
		return receivedTime;
	}
	/**
	 * Returns the time when the command was submitted in nanoseconds or null if it has not been 
	 * submitted yet.
	 * 
	 * @return The time when the command was submitted in nanoseconds or null.
	 */
	Long getSubmittedTime() {
		return submittedTime;
	}
	/**
	 * Returns the time when the command was processed in nanoseconds or null if it has not been 
	 * processed yet.
	 * 
	 * @return The time when the command was processed in nanoseconds or null.
	 */
	Long getProcessedTime() {
		return processedTime;
	}
	/**
	 * Returns whether the command has already been processed.
	 * 
	 * @return Whether the command has already been processed.
	 */
	boolean isProcessed() {
		return processed;
	}
	/**
	 * Returns the {@link java.util.concurrent.Future} instance associated with the submission or null if it has not been
	 * submitted yet.
	 * 
	 * @return The {@link java.util.concurrent.Future} instance associated with the command or null.
	 */
	Future<?> getFuture() {
		return future;
	}
	/**
	 * Sets the {@link java.util.concurrent.Future} instance associated with the submission and the submission time. If the 
	 * {@link java.util.concurrent.Future} instance is not null, subsequent calls are ignored.
	 * 
	 * @param future The {@link java.util.concurrent.Future} instance associated with the submission.
	 */
	void setFuture(Future<?> future) {
		if (this.future == null) {
			submittedTime = System.nanoTime();
			this.future = future;
			synchronized (this) {
				notifyAll();
			}
		}
	}
	/**
	 * Sets the command submission's state to 'processed'. Subsequent calls are ignored.
	 */
	void setProcessedToTrue() {
		if (!processed) {
			processedTime = System.nanoTime();
			processed = true;
			synchronized (this) {
				notifyAll();
			}
		}
	}
	@Override
	public String toString() {
		return String.join("; ", commands.stream().map(c -> c.getInstruction()).collect(Collectors.toList()));
	}
	
}