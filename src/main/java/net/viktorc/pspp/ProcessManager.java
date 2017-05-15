package net.viktorc.pspp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class for starting, managing, and interacting with processes. The process is started by calling the 
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
	private final long keepAliveTime;
	private final Timer timer;
	private final long id;
	private Process process;
	private Reader stdOutReader;
	private Reader errOutReader;
	private BufferedWriter stdInWriter;
	private TimerTask task;
	private volatile ProcessListener listener;
	private volatile Command command;
	private volatile boolean running;
	private volatile boolean stop;
	private volatile boolean startedUp;
	private volatile boolean commandProcessed;
	
	/**
	 * Constructs a manager for the specified process without actually starting the process.
	 * 
	 * @param builder The process builder.
	 * @param keepAliveTime The number of milliseconds of idleness after which the process is cancelled. If it is 0 or 
	 * less, the life-cycle of the processes will not be limited.
	 * @param listener A process listener to manage the instance.
	 * @throws IOException If the process command is invalid.
	 */
	protected ProcessManager(ProcessBuilder builder, long keepAliveTime, ProcessListener listener) throws IOException {
		executor = Executors.newFixedThreadPool(2);
		lock = new ReentrantLock();
		this.builder = builder;
		this.keepAliveTime = keepAliveTime;
		timer = keepAliveTime > 0 ? new Timer() : null;
		id = (new Random()).nextLong();
		this.listener = listener;
	}
	/**
	 * Constructs a manager for the specified process without actually starting the process.
	 * 
	 * @param builder The process builder.
	 * @param keepAliveTime The number of milliseconds of idleness after which the process is cancelled. If it is 0 or 
	 * less, the life-cycle of the processes will not be limited.
	 * @throws IOException If the process command is invalid.
	 */
	protected ProcessManager(ProcessBuilder builder, long keepAliveTime) throws IOException {
		this(builder, keepAliveTime, null);
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
	protected boolean isRunning() {
		return running;
	}
	/**
	 * Returns whether the manager is ready process new commands.
	 * 
	 * @return Whether the manager is ready to process commands.
	 */
	protected boolean isReady() {
		return running && !stop && (!lock.isLocked() || lock.isHeldByCurrentThread());
	}
	/**
	 * Sets the listener of the process.
	 * 
	 * @param listener The listener of the process.
	 */
	protected void setListener(ProcessListener listener) {
		this.listener = listener;
	}
	/**
	 * It prompts the currently running process, if there is one, to terminate.
	 */
	protected void cancel() {
		lock.lock();
		try {
			if (listener == null || !listener.terminate(this)) {
				stop = true;
				if (process != null)
					process.destroy();
			} else
				stop = true;
		} finally {
			lock.unlock();
		}
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
			if (startedUp) {
				synchronized (lock) {
					try {
						commandProcessed = (command == null || command.getListener() == null ?
								commandProcessed : (error ? command.getListener().onNewErrorOutput(line) :
								command.getListener().onNewStandardOutput(line)));
					} finally {
						if (commandProcessed)
							lock.notifyAll();
					}
				}
			} else {
				startedUp = listener.isStartedUp(line, !error);
				if (startedUp) {
					synchronized (ProcessManager.this) {
						ProcessManager.this.notifyAll();
					}
				}
			}
		}
	}
	/**
	 * Attempts to write the specified commands to the standard in stream of the process and blocks until they are 
	 * processed.
	 * 
	 * @param commandSubmission The submitted command(s) to execute.
	 * @return Whether the submission was executed. If the manager is busy processing an other submission, 
	 * it returns false; otherwise the submission is executed and true is returned once it's processed.
	 * @throws IOException If a command cannot be written to the standard in stream.
	 * @throws ProcessManagerException If the thread is interrupted while executing the commands.
	 */
	public boolean executeSubmission(CommandSubmission commandSubmission)
			throws IOException {
		if (running && !stop && lock.tryLock()) {
			CommandSubmission submission = commandSubmission;
			CommandSubmissionListener submissionListener = commandSubmission.getSubmissionListener();
			try {
				if (task != null) {
					task.cancel();
					task = null;
				}
				if (submissionListener != null)
					submissionListener.onStartedProcessing();
				List<Command> commands = submission.getCommands();
				synchronized (lock) {
					for (int i = 0; i < commands.size() && running && !stop && !submission.isCancelled(); i++) {
						command = commands.get(i);
						commandProcessed = command.getListener() == null;
						stdInWriter.write(command.getInstruction());
						stdInWriter.newLine();
						stdInWriter.flush();
						while (running && !stop && !commandProcessed) {
							try {
								lock.wait();
							} catch (InterruptedException e) {
								throw new ProcessManagerException(e);
							}
						}
						command = null;
					}
				}
				if (running && !stop) {
					if (submission.doTerminateProcessAfterwards())
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
				}
				return true;
			} finally {
				try {
					if (submissionListener != null)
						submissionListener.onFinishedProcessing();
				} finally {
					submission.setProcessedToTrue();
					System.out.println("i");
					lock.unlock();
				}
			}
		} else
			return false;
	}
	@Override
	public synchronized void run() {
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
				startedUp = listener == null || listener.isStartedUp(null, true);
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
				while (!startedUp)
					wait();
				// Execute the onStartup listener method.
				if (listener != null)
					listener.onStartup(this);
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
		} catch (Exception e) {
			// Set the result code to the value associated with unexpected termination.
			rc.set(UNEXPECTED_TERMINATION_RESULT_CODE);
			throw new ProcessManagerException(e);
		} finally {
			// Try to clean up and close all the resources.
			if (task != null) {
				task.cancel();
				task = null;
			}
			process = null;
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
			synchronized (lock) {
				lock.notifyAll();
			}
			// Execute the onTermination listener method.
			if (listener != null)
				listener.onTermination(rc.get());
		}
	}
	@Override
	public void close() throws Exception {
		if (timer != null)
			timer.cancel();
		command = null;
		if (running && !stop)
			cancel();
		executor.shutdown();
	}
	@Override
	public String toString() {
		return "#" + Long.toHexString(id);
	}
	
}
