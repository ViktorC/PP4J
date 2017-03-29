package net.viktorc.pspp.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.Queue;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A runnable class for starting, managing, and interacting with processes. The process is started by calling the 
 * {@link #run() run} method.
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
	
	private final ExecutorService executor;
	private final Lock startLock;
	private final Object commandOutputLock;
	private final ProcessBuilder builder;
	private final Queue<ProcessListener> listeners;
	private final long id;
	private final long keepAliveTime;
	private Process process;
	private Reader stdOutReader;
	private Reader errOutReader;
	private BufferedWriter stdInWriter;
	private Timer timer;
	private TimerTask task;
	private volatile CommandListener commandListener;
	private volatile boolean running;
	private volatile boolean ready;
	private volatile boolean stop;
	
	/**
	 * Constructs a manager for the specified process without actually starting the process.
	 * 
	 * @param processCommands The process command.
	 * @throws IOException If the process command is invalid.
	 */
	public ProcessManager(String[] processCommands, long keepAliveTime) throws IOException {
		executor = Executors.newFixedThreadPool(3);
		startLock = new ReentrantLock();
		commandOutputLock = new Object();
		builder = new ProcessBuilder(processCommands);
		listeners = new ConcurrentLinkedQueue<>();
		id = (new Random()).nextLong();
		this.keepAliveTime = keepAliveTime;
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
	 * Returns the 64 bit ID number of the instance.
	 * 
	 * @return The ID of the instance.
	 */
	public long getId() {
		return id;
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
	private void startListening(Reader reader, boolean error) throws IOException {
		int c;
		StringBuilder output = new StringBuilder();
		while ((c = reader.read()) != -1) {
			if (stop) {
				// On Windows all output of the process has to be read for it to terminate.
				while (reader.read() != -1);
				break;
			}
			String line;
			if (c == '\n') {
				line = output.toString().trim();
				output.setLength(0);
			} else {
				output.append((char) c);
				if (reader.ready())
					continue;
				else
					line = output.toString().trim();
			}
			if (line.isEmpty())
				continue;
			synchronized (commandOutputLock) {
				ready = (commandListener == null ? ready : (error ? commandListener.onNewErrorOutput(line) :
					commandListener.onNewStandardOutput(line)));
				if (ready) {
					commandOutputLock.notifyAll();
				}
			}
		}
	}
	/**
	 * Attempts to write the specified command followed by a new line to the standard in stream of the process.
	 * 
	 * @param command The command to write to the process' standard in.
	 * @param commandListener An instance of {@link #CommandListener CommandListener} for consuming the subsequent 
	 * outputs of the process and for determining whether the process has finished processing the command and is ready 
	 * for new commands based on these outputs. If it is null, it is assumed that the command should not produce any 
	 * output and the process will
	 * @param cancelAfterwards Whether the process should be cancelled after the execution of the command.
	 * @return A {@link #java.util.concurrent.Future<?> Future} instance for the submitted command. If it is not null, 
	 * the command was successfully submitted. If the process has not started up yet or is processing another command at 
	 * the moment, no new commands can be submitted and null is returned.
	 * @throws IOException If the command cannot be written to the standard in stream.
	 */
	public Future<?> sendCommand(String command, CommandListener commandListener, boolean cancelAfterwards)
			throws IOException {
		if (ready && !stop && startLock.tryLock()) {
			try {
				if (task != null) {
					task.cancel();
					task = null;
				}
				ready = false;
				stdInWriter.write(command);
				stdInWriter.newLine();
				stdInWriter.flush();
				this.commandListener = commandListener;
				return executor.submit(() -> {
					long start = System.currentTimeMillis();
					synchronized (commandOutputLock) {
						while (!ready) {
							try {
								commandOutputLock.wait();
							} catch (InterruptedException e) { }
						}
						this.commandListener = null;
						if (cancelAfterwards)
							cancel();
						else if (task == null) {
							task = new TimerTask() {
								
								@Override
								public void run() {
									task = null;
									cancel();
								}
							};
							timer.schedule(task, keepAliveTime);
						}
					}
					return System.currentTimeMillis() - start;
				});
			} finally {
				startLock.unlock();
			}
		} else
			return null;
	}
	/**
	 * Attempts to write the specified command followed by a new line to the standard in stream of the process.
	 * 
	 * @param command The command to write to the process' standard in.
	 * @param commandListener An instance of {@link #CommandListener CommandListener} for consuming the subsequent 
	 * outputs of the process and for determining whether the process has finished processing the command and is ready 
	 * for new commands based on these outputs.
	 * @return A {@link #java.util.concurrent.Future<?> Future} instance for the submitted command. If it is not null, 
	 * the command was successfully submitted. If the process has not started up yet or is processing another command at 
	 * the moment, no new commands can be submitted and null is returned.
	 * @throws IOException If the command cannot be written to the standard in stream.
	 */
	public Future<?> sendCommand(String command, CommandListener commandListener) throws IOException {
		return sendCommand(command, commandListener, false);
	}
	/**
	 * It prompts the currently running process, if there is one, to terminate.
	 */
	public void cancel() {
		if (running) {
			if (task != null)
				task.cancel();
			stop = true;
			process.destroy();
		}
	}
	@Override
	public synchronized void run() throws ProcessManagerException {
		running = true;
		ready = false;
		stop = false;
		timer = new Timer();
		AtomicInteger rc = new AtomicInteger(0);
		try {
			// Start process
			process = builder.start();
			stdOutReader = new InputStreamReader(process.getInputStream());
			errOutReader = new InputStreamReader(process.getErrorStream());
			stdInWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
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
			task = new TimerTask() {
				
				@Override
				public void run() {
					task = null;
					cancel();
				}
			};
			timer.schedule(task, keepAliveTime);
			rc.set(process.waitFor());
		} catch (IOException | InterruptedException e) {
			// Set result code to the one associated with unexpected termination.
			rc.set(UNEXPECTED_TERMINATION_RESULT_CODE);
			throw new ProcessManagerException(e);
		} finally {
			// Try to clean up and close all the resources.
			if (timer != null)
				timer.cancel();
			if (stdOutReader != null) {
				try {
					stdOutReader.close();
				} catch (IOException e) { }
			}
			if (errOutReader != null) {
				try {
					errOutReader.close();
				} catch (IOException e) { }
			}
			if (stdInWriter != null) {
				try {
					stdInWriter.close();
				} catch (IOException e) { }
			}
			ready = false;
			running = false;
			// Execute the onTermination listener methods.
			for (ProcessListener l : listeners)
				l.onTermination(rc.get());
		}
	}
	@Override
	public void close() throws Exception {
		if (timer != null)
			timer.cancel();
		commandListener = null;
		cancel();
		executor.shutdown();
	}
	
}
