package net.viktorc.pspp;

/**
 * A listener interface for {@link net.viktorc.pspp.CommandSubmission} instances. It provides methods to call 
 * once the processing of the submitted commands has started or finished.
 * 
 * @author A6714
 *
 */
public interface SubmissionListener {

	/**
	 * A method that is executed once the processing of the submitted commands has begun.
	 */
	void onStartedProcessing();
	/**
	 * A method to execute once the processing of the submitted commands has completed.
	 */
	void onFinishedProcessing();
	
}
