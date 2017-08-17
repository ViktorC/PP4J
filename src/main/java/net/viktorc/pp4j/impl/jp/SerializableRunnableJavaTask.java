package net.viktorc.pp4j.impl.jp;

import java.io.Serializable;

class SerializableRunnableJavaTask implements Serializable, Runnable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1059186698337790761L;

	private Runnable runnable;
	
	SerializableRunnableJavaTask(Runnable runnable) {
		this.runnable = runnable;
	}
	@Override
	public void run() {
		runnable.run();
	}

}
