package net.viktorc.pp4j.impl;

import java.net.URISyntaxException;

/**
 * A simple native wrapper class for testing JNI performance.
 * 
 * @author Viktor Csomor
 *
 */
public class JNIWrapper {

	// Load the library.
	static {
		try {
			System.load(TestUtils.getLibrary().getAbsolutePath());
		} catch (URISyntaxException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	private native void doStuff(int seconds);
	
}
