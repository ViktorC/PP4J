package net.viktorc.pp4j.impl.jp;

import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;

public abstract class JavaProcessManagerFactory implements ProcessManagerFactory {

	protected abstract JavaProcessManager newJavaProcessManager();
	@Override
	public final ProcessManager newProcessManager() {
		return newJavaProcessManager();
	}
	
}
