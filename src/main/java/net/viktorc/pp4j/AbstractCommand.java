package net.viktorc.pp4j;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract implementation of the {@link net.viktorc.pp4j.Command} interface that stores all lines output to the process' standard 
 * out and error out in response to the command.
 * 
 * @author Viktor Csomor
 *
 */
public abstract class AbstractCommand implements Command {

	private final String instruction;
	private final List<String> stdOutLines;
	private final List<String> errOutLines;
	
	/**
	 * Constructs an instance holding the specified instruction.
	 * 
	 * @param instruction The instruction to send to the process' standard in as the command.
	 */
	protected AbstractCommand(String instruction) {
		this.instruction = instruction;
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
	 * {@link net.viktorc.pp4j.Command} instance is reused.
	 */
	public void reset() {
		stdOutLines.clear();
		errOutLines.clear();
	}
	@Override
	public String getInstruction() {
		return instruction;
	}
	@Override
	public final boolean onNewOutput(String outputLine, boolean standard) {
		if (standard)
			stdOutLines.add(outputLine);
		else
			errOutLines.add(outputLine);
		return onOutput(outputLine, standard);
	}
	/**
	 * It stores the output line before calling and returning the result of {@link #onNewOutput(String, boolean)}.
	 * 
	 * @param outputLine The new line of output printed to the standard out of the process.
	 * @param standard Whether this line has been output to the standard out or to the error out.
	 * @return Whether this line of output denotes that the process has finished processing the command.
	 */
	protected abstract boolean onOutput(String outputLine, boolean standard);

}