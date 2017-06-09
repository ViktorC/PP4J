package net.viktorc.ppe4j;

/**
 * A class for convenience and factory methods for creating instances of implementations of the {@link net.viktorc.ppe4j.ProcessPoolExecutor} 
 * interface.
 * 
 * @author Viktor
 *
 */
public class ProcessPoolExecutors {

	/**
	 * Only static methods...
	 */
	private ProcessPoolExecutors() {
		
	}
	/**
	 * Returns a pool of processes. The initial size of the pool is the greater of the minimum pool size and the reserve size. This 
	 * method blocks until the initial number of processes started up. The size of the pool is dynamically adjusted based on the pool 
	 * parameters and the rate of incoming submissions. It is a proxy method for the constructor
	 * {@link net.viktorc.ppe4j.StandardProcessPoolExecutor#StandardProcessPoolExecutor(ProcessManagerFactory, int, int, int, long, boolean)} 
	 * with <code>verbose</code> set to <code>false</code>.
	 * 
	 * @param managerFactory  A {@link net.viktorc.ppe4j.ProcessManagerFactory} instance that is used to build 
	 * {@link net.viktorc.ppe4j.ProcessManager} instances that manage the processes' life cycle in the pool.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @return A pool of process shells each hosting a process.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
	 */
	public static ProcessPoolExecutor newCustomProcessPool(ProcessManagerFactory managerFactory, int minPoolSize, int maxPoolSize,
			int reserveSize, long keepAliveTime) throws InterruptedException {
		return new StandardProcessPoolExecutor(managerFactory, minPoolSize, maxPoolSize, reserveSize, keepAliveTime, false);
	}
	/**
	 * Returns a pool of a fixed number of processes. It is a convenience method for calling 
	 * {@link #newCustomProcessPool(ProcessManagerFactory, int, int, int, long)} with <code>minPoolSize</code> and 
	 * <code>maxPoolSize</code> equal and a <code>reserveSize</code> of 0. The number of shells in the pool is always 
	 * kept at the specified value.
	 * 
	 * @param managerFactory  A {@link net.viktorc.ppe4j.ProcessManagerFactory} instance that is used to build 
	 * {@link net.viktorc.ppe4j.ProcessManager} instances that manage the processes' life cycle in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @return A fixed size pool of process shells.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
	 */
	public static ProcessPoolExecutor newFixedProcessPool(ProcessManagerFactory managerFactory, int size, long keepAliveTime) throws InterruptedException {
		return newCustomProcessPool(managerFactory, size, size, 0, keepAliveTime);
	}
	/**
	 * Returns a pool of processes that grows in size as required. It is a convenience method for calling 
	 * {@link #newCustomProcessPool(ProcessManagerFactory, int, int, int, long)} with 0 as the <code>minPoolSize</code> and 
	 * the <code>reserveSize</code>, and <code>Integer.MAX_VALUE</code> as the maximum pool size. If <code>keepAliveTime</code> 
	 * is non-positive, the size of the process pool is only decreased if a process is cancelled after the execution of a 
	 * submission.
	 * 
	 * @param managerFactory  A {@link net.viktorc.ppe4j.ProcessManagerFactory} instance that is used to build 
	 * {@link net.viktorc.ppe4j.ProcessManager} instances that manage the processes' life cycle in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @return An unbounded pool of process shells.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
	 */
	public static ProcessPoolExecutor newCachedProcessPool(ProcessManagerFactory managerFactory, long keepAliveTime) throws InterruptedException {
		return newCustomProcessPool(managerFactory, 0, Integer.MAX_VALUE, 0, keepAliveTime);
	}
	/**
	 * Returns a fixed size pool holding a single process. It is a convenience method for calling 
	 * {@link #newFixedProcessPool(ProcessManagerFactory, int, long)} with 1 as the <code>size</code>.
	 * 
	 * @param managerFactory  A {@link net.viktorc.ppe4j.ProcessManagerFactory} instance that is used to build 
	 * {@link net.viktorc.ppe4j.ProcessManager} instances that manage the processes' life cycle in the pool.
	 * @param keepAliveTime The number of milliseconds after which idle processes are cancelled. If it is 0 or less, the 
	 * life-cycle of the processes will not be limited.
	 * @return A pool holding a single process shell.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
	 */
	public static ProcessPoolExecutor newSingleProcessPool(ProcessManagerFactory managerFactory, long keepAliveTime) throws InterruptedException {
		return newFixedProcessPool(managerFactory, 1, keepAliveTime);
	}
	
}
