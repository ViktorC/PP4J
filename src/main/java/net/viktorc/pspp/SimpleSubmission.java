package net.viktorc.pspp;

import java.util.Arrays;
import java.util.List;

/**
 * A simple non-cancellable sub-class of the {@link net.viktorc.pspp.AbstractSubmission} abstract class.
 * 
 * @author A6714
 *
 */
public class SimpleSubmission extends AbstractSubmission {
	
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param commands A list of commands to execute. It should not contain null references.
	 * @param terminateProcessAfterwards Whether the process should be terminated after the execution of the commands.
	 * @throws IllegalArgumentException If the commands are null or empty or contain at least one null reference.
	 */
	public SimpleSubmission(List<Command> commands, boolean terminateProcessAfterwards) {
		super(commands, terminateProcessAfterwards);
	}
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param command A command to execute.
	 * @param terminateProcessAfterwards Whether the process should be terminated after the execution of the command.
	 * @throws IllegalArgumentException If the command is null.
	 */
	public SimpleSubmission(Command command, boolean terminateProcessAfterwards) {
		this(Arrays.asList(command), terminateProcessAfterwards);
	}
	@Override
	public boolean isCancelled() {
		return false;
	}

}
