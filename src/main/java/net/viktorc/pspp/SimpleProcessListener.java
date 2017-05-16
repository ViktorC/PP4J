package net.viktorc.pspp;

import java.util.function.Consumer;

/**
 * A simple implementation of the {@link net.viktorc.pspp.ProcessListener} interface. Most of its methods are 
 * no-operations. It assumes that the process is immediately started up (without having to wait for a certain 
 * output denoting that the process is ready), it has the process killed every time it needs to be terminated 
 * due to exceeding the keep-alive-time of the pool or not being reusable, and it has no callback for when the 
 * process terminates. It should be stateless as the methods of the same {@link net.viktorc.pspp.SimpleProcessListener} 
 * instance are used for every {@link net.viktorc.pspp.ProcessManager} of a {@link net.viktorc.pspp.PSPPool} 
 * instance.
 * 
 * @author A6714
 *
 */
public class SimpleProcessListener implements ProcessListener {

	private final Consumer<ProcessManager> onStartup;
	
	/**
	 * Constructs a simple process listener. The specified {@link java.util.function.Consumer} is used to 
	 * implement the {@link net.viktorc.pspp.ProcessListener#onStartup(ProcessManager) onStartup} method
	 * of the {@link net.viktorc.pspp.ProcessListener} interface.
	 * 
	 * @param onStartup A {@link java.util.function.Consumer} to allow for the execution of 
	 * {@link net.viktorc.pspp.CommandSubmission} instances to prepare the {@link net.viktorc.pspp.ProcessManager} 
	 * for the pool.
	 * @throws IllegalArgumentException If the consumer is null
	 */
	public SimpleProcessListener(Consumer<ProcessManager> onStartup) {
		if (onStartup == null)
			throw new IllegalArgumentException("The consumer cannot be null");
		this.onStartup = onStartup;
	}
	@Override
	public boolean isStartedUp(String output, boolean standard) {
		return true;
	}
	@Override
	public final void onStartup(ProcessManager manager) {
		onStartup.accept(manager);
	}
	@Override
	public boolean terminate(ProcessManager manager) {
		return false;
	}
	@Override
	public void onTermination(int resultCode) { }

}
