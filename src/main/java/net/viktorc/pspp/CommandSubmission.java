package net.viktorc.pspp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
	private final SubmissionListener submissionListener;
	private final boolean terminateProcessAfterwards;
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
	 * @param submissionListener A listener for the submission. If it is null, it is simply ignored.
	 * @param terminateProcessAfterwards Whether the process should be terminated after the execution of the commands.
	 * @throws IllegalArgumentException If the commands are null or empty or contain at least one null reference.
	 */
	public CommandSubmission(List<Command> commands, SubmissionListener submissionListener, boolean terminateProcessAfterwards) {
		if (commands == null || commands.isEmpty())
			throw new IllegalArgumentException("The commands cannot be null.");
		if (commands.isEmpty())
			throw new IllegalArgumentException("The commands cannot be empty.");
		if (!commands.stream().filter(c -> c == null).collect(Collectors.toList()).isEmpty())
			throw new IllegalArgumentException("The commands cannot include null references.");
		this.commands = commands;
		this.submissionListener = submissionListener;
		this.terminateProcessAfterwards = terminateProcessAfterwards;
		receivedTime = System.nanoTime();
	}
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param commands A list of commands to execute. It should not contain null references.
	 * @param terminateProcessAfterwards Whether the process should be terminated after the execution of the commands.
	 * @throws IllegalArgumentException If the commands are null or empty or contain at least one null reference.
	 */
	public CommandSubmission(List<Command> commands, boolean terminateProcessAfterwards) {
		this(commands, null, terminateProcessAfterwards);
	}
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param command A command to execute.
	 * @param submissionListener A listener for the submission. If it is null, it is simply ignored.
	 * @param terminateProcessAfterwards Whether the process should be terminated after the execution of the command.
	 * @throws IllegalArgumentException If the command is null.
	 */
	public CommandSubmission(Command command, SubmissionListener submissionListener, boolean terminateProcessAfterwards) {
		this(Arrays.asList(command), submissionListener, terminateProcessAfterwards);
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
	 * Returns the submission listener associated with the instance or null if there is none.
	 * 
	 * @return The submission listener associated with the instance or null if there is none.
	 */
	public SubmissionListener getSubmissionListener() {
		return submissionListener;
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
	 * Prompts the {@link net.viktorc.pspp.ProcessManager} handling the submission to ignore the commands in the submission that 
	 * have not yet been written to the standard in of the process. Canceling the submission does not affect the command 
	 * currently being processed.
	 */
	public void cancel() {
		cancel = true;
	}
	/**
	 * Returns whether the submission has been cancelled.
	 * 
	 * @return Whether the submission has been cancelled.
	 */
	public boolean isCancelled() {
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