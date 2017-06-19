package net.viktorc.ppe4j;

import java.util.Arrays;
import java.util.List;

/**
 * A simple sub-class of the {@link net.viktorc.ppe4j.AbstractSubmission} abstract class. It does not provide a mechanism 
 * for directly canceling submissions; however, they can still be cancelled by canceling the {@link java.util.concurrent.Future} 
 * instance returned by the {@link net.viktorc.ppe4j.ProcessPoolExecutor#submit(Submission) submit} method of the implementations 
 * of the {@link net.viktorc.ppe4j.ProcessPoolExecutor} interface.
 * 
 * @author Viktor Csomor
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