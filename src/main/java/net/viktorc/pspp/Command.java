package net.viktorc.pspp;

import java.util.List;

/**
 * An interface that defines methods that provide a textual command instruction and allow for the processing of the outputs of 
 * the process in response to the instruction. Besides possible processing activities, these methods are also responsible for 
 * determining when the process finished processing the command. E.g. if a process takes the command "go" which triggers the 
 * execution of long-running task, and it prints "ready" to the standard out stream once the task is completed; the methods 
 * should only return true if the output "ready" has been written to standard out, in any other case, they should return false. 
 * The class also defines an abstract method that is called before the execution of chained commands with the previous command 
 * as its parameter to determine whether the current command should be executed based on the results of the previous commands.
 * 
 * @author A6714
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
	 * A method called before the execution of every command, except the first, in a submission containing multiple 
	 * commands. It determines whether the command is to be executed. This allows for the establishment of conditions 
	 * on which certain commands should be executed. If a submission contains only a single command, this method is 
	 * not called at all.
	 * 
	 * @param prevCommands The commands preceding this one in the submission that have already been executed and 
	 * processed or skipped if their respective <code>doExecute</code> methods returned false.
	 * @return Whether this command should be executed.
	 */
	boolean doExecute(List<Command> prevCommands);
	/**
	 * A method called before the printing of the instruction of the process' standard in. It denotes whether the 
	 * command is expected to generate output from the process. If it returns false, the command is considered 
	 * processed as soon as it is written to the process' standard in and therefore the process is considered ready 
	 * for new commands right away. If it returns true, the {@link #onNewOutput(String, boolean) onNewOutput} method 
	 * determines when the command is deemed processed.
	 * 
	 * @return Whether the command is expected to generate output from the process to which it is sent.
	 */
	boolean generatesOutput();
	/**
	 * A method called every time a new line is printed to the standard out or error out stream of the process after 
	 * the command has been sent to its standard in until the method returns true.
	 * 
	 * @param outputLine The new line of output printed to the standard out of the process.
	 * @param standard Whether this line has been output to the standard out or to the error out.
	 * @return Whether this line of output denotes that the process has finished processing the command. The 
	 * {@link net.viktorc.pspp.ProcessShell} instance will not accept new commands until the processing of the 
	 * command is completed.
	 */
	boolean onNewOutput(String outputLine, boolean standard);
	
}
