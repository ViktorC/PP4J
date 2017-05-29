package net.viktorc.pspp;

import java.util.function.Consumer;

/**
 * A simple sub-class of the {@link net.viktorc.pspp.AbstractProcessManager} abstract class. It assumes that the process 
 * is immediately started up as soon as it is running (without having to wait for a certain output denoting that the process 
 * is ready), it has the process forcibly killed every time it needs to be terminated due to exceeding the keep-alive-time 
 * of the pool or not being reusable, and it implements no callback for when the process terminates.
 * 
 * @author A6714
 *
 */
public class SimpleProcessManager extends AbstractProcessManager {

	private final Consumer<ProcessShell> onStartup;
	
	/**
	 * Constructs a manager for the processes created by the specified {@link java.lang.ProcessBuilder}.
	 * 
	 * @param builder The instance to build the processes with.
	 * @param onStartup A consumer that is called after the process started up to allow for the execution 
	 * of commands to 'prepare' the process for the pool.
	 */
	public SimpleProcessManager(ProcessBuilder builder, Consumer<ProcessShell> onStartup) {
		super(builder);
		this.onStartup = onStartup;
	}
	@Override
	public boolean startsUpInstantly() {
		return true;
	}
	@Override
	public boolean isStartedUp(String outputLine, boolean standard) {
		return true;
	}
	@Override
	public void onStartup(ProcessShell shell) {
		onStartup.accept(shell);
	}
	@Override
	public boolean terminate(ProcessShell shell) {
		return false;
	}
	@Override
	public void onTermination(int resultCode) {
		
	}

}
