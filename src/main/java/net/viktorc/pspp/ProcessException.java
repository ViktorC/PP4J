package net.viktorc.pspp;

/**
 * An exception thrown by the {@link net.viktorc.pspp.ProcessShell} class if an 
 * unexpected error occurs that solicits that instantaneous termination of the process.
 * 
 * @author A6714
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
	 * @param t The source exception.
	 */
	public ProcessException(Exception t) {
		super(t);
	}

}
