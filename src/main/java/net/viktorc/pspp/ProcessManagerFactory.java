package net.viktorc.pspp;

/**
 * A functional interface that defines a method for creating new instances of an implementation of the 
 * {@link net.viktorc.pspp.ProcessManager} interface.
 * 
 * @author A6714
 *
 */
public interface ProcessManagerFactory {

	/**
	 * Constructs and returns a new instance of an implementation of the {@link net.viktorc.pspp.ProcessManager} 
	 * interface.
	 * 
	 * @return A new instance of an implementation of the {@link net.viktorc.pspp.ProcessManager} interface.
	 */
	ProcessManager createNewProcessManager();
	
}
