package net.viktorc.ppe4j;

/**
 * A functional interface that defines a method for creating new instances of an implementation of the 
 * {@link net.viktorc.ppe4j.ProcessManager} interface.
 * 
 * @author Viktor
 *
 */
public interface ProcessManagerFactory {

	/**
	 * Constructs and returns a new instance of an implementation of the {@link net.viktorc.ppe4j.ProcessManager} 
	 * interface.
	 * 
	 * @return A new instance of an implementation of the {@link net.viktorc.ppe4j.ProcessManager} interface.
	 */
	ProcessManager createNewProcessManager();
	
}