package net.viktorc.pspp.core;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PSPPool {
	
	private final Queue<ProcessManager> processManagers;
	private final ExecutorService executor;

	public PSPPool(String processCommand, ProcessListener listener, int poolSize) throws IOException {
		processManagers = new ConcurrentLinkedQueue<>();
		executor = Executors.newFixedThreadPool(poolSize);
		for (int i = 0; i < poolSize; i++) {
			ProcessManager p = new ProcessManager(processCommand);
			p.addListener(listener);
			p.addListener(new ProcessListener() {
				
				@Override
				public void onTermination(int resultCode) {
					executor.submit(p);
				}
				@Override
				public void onStarted(ProcessManager manager) { }
			});
			processManagers.add(p);
			executor.submit(p);
		}
	}
	public void executeCommand(String command, CommandListener commandListener) {
		while (true) {
			for (ProcessManager p : processManagers) {
				if (p.isReady()) {
					try {
						p.sendCommand(command, commandListener);
						return;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
}
