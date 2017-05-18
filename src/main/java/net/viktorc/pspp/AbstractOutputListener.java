package net.viktorc.pspp;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple abstract implementation of the {@link net.viktorc.pspp.OutputListener} interface that stores every line output to 
 * the standard out and error out of the process in lists of strings.
 * 
 * @author A6714
 *
 */
public abstract class AbstractOutputListener implements OutputListener {

	private final List<String> stdOutLines;
	private final List<String> errOutLines;
	
	/**
	 * Constructs a simple abstract command listener.
	 */
	public AbstractOutputListener() {
		this.stdOutLines = new ArrayList<>();
		this.errOutLines = new ArrayList<>();
	}
	/**
	 * Returns a list of lines output to the standard out of the underlying process after the instruction of the 
	 * associated {@link net.viktorc.pspp.Command} instance is written to the standard in.
	 * 
	 * @return A list of lines output to the standard out of the underlying process.
	 */
	public List<String> getStandardOutLines() {
		return new ArrayList<>(stdOutLines);
	}
	/**
	 * Returns a list of lines output to the error out of the underlying process after the instruction of the 
	 * associated {@link net.viktorc.pspp.Command} instance is written to the standard in.
	 * 
	 * @return A list of lines output to the error out of the underlying process.
	 */
	public List<String> getErrorOutLines() {
		return new ArrayList<>(errOutLines);
	}
	/**
	 * Returns a string of the lines output to the standard out of the underlying process after the instruction of 
	 * the associated {@link net.viktorc.pspp.Command} instance is written to the standard in.
	 * 
	 * @return A string of the lines output to the standard out of the underlying process
	 */
	public String getJointStandardOutLines() {
		return String.join("\n", stdOutLines);
	}
	/**
	 * Returns a string of the lines output to the error out of the underlying process after the instruction of 
	 * the associated {@link net.viktorc.pspp.Command} instance is written to the standard in.
	 * 
	 * @return A string of the lines output to the error out of the underlying process
	 */
	public String getJointErrorOutLines() {
		return String.join("\n", errOutLines);
	}
	/**
	 * Clears the lists holding the lines output to the out streams of the underlying process. Recommended in case a 
	 * {@link net.viktorc.pspp.SimpleOutputListener} instance is reused.
	 */
	public void reset() {
		stdOutLines.clear();
		errOutLines.clear();
	}
	/**
	 * See {@link net.viktorc.pspp.OutputListener#onStandardOutput(Command, String)}.
	 */
	public abstract boolean onNewStandardOutput(Command command, String newStandardOutputLine);
	/**
	 * See {@link net.viktorc.pspp.OutputListener#onErrorOutput(Command, String)}.
	 */
	public abstract boolean onNewErrorOutput(Command command, String newErrorOutputLine);
	@Override
	public final boolean onStandardOutput(Command command, String standardOutput) {
		stdOutLines.add(standardOutput);
		return onNewStandardOutput(command, standardOutput);
	}
	@Override
	public final boolean onErrorOutput(Command command, String errorOutput) {
		errOutLines.add(errorOutput);
		return onNewErrorOutput(command, errorOutput);
	}
	
}
