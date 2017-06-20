package net.viktorc.pp4j;

/**
 * A functional interface that defines a method for creating new instances of an implementation of the 
 * {@link net.viktorc.pp4j.ProcessManager} interface.
 * 
 * @author Viktor Csomor
 *
 */
public interface ProcessManagerFactory {

	/**
	 * Constructs and returns a new instance of an implementation of the {@link net.viktorc.pp4j.ProcessManager} 
	 * interface.
	 * 
	 * @return A new instance of an implementation of the {@link net.viktorc.pp4j.ProcessManager} interface.
	 */
	ProcessManager newProcessManager();
	
}