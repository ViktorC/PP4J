package net.viktorc.pp4j;

import java.util.List;

/**
 * An interface that defines methods necessary for the submission and execution commands in {@link net.viktorc.pp4j.ProcessExecutor} 
 * instances. It also defines methods to call once the processing of the submitted commands has started or finished which are by 
 * default no-operations.
 * 
 * @author Viktor Csomor
 *
 */
public interface Submission {

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
	 * Returns whether the submission has been cancelled.
	 * 
	 * @return Whether the submission has been cancelled.
	 */
	boolean isCancelled();
	/**
	 * A method that is executed once the processing of the submitted commands has begun.
	 */
	default void onStartedProcessing() {
		
	}
	/**
	 * A method to execute once the processing of the submitted commands has completed.
	 */
	default void onFinishedProcessing() {
		
	}
	
}