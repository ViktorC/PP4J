package net.viktorc.pp4j.api;

import java.util.concurrent.Future;

/**
 * An interface that outlines an executing mechanism for {@link net.viktorc.pp4j.api.Submission} instances in 
 * separate processes and allows for the tracking of the progress of the execution via 
 * {@link java.util.concurrent.Future} instances. The interface also defines a method for shutting down the 
 * process pool and releasing the associated resources.
 * 
 * @author Viktor Csomor
 *
 */
public interface ProcessPool {

	/**
	 * Returns the {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance responsible for creating instances 
	 * of an implementation of the {@link net.viktorc.pp4j.api.ProcessManager} interface for managing the 
	 * processes of the pool.
	 * 
	 * @return The process manager factory of the process pool.
	 */
	ProcessManagerFactory getProcessManagerFactory();
	/**
	 * Submits the specified submission for execution and returns a {@link java.util.concurrent.Future} instance 
	 * which allows for the cancellation of the submission.
	 * 
	 * @param submission The submission to execute.
	 * @return A {@link java.util.concurrent.Future} instance that allows for the waiting for the completion of 
	 * the execution or the cancellation thereof.
	 */
	Future<?> submit(Submission submission);
	/**
	 * Shuts the executor service down freeing up the associated resources.
	 */
	void shutdown();
	
}
