package net.viktorc.pp4j.impl;

import java.io.IOException;

public class Test {

	public static void main(String[] args) throws InterruptedException, IOException {
		StandardProcessExecutor executor = new StandardProcessExecutor(
				new SimpleProcessManager(new ProcessBuilder("cmd.exe")));
		SimpleCommand command = new SimpleCommand("netstat & echo netstat done",
					(c, o) -> "netstat done".equals(o), (c, o) -> false);
		executor.start();
		executor.execute(new SimpleSubmission(command));
		System.out.println(command.getJointStandardOutLines());
		executor.stop(true);
	}

}
