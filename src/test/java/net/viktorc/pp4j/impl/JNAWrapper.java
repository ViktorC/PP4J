package net.viktorc.pp4j.impl;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * A simple JNA interface mapping for the test library.
 * 
 * @author Viktor Csomor
 *
 */
public interface JNAWrapper extends Library {

	JNAWrapper INSTANCE = (JNAWrapper) Native.loadLibrary(TestUtils.getLibrary().getAbsolutePath(), JNAWrapper.class);
	
	void doStuff(int seconds);
	
}
