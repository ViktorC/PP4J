package net.viktorc.ppe4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of the {@link net.viktorc.ppe4j.ProcessShell} interface for starting, managing, and interacting with a process. The 
 * life cycle of the associated process is the same as that of the {@link #run()} method of the instance. The process is not started until 
 * this method is called and the method does not terminate until the process does.
 * 
 * @author Viktor
 *
 */
public class StandardProcessShell implements ProcessShell, Runnable {

	/**
	 * If the process cannot be started or an exception occurs which would make it impossible to retrieve the actual 
	 * result code of the process.
	 */
	public static final int UNEXPECTED_TERMINATION_RESULT_CODE = -99;
	
	private static final Random ID_GEN = new Random();
	
	private final ProcessManager manager;
	private final long keepAliveTime;
	private final KeepAliveTimer timer;
	private final ExecutorService executor;
	private final ReentrantLock lock;
	private final long id;
	private Process process;
	private BufferedReader stdOutReader;
	private BufferedReader errOutReader;
	private BufferedWriter stdInWriter;
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
	 * process and, if specified, for terminating the process if it is idle for too long.
	 * @throws IllegalArgumentException If the manager is null.
	 */
	public StandardProcessShell(ProcessManager manager, long keepAliveTime, ExecutorService executorService) {
		if (manager == null)
			throw new IllegalArgumentException("The process handler cannot be null.");
		timer = keepAliveTime > 0 ? new KeepAliveTimer() : null;
		this.executor = executorService;
		this.manager = manager;
		this.keepAliveTime = keepAliveTime;
		id = ID_GEN.nextLong();
		lock = new ReentrantLock();
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
	 * Returns whether the process is currently running and not cancelled.
	 * 
	 * @return Whether the process is currently running and not cancelled.
	 */
	public boolean isActive() {
		return running && !stop;
	}
	/**
	 * Returns whether the manager is ready to process new commands.
	 * 
	 * @return Whether the manager is ready to process commands.
	 */
	public boolean isReady() {
		return isActive() && (!lock.isLocked() || lock.isHeldByCurrentThread());
	}
	/**
	 * It prompts the currently running process, if there is one, to terminate. Once the process has been successfully terminated, 
	 * subsequent calls are ignored and return true unless the process is started again.
	 * 
	 * @param forcibly Whether the process should be killed forcibly or using the {@link net.viktorc.ppe4j.ProcessManager#terminate(ProcessShell)} 
	 * method of the {@link net.viktorc.ppe4j.ProcessManager} instance assigned to the shell. The latter might be ineffective if the 
	 * process is currently executing a command or has not started up.
	 * @return Whether the process was successfully terminated.
	 */
	public boolean stop(boolean forcibly) {
		synchronized (lock) {
			if (stop)
				return true;
			boolean success = true;
			if (process != null) {
				if (!forcibly) {
					if (lock.tryLock()) {
						try {
							success = manager.terminate(this);
						} finally {
							lock.unlock();
						}
					} else
						success = false;
				} else
					process.destroy();
			}
			if (success) {
				if (timer != null)
					timer.stop();
				stop = true;
				lock.notifyAll();
			}
			return success;
		}
	}
	/**
	 * Starts listening to the specified channel.
	 * 
	 * @param reader The buffered reader to use to listen to the steam.
	 * @param standard Whether it is the standard out or the error out stream of the process.
	 * @throws IOException If there is some problem with the stream.
	 */
	private void startListening(BufferedReader reader, boolean standard) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty())
				continue;
			synchronized (lock) {
				if (startedUp) {
					commandProcessed = command == null || command.onNewOutput(line, standard);
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
	 * Writes the specified commands to the standard in stream of the process and blocks until they are 
	 * processed.
	 * 
	 * @param submission The submitted command(s) to execute.
	 * @return Whether the submission was executed. If the shell is busy processing an other submission, 
	 * it returns false; otherwise the submission is executed and true is returned once it's processed.
	 * @throws InterruptedException If the thread is interrupted while executing the commands.
	 * @throws IOException If the instruction cannot be written to the process' standard in stream.
	 */
	@Override
	public boolean execute(Submission submission) throws IOException, InterruptedException {
		if (running && !stop && lock.tryLock()) {
			try {
				if (timer != null)
					timer.stop();
				submission.onStartedProcessing();
				List<Command> commands = submission.getCommands();
				List<Command> processedCommands = commands.size() > 1 ? new ArrayList<>(commands.size() - 1) : null;
				synchronized (lock) {
					for (int i = 0; i < commands.size() && !submission.isCancelled() && running && !stop; i++) {
						command = commands.get(i);
						if (i != 0 && !command.doExecute(new ArrayList<>(processedCommands)))
							continue;
						commandProcessed = !command.generatesOutput();
						stdInWriter.write(command.getInstruction());
						stdInWriter.newLine();
						stdInWriter.flush();
						while (running && !stop && !commandProcessed)
							lock.wait();
						if (i < commands.size() - 1)
							processedCommands.add(command);
						command = null;
					}
				}
				if (running && !stop) {
					if (submission.doTerminateProcessAfterwards()) {
						if (!stop(false))
							stop(true);
					} else if (timer != null)
						timer.start();
				}
				return true;
			} finally {
				try {
					submission.onFinishedProcessing();
				} finally {
					commandProcessed = true;
					command = null;
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
		command = null;
		int rc = UNEXPECTED_TERMINATION_RESULT_CODE;
		try {
			lock.lock();
			try {
				// Start the process
				synchronized (lock) {
					if (stop)
						return;
					process = manager.start();
					stdOutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
					errOutReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
					stdInWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
					startedUp = manager.startsUpInstantly();
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
					while (!startedUp) {
						lock.wait();
						if (stop)
							return;
					}
					manager.onStartup(this);
					if (stop)
						return;
					if (timer != null) {
						executor.submit(timer);
						timer.start();
					}
				}
			} finally {
				lock.unlock();
			}
			rc = process.waitFor();
		} catch (Exception e) {
			throw new ProcessException(e);
		} finally {
			// Try to clean up and close all the resources.
			if (process != null) {
				if (process.isAlive())
					process.destroy();
				process = null;
			}
			if (timer != null)
				timer.stop();
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
				running = false;
				lock.notifyAll();
			}
			manager.onTermination(rc);
		}
	}
	@Override
	public String toString() {
		return "#" + Long.toHexString(id);
	}
	
	/**
	 * A simple timer that stops the process after <code>keepAliveTime</code> milliseconds unless the process is inactive 
	 * or the timer is cancelled. It also enables the timer to be restarted using the same thread.
	 * 
	 * @author Viktor
	 *
	 */
	private class KeepAliveTimer implements Runnable {

		boolean go;
		
		/**
		 * Restarts the timer.
		 */
		synchronized void start() {
			go = true;
			notifyAll();
		}
		/**
		 * Stops the timer.
		 */
		synchronized void stop() {
			go = false;
			notifyAll();
		}
		@Override
		public synchronized void run() {
			try {
				while (isActive()) {
					while (!go) {
						wait();
						if (!isActive())
							return;
					}
					long waitTime = keepAliveTime;
					while (go && waitTime > 0) {
						long start = System.currentTimeMillis();
						wait(waitTime);
						waitTime -= (System.currentTimeMillis() - start);
					}
					if (go) {
						if (!StandardProcessShell.this.stop(false))
							StandardProcessShell.this.stop(true);
					}
				}
			} catch (InterruptedException e) {
				// Just let the thread terminate.
			} catch (Exception e) {
				throw new ProcessException(e);
			} finally {
				go = false;
			}
		}
		
	}
	
}