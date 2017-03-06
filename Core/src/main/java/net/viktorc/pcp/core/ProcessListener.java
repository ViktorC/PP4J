package net.viktorc.pcp.core;

public interface ProcessListener {

	void onStandardOutput(String standardOutput);
	void onErrorOutput(String errorOutput);
	void onTermination(int resultCode);
	
}
