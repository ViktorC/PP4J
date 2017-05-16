package net.viktorc.pspp;

/**
 * A listener that allows for the processing of the outputs of a process. It is temporarily subscribed to a 
 * {@link net.viktorc.pspp.ProcessManager} instance when the {@link net.viktorc.pspp.ProcessManager#execute(CommandSubmission) executeCommand} 
 * method is called. It listens to the standard out and error out streams of the process and the respective method 
 * is invoked every time a new line is printed to one of these streams. Besides possible processing activities, 
 * these methods are also responsible for determining when the process finished processing the command. E.g. if a 
 * process takes the command "go" which triggers the execution of long-running task, and it prints "ready" to the 
 * standard out stream once the task is completed; the methods should only return true if the output "ready" has 
 * been written to standard out, in any other case, they should return false. The interface also defines a method 
 * that is called once the command has been written to the process' standard in and a method called once the command 
 * has been processed and all the related post-processing activities has been completed.
 * 
 * @author A6714
 *
 */
public interface OutputListener {

	/**
	 * A method called every time a new line is printed to the standard out stream of the process after a command 
	 * has been sent to it and it has not finished processing it up until now.
	 * 
	 * @param standardOutput The new line of output printed to the standard out of the process.
	 * @return Whether this line of output denotes that the process has finished processing the previously sent 
	 * command. The {@link net.viktorc.pspp.ProcessManager} instance will not accept new commands until the 
	 * processing of the command is completed.
	 */
	boolean onNewStandardOutput(String standardOutput);
	/**
	 * A method called every time a new line is printed to the error out stream of the process after a command 
	 * has been sent to it and it has not finished processing it up until now.
	 * 
	 * @param errorOutput The new line of output printed to the error out of the process.
	 * @return Whether this line of output denotes that the process has finished processing the previously sent 
	 * command. The {@link net.viktorc.pspp.ProcessManager} instance will not accept new commands until the 
	 * processing of the command is completed.
	 */
	boolean onNewErrorOutput(String errorOutput);
	
}
