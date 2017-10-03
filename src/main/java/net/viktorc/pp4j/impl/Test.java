package net.viktorc.pp4j.impl;

public class Test {

	public static void main(String[] args) throws Exception {
		try (StandardProcessExecutor executor = new StandardProcessExecutor(
				new SimpleProcessManager(new ProcessBuilder("cmd.exe")))) {
			SimpleCommand command = new SimpleCommand("netstat & echo netstat done",
						(c, o) -> "netstat done".equals(o), (c, o) -> false);
			executor.start();
			executor.execute(new SimpleSubmission(command));
			System.out.println(command.getJointStandardOutLines());
		}
	}

}
