package net.viktorc.pp4j.api;

/**
 * An interface that defines an executor that encapsulates a process and serves as a handle for having the process execute submissions.
 * 
 * @author Viktor Csomor
 *
 */
public interface ProcessExecutor {

	/**
	 * Sequentially writes the specified commands to the process and blocks until they are processed.
	 * 
	 * @param submission The submission to execute.
	 * @return Whether the submission was successfully executed. If the executor is not running or is busy processing an other 
	 * submission, it immediately returns false; otherwise the submission is executed and true is returned once it's successfully 
	 * processed, or false is returned if the submission could not be processed.
	 * @throws Exception If an unexpected error occurs.
	 */
	boolean execute(Submission submission) throws Exception;

}