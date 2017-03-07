package net.viktorc.pspp.core;

import java.io.IOException;

public class App {

	public static void main(String[] args) throws IOException {
		PSPPool pool = new PSPPool("detroid.bat", new ProcessListener() {
			
			@Override
			public void onTermination(int resultCode) {
				System.out.println("Process terminated: " + resultCode);
			}
			
			@Override
			public void onStarted(ProcessManager manager) {
				try {
					System.out.println("On started command");
					manager.sendCommand("uci", new CommandListener() {
						
						@Override
						public boolean onNewStandardOutput(String standardOutput) {
							System.out.println(standardOutput);
							return "uciok".equals(standardOutput);
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
		}, 4);
		for (int i = 0; i < 4; i++) {
			int j = i;
			pool.executeCommand("go movetime 10000", new CommandListener() {
				
				@Override
				public boolean onNewStandardOutput(String standardOutput) {
					System.out.println("Process " + j + ": " + standardOutput);
					if (standardOutput.startsWith("bestmove")) {
						System.out.println("Process " + j + ": " + standardOutput);
						return true;
					}
					return false;
				}
				
				@Override
				public boolean onNewErrorOutput(String errorOutput) {
					return true;
				}
			});
			System.out.println("Go command " + j);
		}
	}

}
