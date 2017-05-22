package net.viktorc.pspp;

import java.util.List;

/**
 * A abstract class holds an instruction to write to a process' standard in and defines methods that allow for the processing 
 * of the outputs of the process in response to the instruction. Besides possible processing activities, these methods are also 
 * responsible for determining when the process finished processing the command. E.g. if a process takes the command "go" which 
 * triggers the execution of long-running task, and it prints "ready" to the standard out stream once the task is completed; the 
 * methods should only return true if the output "ready" has been written to standard out, in any other case, they should return 
 * false. The class also defines an abstract method that is called before the execution of chained commands with the previous 
 * command as its parameter to determine whether the current command should be executed based on the results of the previous 
 * commands.
 * 
 * @author A6714
 *
 */
public abstract class Command {

	private final String instruction;
	
	/**
	 * Constructs an instance according to the specified parameters. The {@link net.viktorc.pspp.ProcessManager} instance 
	 * the command is executed on will assume that the command should induce no response from the underlying process and 
	 * that the process is instantly ready for new commands.
	 * 
	 * @param instruction The instruction to write to the process' standard in.
	 * @throws IllegalArgumentException If the instruction is null.
	 */
	public Command(String instruction) {
		if (instruction == null)
			throw new IllegalArgumentException("The command cannot be null.");
		this.instruction = instruction;
	}
	/**
	 * Returns the instruction to write to the process' standard in.
	 * 
	 * @return The instruction to write to the process' standard in.
	 */
	public String getInstruction() {
		return instruction;
	}
	/**
	 * A method called before the printing of the instruction of the process' standard in. It denotes whether the 
	 * command is expected to generate output from the process. If it returns false, the command is considered 
	 * processed as soon as it is written to the process' standard in and therefore the process is considered ready 
	 * for new commands right away. If it returns true, the {@link #onStandardOutput(String) onStandardOutput} and 
	 * {@link #onErrorOutput(String) onErrorOutput} methods determine when the command is deemed processed.
	 * 
	 * @return Whether the command is expected to generate output from the process to which it is sent.
	 */
	protected abstract boolean generatesOutput();
	/**
	 * A method called every time a new line is printed to the standard out stream of the process after the command 
	 * has been sent to its standard in until the method returns true.
	 * 
	 * @param standardOutputLine The new line of output printed to the standard out of the process.
	 * @return Whether this line of output denotes that the process has finished processing the command. The 
	 * {@link net.viktorc.pspp.ProcessManager} instance will not accept new commands until the processing of the 
	 * command is completed.
	 */
	protected abstract boolean onStandardOutput(String standardOutputLine);
	/**
	 * A method called every time a new line is printed to the error out stream of the process after the command 
	 * has been sent to it and it has not finished processing it up until now.
	 * 
	 * @param errorOutputLine The new line of output printed to the error out of the process.
	 * @return Whether this line of output denotes that the process has finished processing the command. The 
	 * {@link net.viktorc.pspp.ProcessManager} instance will not accept new commands until the processing of the 
	 * command is completed.
	 */
	protected abstract boolean onErrorOutput(String errorOutputLine);
	/**
	 * A method called before the execution of every command, except the first, in a submission containing multiple 
	 * commands. It determines whether the method is to be executed. This allows for the establishment of conditions 
	 * on which certain commands should be executed. If a submission contains only a single command, this method is 
	 * not called at all.
	 * 
	 * @param prevCommands The commands preceding this one in the submission that have already been executed and 
	 * processed.
	 * @return Whether this command should be executed.
	 */
	protected abstract boolean doExecute(List<Command> prevCommands);
	
}
