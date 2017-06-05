package net.viktorc.pspp;

import java.io.IOException;

/**
 * An interface that defines methods that allow for the managing of the life cycle of pooled processes. It 
 * defines methods that start the process, determine and handle its startup behavior, and allow for its 
 * orderly termination.
 * 
 * @author Viktor
 *
 */
public interface ProcessManager {
	
	/**
	 * A method that starts a new process. The process created should always be the same and it should always 
	 * be started only upon the call of this method.
	 * 
	 * @return A new process.
	 * @throws IOException If the process cannot be started.
	 */
	Process start() throws IOException;
	/**
	 * A method that denotes whether the process should be considered started up instantly or if it is only 
	 * started up once a certain output has been printed to one of its out streams. If it returns true, the 
	 * process is instantly considered started up and ready as soon as it is running, and the method 
	 * {@link #onStartup(ProcessShell) onStartup} is executed. If it returns false, the method 
	 * {@link #isStartedUp(String, boolean) isStartedUp} determines when the process is considered started 
	 * up.
	 * 
	 * @return Whether the process instantly start up as soon as it is run or if it is started up and ready 
	 * only when a certain output has been written to one of its output streams.
	 */
	boolean startsUpInstantly();
	/**
	 * Handles the output of the underlying process after it has been started. The return value of the method 
	 * determines whether the process is to be considered started up and ready for the execution of the method 
	 * {@link #onStartup(ProcessShell) onStartup}. It is only ever called if {@link #startsUpInstantly() startsUpInstantly} 
	 * returns false.
	 * 
	 * @param outputLine A line of output produced by the process.
	 * @param standard Whether this line has been output to the standard out or to the error out.
	 * @return Whether the process is to be considered started up.
	 */
	boolean isStartedUp(String outputLine, boolean standard);
	/**
	 * A method called right after the process is started. Its main purpose is to allow for startup 
	 * activities such as the execution of commands.
	 * 
	 * @param shell The {@link net.viktorc.pspp.ProcessShell} instance in which the process is executed. 
	 * It serves as a handle for sending commands to the underlying process after the startup if needed.
	 */
	void onStartup(ProcessShell shell);
	/**
	 * A method called to terminate the process. It allows for an opportunity to execute commands to 
	 * close resources or to exit the process in an orderly way and avoid the need to forcibly terminate 
	 * it at all. The return value of the method determines whether there is a need to forcibly terminate
	 * the process. If true is returned, the termination is assumed to have been successful and the 
	 * process is not forcibly terminated afterwards. If it returns false, the process is killed.
	 * 
	 * @param shell  The {@link net.viktorc.pspp.ProcessShell} instance in which the process is executed. 
	 * It serves as a handle for sending commands to the underlying process to terminate it in an orderly 
	 * way.
	 * @return Whether the process has been successfully terminated or it should be forcibly cancelled.
	 */
	boolean terminate(ProcessShell shell);
	/**
	 * A method called right after the process terminates. Its main purpose is to allow for wrap-up 
	 * activities.
	 * 
	 * @param resultCode The result code the process returned.
	 */
	void onTermination(int resultCode);
	
}