package net.viktorc.pspp.core;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A class for maintaining a pool of identical running processes.
 * 
 * @author A6714
 *
 */
public class PSPPool implements AutoCloseable {
	
	private final Queue<ProcessManager> processManagers;
	private final ExecutorService executor;

	/**
	 * Constructs a pool of the specified number of identical processes started by the specified command. The number 
	 * of pooled process is kept constant, meaning if a process dies, a new one is created.
	 * 
	 * @param processCommands The command for starting the processes.
	 * @param listener The process listener instance.
	 * @param poolSize The number of processes to maintain in the pool.
	 * @throws IOException If the process command is invalid.
	 */
	public PSPPool(String[] processCommands, ProcessListener listener, int poolSize)
			throws IOException {
		processManagers = new ConcurrentLinkedQueue<>();
		executor = Executors.newFixedThreadPool(poolSize);
		for (int i = 0; i < poolSize; i++) {
			ProcessManager p = new ProcessManager(processCommands);
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
	/**
	 * Executes the command on any of the available processes in the pool.
	 * 
	 * @param command The command to send to the process' standard in.
	 * @param commandListener The {@link #CommandListener} instance that possibly processes the outputs of 
	 * the process and determines when the process has finished processing the sent command.
	 * @param cancelProcessAfterwards Whether the process that executed the command should be cancelled after 
	 * the execution of the command.
	 * @return A {@link #java.util.concurrent.Future<?> Future} instance for the submitted command.
	 */
	public Future<?> executeCommand(String command, CommandListener commandListener, boolean cancelProcessAfterwards) {
		while (true) {
			for (ProcessManager p : processManagers) {
				if (p.isRunning()) {
					try {
						Future<?> future = p.sendCommand(command, commandListener, cancelProcessAfterwards);
						if (future != null)
							return future;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	@Override
	public void close() throws Exception {
		for (ProcessManager p : processManagers) {
			p.clearListeners();
			p.close();
		}
	}
	
}
