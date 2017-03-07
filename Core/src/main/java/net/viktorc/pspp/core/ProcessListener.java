package net.viktorc.pspp.core;

public interface ProcessListener {
	
	void onStarted(ProcessManager manager);
	void onTermination(int resultCode);
	
}
