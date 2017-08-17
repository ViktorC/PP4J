package net.viktorc.pp4j.impl.jp;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.viktorc.pp4j.impl.InternalSubmissionFuture;

class JavaSubmissionFuture<T> implements Future<T> {

	private InternalSubmissionFuture origFuture;
	
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
