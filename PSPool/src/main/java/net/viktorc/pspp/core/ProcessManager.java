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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A runnable class for starting, managing, and interacting with processes. The process is started by calling the 
 * {@link #run()} method.
 * 
 * @author A6714
 *
 */
public class ProcessManager implements Runnable, AutoCloseable {

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
	private Lock startLock;
	private volatile CommandListener commandListener;
	private volatile boolean running;
	private volatile boolean ready;
	private volatile boolean stop;
	
	/**
	 * Constructs a manager for the specified process without actually starting the process.
	 * 
	 * @param process The process command.
	 * @throws IOException If the process command is invalid.
	 */
	public ProcessManager(String process) throws IOException {
		builder = new ProcessBuilder(process);
		listeners = new ConcurrentLinkedQueue<>();
		startLock = new ReentrantLock();
	}
	/**
	 * Subscribes a process listener to the manager instance.
	 * 
	 * @param listener The instance of {@link #ProcessListener ProcessListner} to add.
	 */
	public void addListener(ProcessListener listener) {
		listeners.add(listener);
	}
	/**
	 * Removes a process listener from the manager instance if it is already subscribed.
	 * 
	 * @param listener The instance of {@link #ProcessListener ProcessListner} to remove.
	 */
	public void removeListener(ProcessListener listener) {
		listeners.remove(listener);
	}
	/**
	 * Removes all subscribed listeners.
	 */
	public void clearListeners() {
		listeners.clear();
	}
	/**
	 * Returns whether the process is currently running.
	 * 
	 * @return Whether the process is currently running.
	 */
	public boolean isRunning() {
		return running;
	}
	/**
	 * Starts listening to the specified channel.
	 * 
	 * @param reader The channel to listen to.
	 * @param error Whether it is the error out or the standard out stream of the process.
	 * @throws IOException If there is some problem with the stream.
	 */
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
					ProcessManager.this.notifyAll();
				}
			}
		}
	}
	/**
	 * Attempts to write the specified command followed by a new line to the standard in stream of the process.
	 * 
	 * @param command The command to write to the process' standard in.
	 * @param commandListener An instance of {@link #commandListener CommandListener} for consuming the subsequent 
	 * outputs of the process and for determining whether the process has finished processing the command and is ready 
	 * for new commands based on these outputs.
	 * @return A {@link #Future} instance for the submitted command. If it is not null, the command was successfully 
	 * submitted. If the process has not started up yet or is processing another command at the moment, no new commands 
	 * can be submitted and null is returned.
	 * @throws IOException If the command cannot be written to the standard in stream.
	 */
	public Future<?> sendCommand(String command, CommandListener commandListener) throws IOException {
		startLock.lock();
		try {
			if (ready) {
				ready = false;
				stdInWriter.write(command);
				stdInWriter.newLine();
				stdInWriter.flush();
				this.commandListener = commandListener;
				return executor.submit(() -> {
					synchronized (ProcessManager.this) {
						while (!ready) {
							try {
								ProcessManager.this.wait();
							} catch (InterruptedException e) { }
						}
						this.commandListener = null;
					}
				});
			}
			return null;
		} finally {
			startLock.unlock();
		}
	}
	/**
	 * It prompts the currently running process, if there is one, to terminate.
	 */
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
			// Start process
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
			// Execute the onStarted listener methods.
			startLock.lock();
			try {
				ready = true;
				for (ProcessListener l : listeners)
					l.onStarted(this);
			} finally {
				startLock.unlock();
			}
			rc.set(process.waitFor());
		} catch (IOException | InterruptedException e) {
			// Set result code to the one associated with unexpected termination.
			rc.set(UNEXPECTED_TERMINATION_RESULT_CODE);
			throw new ProcessManagerException(e);
		} finally {
			// Try to clean up and close all the resources.
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
			// Execute the onTermination listener methods.
			for (ProcessListener l : listeners)
				l.onTermination(rc.get());
			running = false;
		}
	}
	@Override
	public void close() throws Exception {
		commandListener = null;
		cancel();
	}
	
}
