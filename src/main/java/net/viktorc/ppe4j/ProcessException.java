package net.viktorc.ppe4j;

/**
 * An exception thrown if an unexpected error occurs while running or interacting with a process that solicits the 
 * instantaneous termination of the process and possibly the entire pool.
 * 
 * @author Viktor
 *
 */
public class ProcessException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Constructs a wrapper for the specified exception.
	 * 
	 * @param e The source exception.
	 */
	public ProcessException(Exception e) {
		super(e);
	}

}