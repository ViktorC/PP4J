package net.viktorc.pp4j.impl;

/**
 * A simple native wrapper class for testing JNI performance.
 * 
 * @author Viktor Csomor
 *
 */
public class WrapperJNI {

	// Load the library.
	static {
		System.load(TestUtils.getLibrary().getAbsolutePath());
	}
	
	public native void doStuff(int seconds);
	
}
