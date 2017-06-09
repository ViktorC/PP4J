package net.viktorc.ppe4j;

/**
 * An exception thrown by the {@link net.viktorc.ppe4j.StandardProcessShell} class if an 
 * unexpected error occurs that solicits that instantaneous termination of the process.
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
	 * The only constructor.
	 * 
	 * @param e The source exception.
	 */
	public ProcessException(Exception e) {
		super(e);
	}

}