package net.viktorc.pp4j.impl;

public class Test {

	public static void main(String[] args) throws Exception {
		StandardProcessExecutor executor = new StandardProcessExecutor(new SimpleProcessManager(new ProcessBuilder("cmd.exe")));
		executor.start();
		executor.execute(new SimpleSubmission(new SimpleCommand("netstat -b -o", (c, o) -> {
			System.out.println(o);
			return o.contains("[::1]:51175");
		}, (c, o) -> false)));
		executor.close();
	}

}
