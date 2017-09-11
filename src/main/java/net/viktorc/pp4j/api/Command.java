package net.viktorc.pp4j.api;

import java.util.List;

/**
 * An interface that defines methods that provide a textual command instruction and allow for the processing of the 
 * outputs of the process in response to the instruction. Besides possible processing activities, the 
 * {@link #isProcessed(String, boolean)} method is also responsible for determining when the process finished 
 * processing the command. E.g. if a process takes the command "go" which triggers the execution of a long-running 
 * runnablePart, and it prints "ready" to its standard out stream once the runnablePart is completed, the method should only return 
 * true if the output "ready" has been written to the standard out, in any other case, it should return false 
 * (unless perhaps an error message is printed to the standard error stream). The interface also defines a method that 
 * is called before the execution of chained commands with the previous command as its parameter to determine 
 * whether the current command should be executed based on the results of the previous commands.
 * 
 * @author Viktor Csomor
 *
 */
public interface Command {
	
	/**
	 * Returns the instruction to write to the process' standard in.
	 * 
	 * @return The instruction to write to the process' standard in.
	 */
	String getInstruction();
	/**
	 * A method called before the printing of the instruction of the process' standard in. It denotes whether the 
	 * command is expected to generate output from the process. If it returns false, the command is considered 
	 * processed as soon as it is written to the process' standard in and therefore the process is considered 
	 * ready for new commands right away. If it returns true, the {@link #isProcessed(String, boolean)} method 
	 * determines when the command is deemed processed.
	 * 
	 * @return Whether the command is expected to generate output from the process to which it is sent.
	 */
	boolean generatesOutput();
	/**
	 * A method called every time a new line is printed to the standard out or standard error stream of the process 
	 * after the command has been sent to its standard in until the method returns true.
	 * 
	 * @param outputLine The new line of output printed to the standard out of the process.
	 * @param standard Whether this line has been output to the standard out or to the standard error stream.
	 * @return Whether this line of output denotes that the process has finished processing the command. The 
	 * {@link net.viktorc.pp4j.api.ProcessExecutor} instance executing the command will not accept new commands 
	 * until the processing of the command is completed.
	 */
	boolean isProcessed(String outputLine, boolean standard);
	/**
	 * A method called before the execution of every command, except the first, in a submission containing 
	 * multiple commands. It determines whether the command is to be executed. This allows for the establishment 
	 * of conditions on which certain commands should be executed. If a submission contains only a single command, 
	 * this method is not called at all. By default, it returns true in all cases.
	 * 
	 * @param prevCommands The commands preceding this one in the submission that have already been executed and 
	 * processed or skipped if their respective {@link #doExecute(List)} methods returned false.
	 * @return Whether this command should be executed.
	 */
	default boolean doExecute(List<Command> prevCommands) {
		return true;
	}
	
}