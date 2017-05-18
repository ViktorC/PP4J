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
	private final OutputListener listener;
	private volatile boolean skip;
	
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param instruction The instruction to write to the process' standard in.
	 * @param listener An instance of {@link net.viktorc.pspp.OutputListener} for consuming the subsequent outputs of 
	 * the process and for determining whether the process has finished processing the command and is ready for new commands 
	 * based on these outputs. If it is null, the process manager will assume that process is instantly ready to accept new 
	 * commands.
	 * @throws IllegalArgumentException If the instruction is null.
	 */
	public Command(String instruction, OutputListener listener) {
		if (instruction == null)
			throw new IllegalArgumentException("The command cannot be null.");
		this.instruction = instruction;
		this.listener = listener;
	}
	/**
	 * Constructs an instance according to the specified parameters. The {@link net.viktorc.pspp.ProcessManager} instance 
	 * the command is executed on will assume that the command should induce no response from the underlying process and 
	 * that the process is instantly ready for new commands.
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
	 * Returns the {@link net.viktorc.pspp.OutputListener} instance for consuming the subsequent outputs of the process 
	 * and for determining whether the process has finished processing the command and is ready for new commands based on 
	 * these outputs. If it is null, no output is expected and the process should instantly be ready to execute new commands.
	 * 
	 * @return The {@link net.viktorc.pspp.OutputListener} instance associated with the command.
	 */
	public OutputListener getListener() {
		return listener;
	}
	/**
	 * Returns whether the execution of the command should be skipped.
	 * 
	 * @return Whether the execution of the command should be skipped.
	 */
	public boolean doSkip() {
		return skip;
	}
	/**
	 * Sets whether the execution of the command should be skipped. If set to true, the command will not be executed, even if 
	 * it has already been submitted, as long as the instruction has not been sent to a process. If the instruction has already 
	 * been written to the standard in of a process and the command is currently being executed, calling this method has no 
	 * effect.
	 * 
	 * @param skip Whether the execution of the command should be skipped.
	 */
	public void setSkip(boolean skip) {
		this.skip = skip;
	}
	
}
