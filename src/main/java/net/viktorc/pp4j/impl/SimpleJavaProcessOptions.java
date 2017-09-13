package net.viktorc.pp4j.impl;

import net.viktorc.pp4j.api.JavaProcessOptions;

/**
 * A simple implementation of the {@link net.viktorc.pp4j.api.JavaProcessOptions} interface for the 
 * definition of JVM options and the timeout interval of Java processes.
 * 
 * @author Viktor Csomor
 *
 */
public class SimpleJavaProcessOptions implements JavaProcessOptions {

	private JVMArch arch;
	private JVMType type;
	private int initHeapSizeMb;
	private int maxHeapSizeMb;
	private int stackSizeKb;
	private long keepAliveTime;
	
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param arch The architecture of the JVM. If it is null, it will be ignored.
	 * @param type The type of the JVM. If it is null, it will be ignored.
	 * @param initHeapSizeMb The initial heap size of the JVM in megabytes. If it is non-positive, it will 
	 * be ignored.
	 * @param maxHeapSizeMb The maximum heap size of the JVM in megabytes. If it is non-positive, it will 
	 * be ignored.
	 * @param stackSizeKb The maximum stack size of the JVM in kilobytes. If it is non-positive, it will 
	 * be ignored.
	 * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it 
	 * is non-positive, the life span of the process will not be limited.
	 */
	public SimpleJavaProcessOptions(JVMArch arch, JVMType type, int initHeapSizeMb, int maxHeapSizeMb,
			int stackSizeKb, long keepAliveTime) {
		this.arch = arch;
		this.type = type;
		this.initHeapSizeMb = initHeapSizeMb;
		this.maxHeapSizeMb = maxHeapSizeMb;
		this.stackSizeKb = stackSizeKb;
		this.keepAliveTime = keepAliveTime;
	}
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param initHeapSizeMb The initial heap size of the JVM in megabytes. If it is non-positive, it will 
	 * be ignored.
	 * @param maxHeapSizeMb The maximum heap size of the JVM in megabytes. If it is non-positive, it will 
	 * be ignored.
	 * @param stackSizeKb The maximum stack size of the JVM in kilobytes. If it is non-positive, it will 
	 * be ignored.
	 * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it 
	 * is non-positive, the life span of the process will not be limited.
	 */
	public SimpleJavaProcessOptions(int initHeapSizeMb, int maxHeapSizeMb, int stackSizeKb,
			long keepAliveTime) {
		this(null, null, initHeapSizeMb, maxHeapSizeMb, stackSizeKb, keepAliveTime);
	}
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it 
	 * is non-positive, the life span of the process will not be limited.
	 */
	public SimpleJavaProcessOptions(long keepAliveTime) {
		this(null, null, 0, 0, 0, keepAliveTime);
	}
	@Override
	public JVMArch getArch() {
		return arch;
	}
	@Override
	public JVMType getType() {
		return type;
	}
	@Override
	public int getInitHeapSizeMb() {
		return initHeapSizeMb;
	}
	@Override
	public int getMaxHeapSizeMb() {
		return maxHeapSizeMb;
	}
	@Override
	public int getStackSizeKb() {
		return stackSizeKb;
	}
	@Override
	public long getKeepAliveTime() {
		return keepAliveTime;
	}
	
}
