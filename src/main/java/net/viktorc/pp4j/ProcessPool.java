package net.viktorc.pp4j;

import java.util.concurrent.Future;

/**
 * An interface that outlines an executing mechanism for {@link net.viktorc.pp4j.Submission} instances in separate processes and allows for 
 * the tracking of the progress of the execution via {@link java.util.concurrent.Future} instances. The interface also defines a method for 
 * shutting down the process pool and releasing the associated resources.
 * 
 * @author Viktor Csomor
 *
 */
public interface ProcessPool {

	/**
	 * Submits the specified submission for execution.
	 * 
	 * @param submission The submission to execute.
	 * @return A {@link java.util.concurrent.Future} instance that allows for the waiting for the completion of the execution or the 
	 * cancellation thereof.
	 */
	Future<?> submit(Submission submission);
	/**
	 * Shuts the executor service down freeing up the associated resources.
	 */
	void shutdown();
	
}
