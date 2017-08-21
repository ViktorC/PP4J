package net.viktorc.pp4j.impl.jpp;

import java.io.File;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.Submission;
import net.viktorc.pp4j.api.jpp.JavaProcessOptions;
import net.viktorc.pp4j.api.jpp.ProcessPoolExecutorService;
import net.viktorc.pp4j.api.jpp.JavaProcessOptions.JVMArch;
import net.viktorc.pp4j.api.jpp.JavaProcessOptions.JVMType;
import net.viktorc.pp4j.impl.ProcessException;
import net.viktorc.pp4j.impl.SimpleCommand;
import net.viktorc.pp4j.impl.SimpleProcessManager;
import net.viktorc.pp4j.impl.StandardProcessPool;

/**
 * A sub-class of {@link net.viktorc.pp4j.impl.StandardProcessPool} that implements the 
 * {@link net.viktorc.pp4j.api.jpp.ProcessPoolExecutorService} interface. It uses Java processes for the 
 * implementation of multiprocessing. It communicates with the processes via their standard streams 
 * exchanging serialized and encoded objects. It can send {@link java.lang.Runnable} and 
 * {@link java.util.concurrent.Callable} instances to the processes; and it receives the result
 * or exception object serialized and encoded into a string.
 * 
 * @author Viktor Csomor
 *
 */
public class JavaProcessPoolExecutorService extends StandardProcessPool implements ProcessPoolExecutorService {

	private final Object termLock;
	private volatile boolean terminated;
	
