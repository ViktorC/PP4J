package net.viktorc.pp4j.impl;

import java.io.IOException;

import net.viktorc.pp4j.api.ProcessManager;

/**
 * An abstract implementation of the {@link net.viktorc.pp4j.api.ProcessManager} interface.
 * 
 * 
 * @author Viktor Csomor
 *
 */
public abstract class AbstractProcessManager implements ProcessManager {

	private final ProcessBuilder builder;
	private final long keepAliveTime;
	
	/**
	 * Constructs a manager for the processes created by the specified {@link java.lang.ProcessBuilder} with 
	 * the specified maximum life span.
	 * 
	 * @param builder The instance to build the processes with.
	 * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is 
	 * <code>0</code> or less, the life span of the process will not be limited.
	 */
	protected AbstractProcessManager(ProcessBuilder builder, long keepAliveTime) {
		this.builder = builder;
		this.keepAliveTime = keepAliveTime;
	}
	@Override
	public Process start() throws IOException {
		return builder.start();
	}
	@Override
	public long getKeepAliveTime() {
		return keepAliveTime;
	}

}