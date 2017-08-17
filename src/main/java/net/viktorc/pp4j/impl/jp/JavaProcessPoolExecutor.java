package net.viktorc.pp4j.impl.jp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import net.viktorc.pp4j.impl.StandardProcessPool;

public class JavaProcessPoolExecutor extends StandardProcessPool implements ExecutorService {

	private Object termLock;
	private volatile boolean terminated;
	
	public JavaProcessPoolExecutor(JavaProcessManagerFactory procManagerFactory, int minPoolSize, int maxPoolSize, int reserveSize,
			boolean verbose) throws InterruptedException {
		super(procManagerFactory, minPoolSize, maxPoolSize, reserveSize, verbose);
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
		return submissionQueue.stream().filter(s -> s.getOriginalSubmission() instanceof RunnableJavaSubmission)
				.map(s -> ((RunnableJavaSubmission) s.getOriginalSubmission()).getTask()).collect(Collectors.toList());
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
			long waitTimeMs = unit.toMillis(timeout);
			while (waitTimeMs > 0 && !terminated) {
				long start = System.currentTimeMillis();
				termLock.wait(timeout);
				waitTimeMs -= (System.nanoTime() - start);
			}
			return waitTimeMs > 0;
		}
	}
	@Override
	public <T> Future<T> submit(Callable<T> task) {
		try {
			return new JavaSubmissionFuture<T>(submit(new CallableJavaSubmission(task, false)));
		} catch (IOException e) {
			return null;
		}
	}
	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		try {
			return new JavaSubmissionFuture<T>(submit(new CallableJavaSubmission(Executors.callable(task, result), false)));
		} catch (IOException e) {
			return null;
		}
	}
	@Override
	public Future<?> submit(Runnable task) {
		try {
			return submit(new RunnableJavaSubmission(task, false));
		} catch (IOException e) {
			return null;
		}
	}
	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		List<Future<T>> futures = new ArrayList<Future<T>>();
		for (Callable<T> t : tasks)
			futures.add(submit(t));
		for (Future<T> f : futures) {
			try {
				f.get();
			} catch (ExecutionException e) {
				// Ignore it.
			}
		}
		return futures;
	}
	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		// TODO Auto-generated method stub
		return null;
	}
}
