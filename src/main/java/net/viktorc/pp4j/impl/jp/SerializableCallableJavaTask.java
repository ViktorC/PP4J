package net.viktorc.pp4j.impl.jp;

import java.io.Serializable;
import java.util.concurrent.Callable;

class SerializableCallableJavaTask<T> implements Serializable, Callable<T> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7416088294845052107L;

	private final Callable<T> callable;
	
	SerializableCallableJavaTask(Callable<T> callable) {
		this.callable = callable;
	}
	@Override
	public T call() throws Exception {
		return callable.call();
	}

}
