package net.viktorc.pspp;

/**
 * A simple abstract implementation of the {@link net.viktorc.pspp.ProcessListener} interface. Most of its 
 * methods are no-operations. It assumes that the process is immediately started up (without having to wait 
 * for a certain output denoting that the process is ready), it has the process forcibly killed every time 
 * it needs to be terminated due to exceeding the keep-alive-time of the pool or not being reusable, and it 
 * has no callback for when the process terminates. It should be stateless as the methods of the same 
 * {@link net.viktorc.pspp.SimpleProcessListener} instance are used for every {@link net.viktorc.pspp.ProcessManager} 
 * of a {@link net.viktorc.pspp.PSPPool} instance.
 * 
 * @author A6714
 *
 */
public abstract class SimpleProcessListener implements ProcessListener {

	@Override
	public boolean isStartedUp(String output, boolean standard) {
		return true;
	}
	@Override
	public boolean terminate(ProcessManager manager) {
		return false;
	}
	@Override
	public void onTermination(int resultCode) {
		
	}

}
