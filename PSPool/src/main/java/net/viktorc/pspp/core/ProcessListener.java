package net.viktorc.pspp.core;

/**
 * An interface for listeners for the {@link #ProcessManager} class. It defines a method to execute 
 * right after the starting of the process and method to call right after its termination.
 * 
 * @author A6714
 *
 */
public interface ProcessListener {
	
	/**
	 * A 'blank' process listener instance that ignores the process events.
	 */
	public static final ProcessListener NULL_LISTENER = new ProcessListener() {
		
		@Override
		public void onTermination(int resultCode) { }
		@Override
		public void onStarted(ProcessManager manager) { }
	};
	
	/**
	 * A method called right after the process is started. Its main purpose is to allow for preliminary 
	 * activities such as pre-loading.
	 * 
	 * @param manager The {@link #ProcessManager} instance to which the listener is subscribed. It 
	 * serves as a handle for sending commands to the underlying process after startup if needed.
	 */
	void onStarted(ProcessManager manager);
	/**
	 * A method called right after the process terminates. Its main purpose is to allow for wrap-up 
	 * activities.
	 * 
	 * @param resultCode The result code the process returned.
	 */
	void onTermination(int resultCode);
	
}
