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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.ProcessExecutor;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.Submission;
import net.viktorc.pp4j.api.jpp.JavaProcessOptions;
import net.viktorc.pp4j.api.jpp.ProcessPoolExecutorService;
import net.viktorc.pp4j.api.jpp.JavaProcessOptions.JVMArch;
import net.viktorc.pp4j.api.jpp.JavaProcessOptions.JVMType;
import net.viktorc.pp4j.impl.AbstractProcessManager;
import net.viktorc.pp4j.impl.ProcessException;
import net.viktorc.pp4j.impl.SimpleCommand;
import net.viktorc.pp4j.impl.SimpleSubmission;
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
	public JavaProcessPoolExecutorService(JavaProcessOptions options, int minPoolSize,
			int maxPoolSize, int reserveSize, boolean verbose)
					throws InterruptedException {
		super(new JavaProcessManagerFactory(options), minPoolSize, maxPoolSize, reserveSize, verbose);
		termLock = new Object();
	}
	/**
	 * It executes a serializable {@link java.util.concurrent.Callable} instance with a serializable 
	 * return type in one of the processes of the pool and returns its return value. If the implementation 
	 * contains non-serializable, non-transient fields, or the return type is not serializable, the method 
	 * fails.
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
	public <T extends Serializable, S extends Callable<T> & Serializable> Future<T> submit(S task,
			boolean terminateProcessAfterwards) throws IOException {
		/* Due to the limitation of generics in Java, the serializability of the return type cannot be enforced 
		 * while adhering to the ExecutorService API. */
		return submit(new JavaSubmission<>(task, terminateProcessAfterwards));
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
	public <T extends Runnable & Serializable> Future<?> submit(T task, boolean terminateProcessAfterwards)
			throws IOException {
		return submit(new JavaSubmission<>(new SerializableRunnableWithResult<>(task, null),
				terminateProcessAfterwards));
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
		if (!isShutdown())
			(new Thread(this::syncShutdown)).start();
	}
	@Override
	public List<Runnable> shutdownNow() {
		super.shutdown();
		return getQueuedSubmissions().stream()
				.filter(s -> s instanceof JavaSubmission)
				.map(s -> (JavaSubmission<?,?>) s)
				.filter(s -> s.task instanceof SerializableRunnableWithResult)
				.map(s -> ((SerializableRunnableWithResult<?,?>) s.task).task)
				.collect(Collectors.toList());
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
			return new CastFuture<>(submit(new SerializableCallable<>((Callable<T> & Serializable) task),
					false));
		} catch (Exception e) {
			throw new IllegalArgumentException("The task is not serializable.", e);
		}
	}
	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		try {
			return new CastFuture<>(submit(new SerializableRunnableWithResult<>((Runnable & Serializable) task,
					(T & Serializable) result), false));
		} catch (Exception e) {
			throw new IllegalArgumentException("The task is not serializable.", e);
		}
	}
	@Override
	public Future<?> submit(Runnable task) {
		try {
			return submit((Runnable & Serializable) task, false);
		} catch (Exception e) {
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
	 * A sub-class of {@link net.viktorc.pp4j.impl.AbstractProcessManager} for the management of process instances 
	 * of the {@link net.viktorc.pp4j.impl.jpp.JavaProcess} class.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private static class JavaProcessManager extends AbstractProcessManager {
		
		/**
		 * Constructs an instance using the specified <code>builder</code> and <code>keepAliveTime</code>.
		 * 
		 * @param builder The <code>ProcessBuilder</code> to use for starting the Java processes.
		 * @param keepAliveTime The number of milliseconds of idleness after which the processes should be 
		 * terminated. If it is non-positive, the life-cycle of processes will not be limited based on 
		 * idleness.
		 */
		JavaProcessManager(ProcessBuilder builder, long keepAliveTime) {
			super(builder, keepAliveTime);
		}
		@Override
		public boolean startsUpInstantly() {
			return false;
		}
		@Override
		public boolean isStartedUp(String outputLine, boolean standard) {
			return standard && JavaProcess.STARTUP_SIGNAL.equals(outputLine);
		}
		@Override
		public void onStartup(ProcessExecutor executor) {
			// Warm up the JVM by ensuring that the most relevant classes are loaded.
			try {
				executor.execute(new JavaSubmission<>(new SerializableCallable<>(
						(Callable<Integer> & Serializable) () -> 1 + 2), false));
				executor.execute(new JavaSubmission<>(new SerializableRunnableWithResult<>(
						(Runnable & Serializable) () -> { System.out.println("test"); },
						new AtomicInteger()), false));
			} catch (Exception e) {
				return;
			}
		}
		@Override
		public boolean terminateGracefully(ProcessExecutor executor) {
			try {
				AtomicBoolean success = new AtomicBoolean(false);
				if (executor.execute(new SimpleSubmission(new SimpleCommand(JavaProcess.STOP_REQUEST,
						(c, l) -> {
							success.set(JavaProcess.STOP_SIGNAL.equals(l));
							return true;
						}, (c, l) -> true), false)))
					return success.get();
			} catch (Exception e) { /* Nothing to do. */ }
			return false;
		}
		
	}
	
	/**
	 * An implementation of the {@link net.viktorc.pp4j.api.ProcessManagerFactory} for the creation 
	 * of {@link net.viktorc.pp4j.impl.jpp.JavaProcessPoolExecutorService.JavaProcessManager} instances 
	 * using a single {@link java.lang.ProcessBuilder} instance.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private static class JavaProcessManagerFactory implements ProcessManagerFactory {
		
		JavaProcessOptions options;
		
		/**
		 * Constructs an instance based on the specified JVM options and <code>keepAliveTime</code> 
		 * which are used for the creation of all processes of the pool.
		 * 
		 * @param options The JVM options for starting the Java process.
		 */
		JavaProcessManagerFactory(JavaProcessOptions options) {
			this.options = options;
		}
		@Override
		public ProcessManager newProcessManager() {
			String javaPath = System.getProperty("java.home") + File.separator + "bin" +
					File.separator + "java";
			String classPath = System.getProperty("java.class.path");
			String className = JavaProcess.class.getCanonicalName();
			long keepAliveTime = 0;
			List<String> javaOptions = new ArrayList<>();
			if (options != null) {
				JVMArch arch = options.getArch();
				JVMType type = options.getType();
				Integer initHeap = options.getInitHeapSizeMb();
				Integer maxHeap = options.getMaxHeapSizeMb();
				Integer stack = options.getStackSizeKb();
				keepAliveTime = options.getKeepAliveTime();
				if (arch != null)
					javaOptions.add(arch.equals(JVMArch.BIT_32) ? "-d32" : "-d64");
				if (type != null)
					javaOptions.add(type.equals(JVMType.CLIENT) ? "-client" : "-server");
				if (initHeap > 0)
					javaOptions.add(String.format("-Xms%dm", initHeap));
				if (maxHeap > 0)
					javaOptions.add(String.format("-Xmx%dm", maxHeap));
				if (stack > 0)
					javaOptions.add(String.format("-Xss%dk", stack));
			}
			List<String> args = new ArrayList<>();
			args.add(javaPath);
			args.addAll(javaOptions);
			args.add("-cp");
			args.add(classPath);
			args.add(className);
			ProcessBuilder builder = new ProcessBuilder(args);
			// Redirect the error stream to reduce the number of used threads per process.
			builder.redirectErrorStream(true);
			return new JavaProcessManager(builder, keepAliveTime);
		}
		
	}
	
	/**
	 * An implementation of {@link net.viktorc.pp4j.api.Submission} for serializable {@link java.util.concurrent.Callable} 
	 * instances to submit in Java process. It serializes, encodes, and sends the task to the process for execution. It 
	 * also looks for the serialized and encoded return value of the <code>Callable</code>, and for a serialized and 
	 * encoded {@link java.lang.Throwable} instance output to the stderr stream in case of an error.
	 * 
	 * @author Viktor Csomor
	 *
	 * @param <T> The return type of the task.
	 * @param <S> An implementation of the {@link java.util.concurrent.Callable} and {@link java.io.Serializable} 
	 * interfaces with the return type <code>T</code>
	 */
	private static class JavaSubmission<T extends Serializable, S extends Callable<T> & Serializable>
			implements Submission<T> {

		final S task;
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
		JavaSubmission(S task, boolean terminateProcessAfterwards)
				throws IOException {
			this.task = task;
			this.terminateProcessAfterwards = terminateProcessAfterwards;
			command = Conversion.toString(task);
		}
		@SuppressWarnings("unchecked")
		@Override
		public List<Command> getCommands() {
			return Arrays.asList(new SimpleCommand(command, (c, l) -> {
						try {
							if (l.startsWith(JavaProcess.ERROR_PREFIX))
								error = (Throwable) Conversion.toObject(l.substring(
										JavaProcess.ERROR_PREFIX.length()));
							else
								result = (T) Conversion.toObject(l);
							return true;
						} catch (Exception e) {
							throw new ProcessException(e);
						}
					}, (c, l) -> true /* It cannot happen, as stderr is redirected. */));
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
	
	/**
	 * An implementation of the {@link java.util.concurrent.Future} interface for wrapping a <code>Future</code> 
	 * instance into a <code>Future</code> object with a return type that is a sub-type of that of the wrapped 
	 * instance.
	 * 
	 * @author Viktor Csomor
	 *
	 * @param <T> The return type of the original <code>Future</code> instance.
	 * @param <S> A subtype of <code>T</code>; the return type of the wrapper <code>Future</code> instance.
	 */
	private static class CastFuture<T, S extends T> implements Future<T> {

		final Future<S> origFuture;
		
		/**
		 * Constructs the wrapper object for the specified <code>Future</code> instance.
		 * 
		 * @param origFuture The  <code>Future</code> instance to wrap.
		 */
		CastFuture(Future<S> origFuture) {
			this.origFuture = origFuture;
		}
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return origFuture.cancel(mayInterruptIfRunning);
		}
		@Override
		public boolean isCancelled() {
			return origFuture.isCancelled();
		}
		@Override
		public boolean isDone() {
			return origFuture.isDone();
		}
		@Override
		public T get() throws InterruptedException, ExecutionException {
			return (T) origFuture.get();
		}
		@Override
		public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			return (T) origFuture.get(timeout, unit);
		}
		
	}
	
	/**
	 * A wrapper class implementing the {@link java.util.concurrent.Callable} interface for turning a serializable 
	 * <code>Callable</code> instance with a not explicitly serializable return type into a serializable <code>
	 * Callable</code> instance with an explicitly serializable return type.
	 * 
	 * @author Viktor Csomor
	 *
	 * @param <T> The serializable return type.
	 * @param <S> The serializable <code>Callable</code> implementation with a not explicitly serializable return type.
	 */
	private static class SerializableCallable<T extends Serializable, S extends Callable<? super T> & Serializable>
			implements Callable<T>, Serializable {

		static final long serialVersionUID = -5418713087898561239L;
		
		Callable<T> task;
		
		/**
		 * Constructs a serializable <code>Callable</code> instance with a serializable return type based on the 
		 * specified serializable <code>Callable</code> instance with a not explicitly serializable return type.
		 * 
		 * @param callable The <code>Callable</code> to wrap.
		 */
		@SuppressWarnings("unchecked")
		SerializableCallable(S callable) {
			task = (Callable<T>) callable;
		}
		@Override
		public T call() throws Exception {
			return task.call();
		}

	}
	
	/**
	 * A wrapper class implementing the {@link java.util.concurrent.Callable} interface for turning serializable 
	 * {@link java.lang.Runnable} instances with serializable results into serializable 
	 * {@link java.util.concurrent.Callable} instances with explicitly serialized return types.
	 * 
	 * @author Viktor Csomor
	 *
	 * @param <T> The {@link java.lang.Runnable} and {@link java.io.Serializable} task type.
	 * @param <S> The return type.
	 */
	private static class SerializableRunnableWithResult<T extends Runnable & Serializable, S extends Serializable> 
			implements Callable<S>, Serializable {
		
		static final long serialVersionUID = -1069566262473097740L;

		T task;
		S result;
		
		/**
		 * Constructs a serializable <code>Callable</code> instance from the specified <code>Runnable</code> and 
		 * return object.
		 * 
		 * @param task The task to execute.
		 * @param result The object to return as a result of the operation.
		 */
		SerializableRunnableWithResult(T task, S result) {
			this.task = task;
			this.result = result;
		}
		@Override
		public S call() throws Exception {
			task.run();
			return result;
		}
		
	}
	
}
