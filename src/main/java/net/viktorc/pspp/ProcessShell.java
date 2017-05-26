package net.viktorc.pspp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class for starting, managing, and interacting with a process. The life cycle of the associated process is the same as that of the 
 * {@link #run() run} method of the instance. The process is not started until this method is called and the method does not terminate 
 * until the process does.
 * 
 * @author A6714
 *
 */
public class ProcessShell implements Runnable, AutoCloseable {

	/**
	 * If the process cannot be started or an exception occurs which would make it impossible to retrieve the actual 
	 * result code of the process.
	 */
	public static final int UNEXPECTED_TERMINATION_RESULT_CODE = -99;
	
	private final ProcessManager manager;
	private final long keepAliveTime;
	private final ExecutorService executor;
	private final boolean timed;
	private final boolean internalExecutor;
	private final ReentrantLock lock;
	private final long id;
	private Process process;
	private Reader stdOutReader;
	private Reader errOutReader;
	private BufferedWriter stdInWriter;
	private volatile Future<?> timedTask;
	private volatile Command command;
	private volatile boolean running;
	private volatile boolean stop;
	private volatile boolean startedUp;
	private volatile boolean commandProcessed;
	
	/**
	 * Constructs a shell for the specified process using two threads of the specified {@link java.util.concurrent.ExecutorService} to 
	 * listen to the out streams of the process and one thread for ensuring that the process is terminated once it times out if 
	 * <code>keepAliveTime</code> is greater than 0.
	 * 
	 * @param manager The process manager to handle the underlying process.
	 * @param keepAliveTime The number of milliseconds of idleness after which the process is cancelled. If it is 0 or 
	 * less, the life-cycle of the processes will not be limited.
	 * @param executorService A thread pool for the threads required for listening to the standard out and error out of the underlying 
	 * process and, if specified, for terminating the process if it is idle for too long. If it is null, the manager's own thread pool 
	 * is created. If the manager uses an external thread pool, it is not closed upon calling {@link #close() close}.
	 * @throws IOException If the process command is invalid.
	 * @throws IllegalArgumentException If the handler is null.
	 */
	protected ProcessShell(ProcessManager manager, long keepAliveTime, ExecutorService executorService)
			throws IOException {
		if (manager == null)
			throw new IllegalArgumentException("The process handler cannot be null.");
		timed = keepAliveTime > 0;
		internalExecutor = executorService == null;
		this.executor = internalExecutor ? Executors.newFixedThreadPool(timed ? 3 : 2) : executorService;
		lock = new ReentrantLock();
		this.manager = manager;
		this.keepAliveTime = keepAliveTime;
		id = (new Random()).nextLong();
	}
	/**
	 * Constructs a shell for the specified process.
	 * 
	 * @param manager The process manager to handle the underlying process.
	 * @param keepAliveTime The number of milliseconds of idleness after which the process is cancelled. If it is 0 or 
	 * less, the life-cycle of the processes will not be limited.
	 * @throws IOException If the process command is invalid.
	 * @throws IllegalArgumentException If the builder or the listener is null.
	 */
	protected ProcessShell(ProcessManager manager, long keepAliveTime)
		throws IOException {
		this(manager, keepAliveTime, null);
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
	 * Returns whether the manager is ready to process new commands.
	 * 
	 * @return Whether the manager is ready to process commands.
	 */
	protected boolean isReady() {
		return running && !stop && (!lock.isLocked() || lock.isHeldByCurrentThread());
	}
	/**
	 * Submits a {@link java.lang.Runnable} to the thread pool that calls the {@link #stop() cancel} method after 
	 * {@link #keepAliveTime} milliseconds.
	 */
	private void startKeepAliveTimer() {
		timedTask = executor.submit(() -> {
			try {
				Thread.sleep(keepAliveTime);
				if (!stop)
					ProcessShell.this.stop(false);
			} catch (InterruptedException e) {
				
			} catch (Exception e) {
				throw new ProcessException(e);
			}
		});
	}
	/**
	 * Starts listening to the specified channel.
	 * 
	 * @param reader The channel to listen to.
	 * @param standard Whether it is the standard out or the error out stream of the process.
	 * @throws IOException If there is some problem with the stream.
	 */
	private void startListening(Reader reader, boolean standard) throws IOException {
		int c;
		StringBuilder output = new StringBuilder();
		while ((c = reader.read()) != -1) {
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
				if (startedUp) {
					commandProcessed = (command == null || command.onNewOutput(line, standard));
					if (commandProcessed)
						lock.notifyAll();
				} else {
					startedUp = manager.isStartedUp(line, standard);
					if (startedUp)
						lock.notifyAll();
				}
			}
		}
	}
	/**
	 * It prompts the currently running process, if there is one, to terminate.
	 * 
	 * @param forcibly Whether the process should be killed forcibly or using the {@link net.viktorc.pspp.ProcessManager#terminate(ProcessShell) terminate} 
	 * method of the {@link net.viktorc.pspp.ProcessManager} instance assigned to the shell. The latter might be ineffective if the 
	 * process is currently executing, not listening to its standard in.
	 */
	protected void stop(boolean forcibly) {
		if (process == null)
			return;
		lock.lock();
		try {
			if (forcibly || !manager.terminate(this))
				process.destroy();
			stop = true;
		} finally {
			lock.unlock();
		}
	}
	/**
	 * Attempts to write the specified commands to the standard in stream of the process and blocks until they are 
	 * processed.
	 * 
	 * @param submission The submitted command(s) to execute.
	 * @return Whether the submission was executed. If the manager is busy processing an other submission, 
	 * it returns false; otherwise the submission is executed and true is returned once it's processed.
	 * @throws IOException If a command cannot be written to the standard in stream.
	 * @throws ProcessException If the thread is interrupted while executing the commands.
	 */
	public boolean execute(Submission submission)
			throws IOException {
		if (running && !stop && lock.tryLock()) {
			try {
				if (timedTask != null)
					timedTask.cancel(true);
				submission.onStartedProcessing();
				List<Command> commands = submission.getCommands();
				List<Command> processedCommands = commands.size() > 1 ? new ArrayList<>(commands.size() - 1) : null;
				synchronized (lock) {
					for (int i = 0; i < commands.size() && running && !stop; i++) {
						command = commands.get(i);
						if (submission.isCancelled())
							break;
						if (i != 0 && !command.doExecute(new ArrayList<>(processedCommands)))
							continue;
						commandProcessed = !command.generatesOutput();
						stdInWriter.write(command.getInstruction());
						stdInWriter.newLine();
						stdInWriter.flush();
						while (running && !stop && !commandProcessed) {
							try {
								lock.wait();
							} catch (InterruptedException e) {
								throw new ProcessException(e);
							}
						}
						if (i < commands.size() - 1)
							processedCommands.add(command);
						command = null;
					}
				}
				if (running && !stop) {
					if (submission.doTerminateProcessAfterwards())
						stop(false);
					else if (timed && (timedTask == null || timedTask.isCancelled()))
						startKeepAliveTimer();
				}
				return true;
			} finally {
				try {
					submission.onFinishedProcessing();
				} finally {
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
		int rc = -1;
		try {
			lock.lock();
			try {
				// Start process
				process = manager.start();
				stdOutReader = new InputStreamReader(process.getInputStream());
				errOutReader = new InputStreamReader(process.getErrorStream());
				stdInWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
				startedUp = manager.startsUpInstantly();
				synchronized (lock) {
					executor.submit(() -> {
						try {
							startListening(stdOutReader, true);
						} catch (IOException e) {
							throw new ProcessException(e);
						}
					});
					executor.submit(() -> {
						try {
							startListening(errOutReader, false);
						} catch (IOException e) {
							throw new ProcessException(e);
						}
					});
					while (!startedUp)
						lock.wait();
				}
				manager.onStartup(this);
				if (timed && (timedTask == null || timedTask.isCancelled()))
					startKeepAliveTimer();
			} finally {
				lock.unlock();
			}
			rc = process.waitFor();
		} catch (Exception e) {
			rc = UNEXPECTED_TERMINATION_RESULT_CODE;
			throw new ProcessException(e);
		} finally {
			running = false;
			// Try to clean up and close all the resources.
			if (timedTask != null)
				timedTask.cancel(true);
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
			synchronized (lock) {
				lock.notifyAll();
			}
			manager.onTermination(rc);
		}
	}
	@Override
	public void close() throws Exception {
		command = null;
		if (running && !stop)
			stop(true);
		if (internalExecutor)
			executor.shutdown();
	}
	@Override
	public String toString() {
		return "#" + Long.toHexString(id);
	}
	
}
