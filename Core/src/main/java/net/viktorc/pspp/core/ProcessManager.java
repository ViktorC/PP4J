package net.viktorc.pspp.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessManager implements Runnable {

	/**
	 * If the process cannot be started or an exception occurs which would make it impossible to retrieve the actual 
	 * result code of the process.
	 */
	public static final int UNEXPECTED_TERMINATION_RESULT_CODE = -99;
	
	private final ProcessBuilder builder;
	private final Queue<ProcessListener> listeners;
	private Process process;
	private BufferedReader stdOutReader;
	private BufferedReader errOutReader;
	private BufferedWriter stdInWriter;
	private ExecutorService executor;
	private Object startLock;
	private volatile CommandListener commandListener;
	private volatile boolean running;
	private volatile boolean ready;
	private volatile boolean stop;
	
	public ProcessManager(String processCommand) throws IOException {
		builder = new ProcessBuilder(processCommand);
		listeners = new ConcurrentLinkedQueue<>();
		startLock = new Object();
	}
	public void addListener(ProcessListener listener) {
		listeners.add(listener);
	}
	public void removeListener(ProcessListener listener) {
		listeners.remove(listener);
	}
	public void clearListeners() {
		listeners.clear();
	}
	public boolean isRunning() {
		return running;
	}
	public boolean isReady() {
		return ready;
	}
	private void startListening(BufferedReader reader, boolean error) throws IOException {
		String line = "";
		while ((line = reader.readLine()) != null) {
			if (stop)
				break;
			line = line.trim();
			if ("".equals(line))
				continue;
			synchronized (ProcessManager.this) {
				ready = (commandListener == null ? ready : (error ? commandListener.onNewErrorOutput(line) :
					commandListener.onNewStandardOutput(line)));
				if (ready) {
					System.out.println(line);
					ProcessManager.this.notifyAll();
				}
			}
		}
	}
	public boolean sendCommand(String command, CommandListener commandListener) throws IOException {
		synchronized (startLock) {
			if (ready) {
				ready = false;
				stdInWriter.write(command);
				stdInWriter.newLine();
				stdInWriter.flush();
				this.commandListener = commandListener;
				executor.submit(() -> {
					synchronized (ProcessManager.this) {
						while (!ready) {
							try {
								ProcessManager.this.wait();
							} catch (InterruptedException e) { }
						}
						this.commandListener = null;
					}
				});
				return true;
			}
			return false;
		}
	}
	public void cancel() {
		if (running) {
			stop = true;
			process.destroy();
		}
	}
	@Override
	public void run() throws ProcessManagerException {
		if (running)
			throw new ProcessManagerException(new IllegalStateException());
		running = true;
		ready = false;
		stop = false;
		AtomicInteger rc = new AtomicInteger(0);
		try {
			process = builder.start();
			stdOutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			errOutReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			stdInWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
			executor = Executors.newFixedThreadPool(3);
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
			synchronized (startLock) {
				ready = true;
				for (ProcessListener l : listeners)
					l.onStarted(this);
			}
			rc.set(process.waitFor());
		} catch (IOException | InterruptedException e) {
			rc.set(UNEXPECTED_TERMINATION_RESULT_CODE);
			throw new ProcessManagerException(e);
		} finally {
			if (stdOutReader != null) {
				try {
					stdOutReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (errOutReader != null) {
				try {
					errOutReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (stdInWriter != null) {
				try {
					stdInWriter.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (executor != null)
				executor.shutdown();
			ready = false;
			for (ProcessListener l : listeners)
				l.onTermination(rc.get());
			running = false;
		}
	}
	
	
}
