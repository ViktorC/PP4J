package net.viktorc.pspp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A simple implementation of the {@link net.viktorc.pspp.CommandListener} interface that stores every line output to 
 * the standard out and error out of the process in lists of strings.
 * 
 * @author A6714
 *
 */
public class SimpleCommandListener implements CommandListener {

	private final List<String> stdOutLines;
	private final List<String> errOutLines;
	private final Predicate<String> stdPred;
	private final Predicate<String> errPred;
	
	/**
	 * Constructs a simple command listener. The parameters are used to implement the
	 * {@link net.viktorc.pspp.CommandListener#onNewStandardOutput(String) onNewStandardOutput} and 
	 * {@link net.viktorc.pspp.CommandListener#onNewErrorOutput(String) onNewErrorOutput} methods of the 
	 * {@link net.viktorc.pspp.CommandListener} interface.
	 * 
	 * @param stdPred A {@link java.util.function.Predicate} to determine if the {@link net.viktorc.pspp.Command} 
	 * has been processed based on the standard output of the underlying process.
	 * @param errPred A {@link java.util.function.Predicate} to determine if the {@link net.viktorc.pspp.Command} 
	 * has been processed based on the error output of the underlying process.
	 * @throws IllegalArgumentException If either of the predicates is null.
	 */
	public SimpleCommandListener(Predicate<String> stdPred, Predicate<String> errPred) {
		if (stdPred == null || errPred == null)
			throw new IllegalArgumentException("The predicates cannot be null");
		this.stdOutLines = new ArrayList<>();
		this.errOutLines = new ArrayList<>();
		this.stdPred = stdPred;
		this.errPred = errPred;
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
	 * {@link net.viktorc.pspp.SimpleCommandListener} instance is reused.
	 */
	public void reset() {
		stdOutLines.clear();
		errOutLines.clear();
	}
	@Override
	public final boolean onNewStandardOutput(String standardOutput) {
		stdOutLines.add(standardOutput);
		return stdPred.test(standardOutput);
	}
	@Override
	public final boolean onNewErrorOutput(String errorOutput) {
		errOutLines.add(errorOutput);
		return errPred.test(errorOutput);
	}

}