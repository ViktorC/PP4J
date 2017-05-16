package net.viktorc.pspp;

/**
 * An interface for listeners for the {@link net.viktorc.pspp.ProcessManager} class. It defines a method to 
 * execute right after the starting of the process and method to call right after its termination. Moreover, 
 * it defines a method to execute before the forced termination of the process. Its implementation should be 
 * stateless as the methods of the same {@link net.viktorc.pspp.ProcessListener} instance is used for every 
 * {@link net.viktorc.pspp.ProcessManager} of a {@link net.viktorc.pspp.PSPPool} instance.
 * 
 * @author A6714
 *
 */
public interface ProcessListener {
	
	/**
	 * Handles the output of the underlying process after it has been started. The return value of the method 
	 * determines whether the process is to be considered started up. This method is first called with a null 
	 * argument right after the {@link java.lang.Process} is started. If the method returns true as a response 
	 * to this null value, the process is instantly considered started up, and the method 
	 * {@link #onStartup(ProcessManager) onStartup} is executed. If the method does not return true on this 
	 * first call, it is called every time a new line is output to the process' standard output or error output 
	 * until it returns true.
	 * 
	 * @param output A line of output produced by the process.
	 * @param standard Whether this line has been output to the standard out or to the error out.
	 * @return Whether the process is to be considered started up.
	 */
	boolean isStartedUp(String output, boolean standard);
	/**
	 * A method called right after the process is started. Its main purpose is to allow for startup 
	 * activities such as the execution of commands.
	 * 
	 * @param manager The {@link net.viktorc.pspp.ProcessManager} instance to which the listener is 
	 * subscribed. It serves as a handle for sending commands to the underlying process after the startup 
	 * if needed.
	 */
	void onStartup(ProcessManager manager);
	/**
	 * A method called to terminate the process. It allows for an opportunity to execute commands to 
	 * close resources or to exit the process in an orderly way and avoid the need to forcibly terminate 
	 * it at all. The return value of the method determines whether there is a need to forcibly terminate
	 * the process. If true is returned, the termination is assumed to have been successful and the 
	 * process is not forcibly terminated afterwards. If it returns false, the process is killed.
	 * 
	 * @param manager The {@link net.viktorc.pspp.ProcessManager} instance to which the listener is 
	 * subscribed. It serves as a handle for sending commands to the underlying process to terminate it 
	 * in an orderly way.
	 * @return Whether the process has been successfully terminated or it should be forcibly cancelled.
	 */
	boolean terminate(ProcessManager manager);
	/**
	 * A method called right after the process terminates. Its main purpose is to allow for wrap-up 
	 * activities.
	 * 
	 * @param resultCode The result code the process returned.
	 */
	void onTermination(int resultCode);
	
}
