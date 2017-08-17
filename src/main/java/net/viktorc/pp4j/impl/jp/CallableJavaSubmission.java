package net.viktorc.pp4j.impl.jp;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.Submission;
import net.viktorc.pp4j.impl.ProcessException;
import net.viktorc.pp4j.impl.SimpleCommand;

class CallableJavaSubmission implements Submission {

	private final String command;
	private final boolean terminateProcessAfterwards;
	private volatile Object result;
	private volatile Throwable error;
	
	CallableJavaSubmission(Callable<?> task, boolean terminateProcessAfterwards)
			throws IOException {
		command = ConversionUtil.encode(new SerializableCallableJavaTask<>(task));
		this.terminateProcessAfterwards = terminateProcessAfterwards;
	}
	@Override
	public List<Command> getCommands() {
		return Arrays.asList(new SimpleCommand(command,
				(c, l) -> {
						try {
							result = ConversionUtil.decode(l);
						} catch (ClassNotFoundException | IOException e) {
							throw new ProcessException(e);
						}
						return true;
					},
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
		return result;
	}

}
