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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

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
	private final ReentrantLock lock;
	private final ProcessBuilder builder;
	private final Queue<ProcessListener> listeners;
	private final long keepAliveTime;
	private final Timer timer;
	private final long id;
	private Process process;
	private Reader stdOutReader;
	private Reader errOutReader;
	private BufferedWriter stdInWriter;
	private TimerTask task;
	private volatile CommandListener commandListener;
	private volatile boolean commandProcessed;
	private volatile boolean running;
	private volatile boolean stop;
	
	/**
	 * Constructs a manager for the specified process without actually starting the process.
	 * 
	 * @param builder The process builder.
	 * @throws IOException If the process command is invalid.
	 */
	public ProcessManager(ProcessBuilder builder, long keepAliveTime) throws IOException {
		executor = Executors.newFixedThreadPool(2);
		lock = new ReentrantLock();
		this.builder = builder;
		listeners = new ConcurrentLinkedQueue<>();
		this.keepAliveTime = keepAliveTime;
		timer = keepAliveTime > 0 ? new Timer() : null;
		id = (new Random()).nextLong();
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
			synchronized (lock) {
				commandProcessed = (commandListener == null ? commandProcessed :
					(error ? commandListener.onNewErrorOutput(line) : commandListener.onNewStandardOutput(line)));
				if (commandProcessed)
					lock.notifyAll();
			}
		}
	}
	/**
	 * Attempts to write the specified command followed by a new line to the standard in stream of the process and blocks 
	 * until it is processed.
	 * 
	 * @param command The command to write to the process' standard in.
	 * @param onSubmitted A consumer that is executed when the command is actually written to the standard in of the 
	 * underlying process with the command itself as its argument. If the command is not accepted  because the manager 
	 * is occupied with another command, the consumer is not executed. If it is null, it is ignored.
	 * @param commandListener An instance of {@link #CommandListener CommandListener} for consuming the subsequent 
	 * outputs of the process and for determining whether the process has finished processing the command and is ready 
	 * for new commands based on these outputs. If it is null, the process manager will not accept any other command for 
	 * the rest of the current progress' life cycle and the cancelAfterwards parameter is rendered ineffective.
	 * @param cancelAfterwards Whether the process should be cancelled after the execution of the command.
	 * @return Whether the command was submitted for execution. If the manager is busy processing another command, it 
	 * returns false; otherwise the command is submitted and true is returned once it's processed.
	 * @throws IOException If the command cannot be written to the standard in stream.
	 * @throws IllegalArgumentException If the command is null or empty or if the command listener is null.
	 */
	public boolean sendCommand(String command, Consumer<String> onSubmitted, CommandListener commandListener,
			boolean cancelAfterwards) throws IOException, IllegalArgumentException {
		if (command == null)
			throw new IllegalArgumentException("The command cannot be null or empty.");
		if (commandListener == null)
			throw new IllegalArgumentException("The command listener cannot be null.");
		if (!stop && lock.tryLock()) {
			try {
				if (task != null) {
					task.cancel();
					task = null;
				}
				this.commandListener = commandListener;
				commandProcessed = false;
				stdInWriter.write(command);
				stdInWriter.newLine();
				stdInWriter.flush();
				if (onSubmitted != null)
					onSubmitted.accept(command);
				synchronized (lock) {
					while (!commandProcessed) {
						try {
							lock.wait();
						} catch (InterruptedException e) { }
					}
				}
				this.commandListener = null;
				if (cancelAfterwards)
					cancel();
				else if (timer != null && task == null) {
					task = new TimerTask() {
						
						@Override
						public void run() {
							task = null;
							ProcessManager.this.cancel();
						}
					};
					timer.schedule(task, keepAliveTime);
				}
				return true;
			} finally {
				lock.unlock();
			}
		} else
			return false;
	}
	/**
	 * Attempts to write the specified command followed by a new line to the standard in stream of the process and blocks 
	 * until it is processed.
	 * 
	 * @param command The command to write to the process' standard in.
	 * @param commandListener An instance of {@link #CommandListener CommandListener} for consuming the subsequent 
	 * outputs of the process and for determining whether the process has finished processing the command and is ready 
	 * for new commands based on these outputs. If it is null, the process manager will not accept any other command for 
	 * the rest of the current progress' life cycle and the cancelAfterwards parameter is rendered ineffective.
	 * @param cancelAfterwards Whether the process should be cancelled after the execution of the command.
	 * @return Whether the command was submitted for execution. If the manager is busy processing another command, it 
	 * returns false; otherwise the command is submitted and true is returned once it's processed.
	 * @throws IOException If the command cannot be written to the standard in stream.
	 * @throws IllegalArgumentException If the command is null or empty or if the command listener is null.
	 */
	public boolean sendCommand(String command, CommandListener commandListener, boolean cancelAfterwards)
			throws IOException, IllegalArgumentException {
		return sendCommand(command, null, commandListener, cancelAfterwards);
	}
	/**
	 * Attempts to write the specified command followed by a new line to the standard in stream of the process and blocks 
	 * until it is processed.
	 * 
	 * @param command The command to write to the process' standard in.
	 * @param onSubmitted A consumer that is executed when the command is actually written to the standard in of the 
	 * underlying process with the command itself as its argument. If the command is not accepted  because the manager 
	 * is occupied with another command, the consumer is not executed. If it is null, it is ignored.
	 * @param commandListener An instance of {@link #CommandListener CommandListener} for consuming the subsequent 
	 * outputs of the process and for determining whether the process has finished processing the command and is ready 
	 * for new commands based on these outputs. If it is null, the process manager will not accept any other command for 
	 * the rest of the current progress' life cycle and the cancelAfterwards parameter is rendered ineffective.
	 * @return Whether the command was submitted for execution. If the manager is busy processing another command, it 
	 * returns false; otherwise the command is submitted and true is returned once it's processed.
	 * @throws IOException If the command cannot be written to the standard in stream.
	 * @throws IllegalArgumentException If the command is null or empty or if the command listener is null.
	 */
	public boolean sendCommand(String command, Consumer<String> onSubmitted, CommandListener commandListener)
			throws IOException, IllegalArgumentException {
		return sendCommand(command, onSubmitted, commandListener, false);
	}
	/**
	 * Attempts to write the specified command followed by a new line to the standard in stream of the process and blocks 
	 * until it is processed.
	 * 
	 * @param command The command to write to the process' standard in.
	 * @param commandListener An instance of {@link #CommandListener CommandListener} for consuming the subsequent 
	 * outputs of the process and for determining whether the process has finished processing the command and is ready 
	 * for new commands based on these outputs. If it is null, the process manager will not accept any other command for 
	 * the rest of the current progress' life cycle and the cancelAfterwards parameter is rendered ineffective.
	 * @return Whether the command was submitted for execution. If the manager is busy processing another command, it 
	 * returns false; otherwise the command is submitted and true is returned once it's processed.
	 * @throws IOException If the command cannot be written to the standard in stream.
	 * @throws IllegalArgumentException If the command is null or empty or if the command listener is null.
	 */
	public boolean sendCommand(String command, CommandListener commandListener)
			throws IOException, IllegalArgumentException {
		return sendCommand(command, null, commandListener, false);
	}
	/**
	 * It prompts the currently running process, if there is one, to terminate.
	 */
	public void cancel() {
		if (task != null) {
			task.cancel();
			task = null;
		}
		stop = true;
		if (process != null)
			process.destroy();
	}
	@Override
	public synchronized void run() throws ProcessManagerException {
		running = true;
		stop = false;
		AtomicInteger rc = new AtomicInteger(0);
		try {
			lock.lock();
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
				// TODO Consume initial output or let the process listener process it.
				// Execute the onStarted listener methods.
				for (ProcessListener l : listeners)
					l.onStarted(this);
				if (timer != null && task == null) {
					task = new TimerTask() {
						
						@Override
						public void run() {
							task = null;
							ProcessManager.this.cancel();
						}
					};
					timer.schedule(task, keepAliveTime);
				}
			} finally {
				lock.unlock();
			}
			rc.set(process.waitFor());
		} catch (IOException | InterruptedException e) {
			// Set the result code to the value associated with unexpected termination.
			rc.set(UNEXPECTED_TERMINATION_RESULT_CODE);
			throw new ProcessManagerException(e);
		} finally {
			// Try to clean up and close all the resources.
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
			running = false;
			// Execute the onTermination listener methods.
			for (ProcessListener l : listeners)
				l.onTermination(this, rc.get());
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
	@Override
	public String toString() {
		return "#" + Long.toHexString(id);
	}
	
}
