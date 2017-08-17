package net.viktorc.pp4j.impl.jp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import net.viktorc.pp4j.impl.InternalSubmissionFuture;
import net.viktorc.pp4j.impl.StandardProcessPool;

public class JavaProcessPoolExecutor extends StandardProcessPool implements ExecutorService {

	private Object termLock;
	private volatile boolean terminated;
	
	public JavaProcessPoolExecutor(JavaProcessManagerFactory procManagerFactory, int minPoolSize,
			int maxPoolSize, int reserveSize, boolean verbose) throws InterruptedException {
		super(procManagerFactory, minPoolSize, maxPoolSize, reserveSize, verbose);
	}
	public <T> Future<T> submit(Callable<T> task, boolean terminateProcessAfterwards) {
		try {
			return new JavaSubmissionFuture<T>(submit(
					new CallableJavaSubmission(task, terminateProcessAfterwards)));
		} catch (IOException e) {
			return null;
		}
	}
	public <T> Future<T> submit(Runnable task, T result, boolean terminateProcessAfterwards) {
		try {
			return new JavaSubmissionFuture<T>(submit(new CallableJavaSubmission(
					Executors.callable(task, result), terminateProcessAfterwards)));
		} catch (IOException e) {
			return null;
		}
	}
	public Future<?> submit(Runnable task, boolean terminateProcessAfterwards) {
		try {
			return submit(new RunnableJavaSubmission(task, terminateProcessAfterwards));
		} catch (IOException e) {
			return null;
		}
	}
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
	public void shutdown() {
		if (!close)
			(new Thread(this::syncShutdown)).start();
	}
	@Override
	public List<Runnable> shutdownNow() {
		super.shutdown();
		return submissionQueue.stream()
				.filter(s -> s.getOriginalSubmission() instanceof RunnableJavaSubmission)
				.map(s -> ((RunnableJavaSubmission) s.getOriginalSubmission()).getTask())
				.collect(Collectors.toList());
	}
	@Override
	public boolean isShutdown() {
		return close;
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
		return submit(task, false);
	}
	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		return submit(task, result, false);
	}
	@Override
	public Future<?> submit(Runnable task) {
		return submit(task, false);
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
	
	private class JavaSubmissionFuture<T> implements Future<T> {

		InternalSubmissionFuture origFuture;
		
		JavaSubmissionFuture(InternalSubmissionFuture origFuture) {
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
		@SuppressWarnings("unchecked")
		@Override
		public T get() throws InterruptedException, ExecutionException {
			return (T) origFuture.get();
		}
		@SuppressWarnings("unchecked")
		@Override
		public T get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException {
			return (T) origFuture.get(timeout, unit);
		}

	}
}
