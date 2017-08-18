package net.viktorc.pp4j.api;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * An interface that defines methods necessary for the submission and execution commands in 
 * {@link net.viktorc.pp4j.api.ProcessExecutor} instances. It also defines methods to call once the processing 
 * of the submitted commands has started or finished which are by default no-operations.
 * 
 * @author Viktor Csomor
 *
 * @param <T> The return type associated with the submission.
 */
public interface Submission<T> {

	/**
	 * Returns the commands to execute.
	 * 
	 * @return The commands to execute.
	 */
	List<Command> getCommands();
	/**
	 * Returns whether the process should be terminated after the execution of the commands.
	 * 
	 * @return Whether the process should be terminated after the execution of the commands.
	 */
	boolean doTerminateProcessAfterwards();
	/**
	 * Returns the result of the submission. By default, it returns null.
	 * 
	 * @return The object representing the result of the submission or null if no result is associated with 
	 * the submission.
	 * @throws ExecutionException if an error occurred while executing the submission.
	 */
	default T getResult() throws ExecutionException { return null; }
	/**
	 * A method that is executed once the processing of the submitted commands has begun.
	 */
	default void onStartedProcessing() { }
	/**
	 * A method to execute once the processing of the submitted commands has completed.
	 */
	default void onFinishedProcessing() { }
	
}