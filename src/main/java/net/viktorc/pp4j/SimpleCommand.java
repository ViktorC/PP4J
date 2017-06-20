package net.viktorc.pp4j;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * A simple sub-class of the {@link net.viktorc.pp4j.AbstractCommand} abstract class that relies on lambda functions to 
 * implement the {@link net.viktorc.pp4j.AbstractCommand#onOutput(String, boolean) onOutput} method and assumes that the 
 * command should always be executed and that the process generates an output in response to the command.
 * 
 * @author Viktor Csomor
 *
 */
public class SimpleCommand extends AbstractCommand {

	private final BiPredicate<SimpleCommand,String> onStandardOutput;
	private final BiPredicate<SimpleCommand,String> onErrorOutput;
	
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param instruction The instruction to write to the process' standard in.
	 * @param onStandardOutput The predicate that allows for the processing of the process' standard output in response to 
	 * the command and determines when the command is to be considered processed by returning true.
	 * @param onErrorOutput The predicate that allows for the processing of the process' error output in response to the 
	 * command and determines when the command is to be considered processed by returning true.
	 */
	public SimpleCommand(String instruction, BiPredicate<SimpleCommand,String> onStandardOutput,
			BiPredicate<SimpleCommand,String> onErrorOutput) {
		super(instruction);
		this.onStandardOutput = onStandardOutput;
		this.onErrorOutput = onErrorOutput;
	}
	@Override
	public boolean doExecute(List<Command> prevCommands) {
		return true;
	}
	@Override
	public boolean generatesOutput() {
		return true;
	}
	@Override
	protected boolean onOutput(String outputLine, boolean standard) {
		return (standard ? onStandardOutput.test(this, outputLine) : onErrorOutput.test(this, outputLine));
	}

}