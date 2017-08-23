package net.viktorc.pp4j.api;

import java.io.IOException;

/**
 * An interface that defines methods that allow for the managing of the life cycle of pooled processes. It 
 * defines methods that start the process, determine and handle its startup behavior, and allow for its 
 * orderly termination.
 * 
 * @author Viktor Csomor
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
	 * {@link #onStartup(ProcessExecutor)} is executed. If it returns false, the method 
	 * {@link #isStartedUp(String, boolean)} determines when the process is considered started up.
	 * 
	 * @return Whether the process instantly start up as soon as it is run or if it is started up and ready 
	 * only when a certain output has been written to one of its output streams.
	 */
	boolean startsUpInstantly();
	/**
	 * Handles the output of the underlying process after it has been started. The return value of the method 
	 * determines whether the process is to be considered started up and ready for the execution of the method 
	 * {@link #onStartup(ProcessExecutor)}. It is only ever called if {@link #startsUpInstantly()} returns false.
	 * 
	 * @param outputLine A line of output produced by the process.
	 * @param standard Whether this line has been output to the standard out or the standard error stream.
	 * @return Whether the process is to be considered started up.
	 */
	boolean isStartedUp(String outputLine, boolean standard);
	/**
	 * A method called right after the process is started. Its main purpose is to allow for startup 
	 * activities such as the execution of commands. The <code>executor</code> should be available and ready 
	 * for processing submissions within this call back, thus its 
	 * {@link net.viktorc.pp4j.api.ProcessExecutor#execute(Submission)} method should always return <code>true
	 * </code>.
	 * 
	 * @param executor The {@link net.viktorc.pp4j.api.ProcessExecutor} instance in which the process is executed. 
	 * It serves as a handle for sending commands to the underlying process after the startup if needed.
	 */
	void onStartup(ProcessExecutor executor);
	/**
	 * A method called to terminate the process. It allows for an opportunity to execute commands to 
	 * close resources or to exit the process in an orderly way. The return value of the method denotes 
	 * whether the process was successfully terminated. If orderly termination fails or for any other reason 
	 * this method returns <code>false</code>, the process is killed forcibly. If the return value is <code>
	 * true</code>, the process is considered successfully terminated.
	 * 
	 * @param executor The {@link net.viktorc.pp4j.api.ProcessExecutor} instance in which the process is executed. 
	 * It serves as a handle for sending commands to the underlying process to terminate it in an orderly 
	 * way.
	 * @return Whether the process has been successfully terminated.
	 */
	boolean terminateGracefully(ProcessExecutor executor);
	/**
	 * Determines the duration of continuous idleness after which the process is to be terminated. The process 
	 * is considered idle if it is started up and not processing any submission. If it returns <code>0</code> 
	 * or less, the life span of the process is not limited. By default, it returns <code>0</code>.
	 * 
	 * @return The number of milliseconds of idleness after which the process is to be terminated.
	 */
	default long getKeepAliveTime() {
		return 0;
	}
	/**
	 * A method called right after the process terminates. Its main purpose is to allow for wrap-up 
	 * activities.
	 * 
	 * @param resultCode The result code the process returned.
	 * @param lifeTime The life time of the process in milliseconds.
	 */
	default void onTermination(int resultCode, long lifeTime) { }
	
}