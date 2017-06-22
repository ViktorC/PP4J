package net.viktorc.pp4j;

import java.io.IOException;

/**
 * An abstract implementation of the {@link net.viktorc.pp4j.ProcessManager} interface.
 * 
 * 
 * @author Viktor Csomor
 *
 */
public abstract class AbstractProcessManager implements ProcessManager {

	private final ProcessBuilder builder;
	
	/**
	 * Constructs a manager for the processes created by the specified {@link java.lang.ProcessBuilder}.
	 * 
	 * @param builder The instance to build the processes with.
	 */
	protected AbstractProcessManager(ProcessBuilder builder) {
		this.builder = builder;
	}
	@Override
	public Process start() throws IOException {
		return builder.start();
	}

}