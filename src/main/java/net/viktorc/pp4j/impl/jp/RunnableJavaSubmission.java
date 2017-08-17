package net.viktorc.pp4j.impl.jp;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.Submission;
import net.viktorc.pp4j.impl.ProcessException;
import net.viktorc.pp4j.impl.SimpleCommand;

class RunnableJavaSubmission implements Submission {

	private final Runnable task;
	private final String command;
	private final boolean terminateProcessAfterwards;
	private volatile Throwable error;
	
	RunnableJavaSubmission(Runnable task, boolean terminateProcessAfterwards)
			throws IOException {
		this.task = task;
		command = ConversionUtil.encode(new SerializableRunnableJavaTask(task));
		this.terminateProcessAfterwards = terminateProcessAfterwards;
	}
	Runnable getTask() {
		return task;
	}
	@Override
	public List<Command> getCommands() {
		return Arrays.asList(new SimpleCommand(command,
				(c, l) -> JavaProcess.COMPLETION_SIGNAL.equals(l),
				(c, l) -> {
					try {
						error = (Throwable) ConversionUtil.decode(l);
					} catch (ClassNotFoundException | IOException e) {
						throw new ProcessException(e);
					}
					return true;
				}));
	}
	@Override
	public boolean doTerminateProcessAfterwards() {
		return terminateProcessAfterwards;
	}
	@Override
	public Object getResult() throws ExecutionException {
		if (error != null)
			throw new ExecutionException(error);
		return null;
	}
	
}
