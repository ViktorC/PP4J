package net.viktorc.pspp;

import java.io.IOException;

/**
 * An abstract implementation of the {@link net.viktorc.pspp.ProcessManager} interface. Its implementations should be stateless 
 * as the methods of the same {@link net.viktorc.pspp.SimpleProcessManager} instance are used for every {@link net.viktorc.pspp.ProcessShell} 
 * of a {@link net.viktorc.pspp.PSPPool} instance.
 * 
 * 
 * @author A6714
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
