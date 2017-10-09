package net.viktorc.pp4j.impl;

import java.net.URISyntaxException;

public class JNITest {

	static {
		try {
			System.load(TestUtils.getLibrary().getAbsolutePath());
		} catch (URISyntaxException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	private native void doStuff(int seconds);
	
	public static void main(String[] args) {
		long start = System.currentTimeMillis();
		(new JNITest()).doStuff(3);
		System.out.println(System.currentTimeMillis() - start);
	}
}
