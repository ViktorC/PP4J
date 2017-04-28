package net.viktorc.pspp.core;

import java.io.File;
import java.io.IOException;

public class Test {

	private static final boolean REUSE = false;
	private static final int MIN_POOL_SIZE = 5;
	private static final int MAX_POOL_SIZE = 20;
	private static final int PROC_RESERVE = 1;
	private static final long KEEP_ALIVE_TIME = 60000;
	
	public static void main(String[] args) {
		try {
			String programLocation = "\"" + new File(Test.class.getResource("test.exe").toURI()
					.getPath()).getAbsolutePath() + "\"";
			System.out.println(programLocation);
			PSPPool pool;
			pool = new PSPPool(new ProcessBuilder(programLocation), new ProcessListener() {
				
				@Override
				public void onStarted(ProcessManager manager) {
					try {
						manager.sendCommand("start", new CommandListener() {
							
							@Override
							public boolean onNewStandardOutput(String standardOutput) {
								return "ok".equals(standardOutput);
							}
							@Override
							public boolean onNewErrorOutput(String errorOutput) {
								return true;
							}
						});
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				@Override
				public void onTermination(ProcessManager manager, int resultCode) {
					
				}
			}, MIN_POOL_SIZE, MAX_POOL_SIZE, PROC_RESERVE, KEEP_ALIVE_TIME);
			pool.setLogging(true);
			for (int i = 0; i < 40; i++) {
				if (i != 0) {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				pool.executeCommand("process 5", new CommandListener() {
					
					@Override
					public boolean onNewStandardOutput(String standardOutput) {
						if (standardOutput.equals("ready")) {
							System.out.println(standardOutput);
							return true;
						}
						return false;
					}
					@Override
					public boolean onNewErrorOutput(String errorOutput) {
						return true;
					}
				}, REUSE);
			}
			Thread.sleep(6000);
			pool.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

}
