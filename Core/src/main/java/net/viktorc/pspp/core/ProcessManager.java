package net.viktorc.pspp.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProcessManager implements Runnable {

	/**
	 * If the process cannot be started or an exception occurs which would make it impossible to retrieve the actual 
	 * result code of the process.
	 */
	public static final int UNEXPECTED_TERMINATION_RESULT_CODE = -99;
	
	private final ProcessListener listener;
	private final ProcessBuilder builder;
	private Process process;
	private BufferedReader stdOutReader;
	private BufferedReader errOutReader;
	private BufferedWriter stdInWriter;
	private ExecutorService executor;
	private volatile boolean running;
	private volatile boolean stop;
	
	public ProcessManager(String processCommand, ProcessListener listener) throws IOException {
		this.listener = listener;
		builder = new ProcessBuilder(processCommand);
	}
	private void startListening(BufferedReader reader, boolean error) throws IOException {
		String line = "";
		while ((line = reader.readLine()) != null) {
			if (stop)
				break;
			line = line.trim();
			if ("".equals(line))
				continue;
			if (error)
				listener.onNewErrorOutput(line);
			else
				listener.onNewStandardOutput(line);
		}
	}
	public boolean isRunning() {
		return running;
	}
	public void writeLine(String line) throws IOException {
		if (running)
			stdInWriter.write(line + System.lineSeparator());
	}
	public void cancel() {
		if (running) {
			stop = true;
			process.destroy();
		}
	}
	@Override
	public void run() {
		if (running)
			throw new ProcessManagerException(new IllegalStateException());
		try {
			running = true;
			process = builder.start();
			stdOutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			errOutReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			stdInWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
			executor = Executors.newFixedThreadPool(2);
			executor.submit(() -> {
				try {
					startListening(stdOutReader, false);
				} catch (IOException e) {
					throw new ProcessManagerException(e);
				}
			});
			executor.submit(() -> {
				try {
					startListening(errOutReader, true);
				} catch (IOException e) {
					throw new ProcessManagerException(e);
				}
			});
			listener.onTermination(process.waitFor());
		} catch (IOException | InterruptedException e) {
			listener.onTermination(UNEXPECTED_TERMINATION_RESULT_CODE);
			throw new ProcessManagerException(e);
		} finally {
			try {
				stdOutReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				errOutReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				stdInWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			executor.shutdown();
			running = false;
		}
	}
	
	
}
