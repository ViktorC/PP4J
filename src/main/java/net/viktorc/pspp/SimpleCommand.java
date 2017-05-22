package net.viktorc.pspp;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple, abstract implementation of the abstract class {@link net.viktorc.pspp.Command} that assumes that the command generates 
 * output and that it should always be executed. Moreover, it also stores all lines output to the process' standard out and error 
 * out in response to the command.
 * 
 * @author A6714
 *
 */
public abstract class SimpleCommand extends Command {

	private final List<String> stdOutLines;
	private final List<String> errOutLines;
	
	/**
	 * Constructs an instance holding the specified instruction.
	 * 
	 * @param instruction The instruction to send to the process' standard in as the command.
	 */
	public SimpleCommand(String instruction) {
		super(instruction);
		stdOutLines = new ArrayList<>();
		errOutLines = new ArrayList<>();
	}
	/**
	 * Returns a list of lines output to the standard out of the underlying process after the instruction has been written to the 
	 * standard in of the process.
	 * 
	 * @return A list of lines output to the standard out of the underlying process.
	 */
	public List<String> getStandardOutLines() {
		return new ArrayList<>(stdOutLines);
	}
	/**
	 * Returns a list of lines output to the error out of the underlying process after the instruction has been written to the 
	 * standard in of the process.
	 * 
	 * @return A list of lines output to the standard out of the underlying process.
	 */
	public List<String> getErrorOutLines() {
		return new ArrayList<>(errOutLines);
	}
	/**
	 * Returns a string of the lines output to the standard out of the underlying process after the instruction has been written to 
	 * the standard in of the process.
	 * 
	 * @return A string of the lines output to the standard out of the underlying process.
	 */
	public String getJointStandardOutLines() {
		return String.join("\n", stdOutLines);
	}
	/**
	 * Returns a string of the lines output to the error out of the underlying process after the instruction has been written to 
	 * the standard in of the process.
	 * 
	 * @return A string of the lines output to the error out of the underlying process.
	 */
	public String getJointErrorOutLines() {
		return String.join("\n", errOutLines);
	}
	/**
	 * Clears the lists holding the lines output to the out streams of the underlying process. Recommended in case the 
	 * {@link net.viktorc.pspp.Command} instance is reused.
	 */
	public void reset() {
		stdOutLines.clear();
		errOutLines.clear();
	}
	@Override
	protected boolean generatesOutput() {
		return true;
	}
	@Override
	protected final boolean onStandardOutput(String standardOutputLine) {
		stdOutLines.add(standardOutputLine);
		return onNewStandardOutput(standardOutputLine);
	}
	@Override
	protected final boolean onErrorOutput(String errorOutputLine) {
		errOutLines.add(errorOutputLine);
		return onNewErrorOutput(errorOutputLine);
	}
	@Override
	protected boolean doExecute(List<Command> prevCommands) {
		return true;
	}
	/**
	 * It stores the output line before calling and returning the result of {@link #onStandardOutput(String)}.
	 * 
	 * @param standardOutputLine The new line of output printed to the standard out of the process.
	 * @return Whether this line of output denotes that the process has finished processing the command. The 
	 * {@link net.viktorc.pspp.ProcessManager} instance will not accept new commands until the processing of the 
	 * command is completed.
	 */
	protected abstract boolean onNewStandardOutput(String standardOutputLine);
	/**
	 * It stores the output line before calling and returning the result of {@link #onErrorOutput(String)}.
	 * 
	 * @param errorOutputLine The new line of output printed to the error out of the process.
	 * @return Whether this line of output denotes that the process has finished processing the command. The 
	 * {@link net.viktorc.pspp.ProcessManager} instance will not accept new commands until the processing of the 
	 * command is completed.
	 */
	protected abstract boolean onNewErrorOutput(String errorOutputLine);

}
