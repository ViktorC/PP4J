package net.viktorc.pcp.core;

import java.io.IOException;

public abstract class ProcessManager {

	private ProcessBuilder builder;
	private Process process;
	
	protected ProcessManager(String... processCommands) throws IOException {
		builder = new ProcessBuilder(processCommands);
	}
	public int start(ProcessListener listener) throws IOException, InterruptedException {
		process = builder.start();
		return process.waitFor();
	};
	
}
