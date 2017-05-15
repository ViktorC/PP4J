package net.viktorc.pspp;

/**
 * A class for holding an instruction to write to a process' standard in and a listener that is responsible for validating 
 * the processing of the instruction.
 * 
 * @author A6714
 *
 */
public class Command {

	private final String instruction;
	private final CommandListener listener;
	
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param instruction The instruction to write to the process' standard in.
	 * @param listener An instance of {@link net.viktorc.pspp.CommandListener} for consuming the subsequent outputs of 
	 * the process and for determining whether the process has finished processing the command and is ready for new commands 
	 * based on these outputs. If it is null, the process manager will assume that process is instantly ready to accept new 
	 * commands.
	 * @throws IllegalArgumentException If the instruction is null.
	 */
	public Command(String instruction, CommandListener listener) {
		if (instruction == null)
			throw new IllegalArgumentException("The command cannot be null.");
		this.instruction = instruction;
		this.listener = listener;
	}
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param instruction The instruction to write to the process' standard in.
	 * @throws IllegalArgumentException If the instruction is null.
	 */
	public Command(String instruction) {
		this(instruction, null);
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
	 * Returns the {@link net.viktorc.pspp.CommandListener} instance for consuming the subsequent outputs of the process 
	 * and for determining whether the process has finished processing the command and is ready for new commands based on 
	 * these outputs. If it is null, no output is expected and the process should instantly be ready to execute new commands.
	 * 
	 * @return The {@link net.viktorc.pspp.CommandListener} instance associated with the command.
	 */
	public CommandListener getListener() {
		return listener;
	}
	
}
