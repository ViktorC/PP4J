package net.viktorc.pspp.core;

/**
 * An exception thrown by the {@link #ProcessManager} class if an unexpected error occurs that 
 * solicits that instantaneous termination of the process.
 * 
 * @author A6714
 *
 */
public class ProcessManagerException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * The only constructor.
	 * 
	 * @param e The source exception.
	 */
	public ProcessManagerException(Exception e) {
		super(e);
	}

}
