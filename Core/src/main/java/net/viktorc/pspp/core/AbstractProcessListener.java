package net.viktorc.pspp.core;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractProcessListener implements ProcessListener {

	protected List<String> stdOutLines = new ArrayList<>();
	protected List<String> errOutLines = new ArrayList<>();
	
	public List<String> getStdOutLines() {
		return new ArrayList<>(stdOutLines);
	}
	public List<String> getErrOutLines() {
		return new ArrayList<>(errOutLines);
	}
	@Override
	public void onNewStandardOutput(String standardOutput) {
		stdOutLines.add(standardOutput);
	}
	@Override
	public void onNewErrorOutput(String errorOutput) {
		errOutLines.add(errorOutput);
	}

}
