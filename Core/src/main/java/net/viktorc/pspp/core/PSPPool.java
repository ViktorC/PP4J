package net.viktorc.pspp.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PSPPool {
	
	private ExecutorService executor;

	public PSPPool() {
		executor = Executors.newCachedThreadPool();
	}
	public PSPPool(int numberOfProcesses) {
		executor = Executors.newFixedThreadPool(numberOfProcesses);
	}
	
	
}
