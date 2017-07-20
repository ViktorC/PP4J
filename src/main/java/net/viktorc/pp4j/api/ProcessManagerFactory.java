package net.viktorc.pp4j.api;

/**
 * A functional interface that defines a method for creating new instances of an implementation of the 
 * {@link net.viktorc.pp4j.api.ProcessManager} interface.
 * 
 * @author Viktor Csomor
 *
 */
public interface ProcessManagerFactory {

	/**
	 * Constructs and returns a new instance of an implementation of the {@link net.viktorc.pp4j.api.ProcessManager} 
	 * interface.
	 * 
	 * @return A new instance of an implementation of the {@link net.viktorc.pp4j.api.ProcessManager} interface.
	 */
	ProcessManager newProcessManager();
	
}