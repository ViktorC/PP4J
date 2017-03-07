package net.viktorc.pspp.core;

public interface CommandListener {

	boolean onNewStandardOutput(String standardOutput);
	boolean onNewErrorOutput(String errorOutput);
	
}