	/**
	 * Constructs a Java process pool executor using the specified parameters.
	 * 
	 * @param options The options for the "java" program used to create the new JVM. If it is null, no options 
	 * are used.
	 * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is 
	 * <code>0</code> or less, the life span of the process will not be limited.
	 * @param minPoolSize The minimum size of the process pool.
	 * @param maxPoolSize The maximum size of the process pool.
	 * @param reserveSize The number of available processes to keep in the pool.
	 * @param verbose Whether the events related to the management of the process pool should be logged. Setting 
	 * this parameter to <code>true</code> does not guarantee that logging will be performed as logging depends on 
	 * the SLF4J binding and the logging configurations, but setting it to <code>false</code> guarantees that no 
	 * logging will be performed by the constructed instance.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start 
	 * up.
	 * @throws IllegalArgumentException If the minimum pool size is less than 0, or the maximum pool size is less 
	 * than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum pool size.
	 */
	public JavaProcessPoolExecutorService(JavaProcessOptions options, long keepAliveTime, int minPoolSize,
			int maxPoolSize, int reserveSize, boolean verbose)
					throws InterruptedException {
		super(() -> new SimpleProcessManager(createJavaProcessBuilder(options),
				keepAliveTime, e -> { }), minPoolSize, maxPoolSize, reserveSize,
				verbose);
		termLock = new Object();
	}
	/**
	 * A utility method for creating the <code>ProcessBuilder</code> used to spawn the Java processes.
	 * 
	 * @param options The JVM options. If it is null, no options are used.
	 * @return The <code>ProcessBuilder</code> instance.
	 */
	private static ProcessBuilder createJavaProcessBuilder(JavaProcessOptions options) {
		String javaPath = System.getProperty("java.home") + File.separator + "bin" +
				File.separator + "java";
		String classPath = System.getProperty("java.class.path");
		String className = JavaProcess.class.getCanonicalName();
		List<String> javaOptions = new ArrayList<>();
		if (options != null) {
			JVMArch arch = options.getArch();
			JVMType type = options.getType();
			Integer initHeap = options.getInitHeapSizeMb();
			Integer maxHeap = options.getMaxHeapSizeMb();
			Integer stack = options.getStackSizeKb();
			if (arch != null)
				javaOptions.add(arch.equals(JVMArch.BIT_32) ? "-d32" : "-d64");
			if (type != null)
				javaOptions.add(type.equals(JVMType.CLIENT) ? "-client" : "-server");
			if (initHeap != null)
				javaOptions.add(String.format("-Xms%dm", initHeap));
			if (maxHeap != null)
				javaOptions.add(String.format("-Xmx%dm", maxHeap));
			if (stack != null)
				javaOptions.add(String.format("-Xss%dk", stack));
		}
		List<String> args = new ArrayList<>();
		args.add(javaPath);
		args.addAll(javaOptions);
		args.add("-cp");
		args.add(classPath);
		args.add(className);
		return new ProcessBuilder(args);
	}
	/**
	 * It executes a serializable {@link java.util.concurrent.Callable} instance with a serializable 
	 * return type in one of the processes of the pool and returns its return value. If the implementation 
	 * contains non-serializable, non-transient fields, the method fails.
	 * 
	 * @param task The task to execute.
	 * @param terminateProcessAfterwards Whether the process that executes the task should be terminated 
	 * afterwards.
	 * @return A {@link java.util.concurrent.Future} instance associated with the return value of the 
	 * task.
	 * @throws IOException If the serialization fails.
	 * @throws NotSerializableException If some object to be serialized does not implement the 
	 * {@link java.io.Serializable} interface.
	 */
	public <T> Future<T> submit(Callable<T> task,
			boolean terminateProcessAfterwards) throws IOException {
		return submit(new CallableJavaSubmission<T>(task, terminateProcessAfterwards));
	}
	/**
	 * It executes a serializable {@link #submit(Runnable)} instance in one of the processes of the pool. 
	 * If the implementation contains non-serializable, non-transient fields, the method fails.
	 * 
	 * @param task The task to execute.
	 * @param terminateProcessAfterwards Whether the process that executes the task should be terminated 
	 * afterwards.
	 * @return A {@link java.util.concurrent.Future} instance.
	 * @throws IOException If the serialization fails.
	 * @throws NotSerializableException If some object to be serialized does not implement the 
	 * {@link java.io.Serializable} interface.
	 */
	public Future<?> submit(Runnable task, boolean terminateProcessAfterwards)
			throws IOException {
		return submit(new RunnableJavaSubmission(task, terminateProcessAfterwards));
	}
	/**
	 * Synchronously shuts down the process pool.
	 */
	private void syncShutdown() {
		synchronized (termLock) {
			super.shutdown();
			terminated = true;
			termLock.notifyAll();
		}
	}
	@Override
	public void execute(Runnable command) {
		try {
			submit(command).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}
	@Override
	public synchronized void shutdown() {
		if (!isClosed())
			(new Thread(this::syncShutdown)).start();
	}
	@Override
	public List<Runnable> shutdownNow() {
		super.shutdown();
		return getQueuedSubmissions().stream()
				.filter(s -> s instanceof RunnableJavaSubmission)
				.map(s -> (Runnable) ((RunnableJavaSubmission) s).task)
				.collect(Collectors.toList());
	}
	@Override
	public boolean isShutdown() {
		return isClosed();
	}
	@Override
	public boolean isTerminated() {
		return terminated;
	}
	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		synchronized (termLock) {
			long waitTimeNs = unit.toNanos(timeout);
			while (waitTimeNs > 0 && !terminated) {
				long start = System.nanoTime();
				termLock.wait(waitTimeNs/1000000, (int) (waitTimeNs%1000000));
				waitTimeNs -= (System.nanoTime() - start);
			}
			return waitTimeNs > 0;
		}
	}
	@Override
	public <T> Future<T> submit(Callable<T> task) {
		try {
			return submit(task, false);
		} catch (IOException e) {
			throw new IllegalArgumentException("The task is not serializable.", e);
		}
	}
	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		try {
			return submit(Executors.callable(task, result), false);
		} catch (IOException e) {
			throw new IllegalArgumentException("The task is not serializable.", e);
		}
	}
	@Override
	public Future<?> submit(Runnable task) {
		try {
			return submit((Runnable & Serializable) task, false);
		} catch (IOException e) {
			throw new IllegalArgumentException("The task is not serializable.", e);
		}
	}
	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
			throws InterruptedException {
		List<Future<T>> futures = new ArrayList<Future<T>>();
		for (Callable<T> t : tasks)
			futures.add(submit(t));
		for (Future<T> f : futures) {
			try {
				if (!f.isDone())
					f.get();
			} catch (ExecutionException | CancellationException e) {
				continue;
			}
		}
		return futures;
	}
	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
			long timeout, TimeUnit unit) throws InterruptedException {
		List<Future<T>> futures = new ArrayList<Future<T>>();
		for (Callable<T> t : tasks)
			futures.add(submit(t));
		long waitTimeNs = unit.toNanos(timeout);
		for (int i = 0; i < futures.size(); i++) {
			Future<T> f = futures.get(i);
			long start = System.nanoTime();
			try {
				if (!f.isDone())
					f.get(waitTimeNs, TimeUnit.NANOSECONDS);
			} catch (ExecutionException | CancellationException e) {
				continue;
			} catch (TimeoutException e) {
				for (int j = i + 1; j < futures.size(); j++)
					futures.get(j).cancel(true);
				break;
			} finally {
				waitTimeNs -= (System.nanoTime() - start);
			}
		}
		return futures;
	}
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
			throws InterruptedException, ExecutionException {
		ExecutionException execException = null;
		for (Future<T> f : invokeAll(tasks)) {
			try {
				return f.get();
			} catch (ExecutionException e) {
				execException = e;
			} catch (CancellationException e) {
				continue;
			}
		}
		if (execException == null)
			throw new ExecutionException(new Exception("No task completed successfully."));
		throw execException;
	}
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		ExecutionException execException = null;
		for (Future<T> f : invokeAll(tasks)) {
			try {
				return f.get();
			} catch (ExecutionException e) {
				execException = e;
			} catch (CancellationException e) {
				continue;
			}
		}
		if (execException == null)
			throw new TimeoutException();
		throw execException;
	}
	
	/**
	 * A submission of a {@link java.lang.Runnable} to the Java process. It wraps the instance into a 
	 * serializable object which it then serializes, encodes, and sends to the process for execution. 
	 * It also looks for the completion signal to determine when the execution finishes or for a 
	 * serialized and encoded {@link java.lang.Throwable} instance output to the stderr stream in case of 
	 * an error.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class RunnableJavaSubmission implements Submission<Object> {

		final Runnable task;
		final String command;
		final boolean terminateProcessAfterwards;
		volatile Throwable error;
		
		/**
		 * Creates a submission for the specified {@link java.lang.Runnable}.
		 * 
		 * @param task The task to execute.
		 * @param terminateProcessAfterwards Whether the process should be terminated after the execution 
		 * of the task.
		 * @throws IOException If the encoding of the serialized task fails.
		 * @throws NotSerializableException If some object to be serialized does not implement the 
		 * {@link java.io.Serializable} interface.
		 */
		RunnableJavaSubmission(Runnable task, boolean terminateProcessAfterwards)
				throws IOException {
			this.task = task;
			this.terminateProcessAfterwards = terminateProcessAfterwards;
			command = ConversionUtil.encode(task);
		}
		@Override
		public List<Command> getCommands() {
			return Arrays.asList(new SimpleCommand(command,
					(c, l) -> JavaProcess.COMPLETION_SIGNAL.equals(l),
					(c, l) -> {
						try {
							error = (Throwable) ConversionUtil.decode(l);
						} catch (ClassNotFoundException | IOException e) {
							throw new ProcessException(e);
						}
						return true;
					}));
		}
		@Override
		public boolean doTerminateProcessAfterwards() {
			return terminateProcessAfterwards;
		}
		@Override
		public Object getResult() throws ExecutionException {
			if (error != null)
				throw new ExecutionException(error);
			return null;
		}
		
	}
	
	/**
	 * A submission of a {@link java.util.concurrent.Callable} to the Java process. It wraps the instance 
	 * into a serializable object which it then serializes, encodes, and sends to the process for execution. 
	 * It also looks for the completion signal to determine when the execution finishes.
	 * {@link java.util.concurrent.Callable}
	 * 
	 * A submission of a {@link java.util.concurrent.Callable} to the Java process. It wraps the instance 
	 * into a serializable object which it then serializes, encodes, and sends to the process for execution. 
	 * It also looks for the serialized and encoded return value of the <code>Callable</code>, and for a 
	 * serialized and encoded {@link java.lang.Throwable} instance output to the stderr stream in case of an 
	 * error.
	 * 
	 * @author Viktor Csomor
	 *
	 * @param <T> The return type of the <code>Callable</code> task.
	 */
	private class CallableJavaSubmission<T> implements Submission<T> {

		final String command;
		final boolean terminateProcessAfterwards;
		volatile T result;
		volatile Throwable error;
		
		/**
		 * Creates a submission for the specified {@link java.util.concurrent.Callable}.
		 * 
		 * @param task The task to execute.
		 * @param terminateProcessAfterwards Whether the process should be terminated after the execution 
		 * of the task.
		 * @throws IOException If the encoding of the serialized task fails.
		 * @throws NotSerializableException If some object to be serialized does not implement the 
		 * {@link java.io.Serializable} interface.
		 */
		CallableJavaSubmission(Callable<T> task, boolean terminateProcessAfterwards)
				throws IOException {
			this.terminateProcessAfterwards = terminateProcessAfterwards;
			command = ConversionUtil.encode(task);
		}
		@SuppressWarnings("unchecked")
		@Override
		public List<Command> getCommands() {
			return Arrays.asList(new SimpleCommand(command, (c, l) -> {
						try {
							result = (T) ConversionUtil.decode(l);
						} catch (ClassNotFoundException | IOException e) {
							throw new ProcessException(e);
						}
						return true;
					}, (c, l) -> {
						try {
							error = (Throwable) ConversionUtil.decode(l);
						} catch (ClassNotFoundException | IOException e) {
							throw new ProcessException(e);
						}
						return true;
					}));
		}
		@Override
		public boolean doTerminateProcessAfterwards() {
			return terminateProcessAfterwards;
		}
		@Override
		public T getResult() throws ExecutionException {
			if (error != null)
				throw new ExecutionException(error);
			return result;
		}
		
	}
	
}
