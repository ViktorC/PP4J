package net.viktorc.pp4j.impl;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * A simple JNA interface mapping for the test library.
 * 
 * @author Viktor Csomor
 *
 */
public interface WrapperJNA extends Library {

	WrapperJNA INSTANCE = (WrapperJNA) Native.loadLibrary(TestUtils.getLibrary().getAbsolutePath(), WrapperJNA.class);
	
	void doStuff(int seconds);
	
}
