package net.viktorc.pspp.core;

public interface ProcessListener {

	void onNewStandardOutput(String standardOutput);
	void onNewErrorOutput(String errorOutput);
	void onTermination(int resultCode);
	
}
