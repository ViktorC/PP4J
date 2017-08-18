package net.viktorc.pp4j.impl.jpp;

import net.viktorc.pp4j.api.jpp.JavaProcessOptions;

/**
 * A simple implementation of the {@link net.viktorc.pp4j.api.jpp.JavaProcessOptions} interface for the 
 * definition of JVM options.
 * 
 * @author Viktor Csomor
 *
 */
public class SimpleJavaProcessOptions implements JavaProcessOptions {

	private JVMArch arch;
	private JVMType type;
	private Integer initHeapSizeMb;
	private Integer maxHeapSizeMb;
	private Integer stackSizeKb;
	
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param arch The architecture of the JVM. If it is null, it will be ignored.
	 * @param type The type of the JVM. If it is null, it will be ignored.
	 * @param initHeapSizeMb The initial heap size of the JVM in megabytes. If it is null, it will be ignored.
	 * @param maxHeapSizeMb The maximum heap size of the JVM in megabytes. If it is null, it will be ignored.
	 * @param stackSizeKb The maximum stack size of the JVM in kilobytes. If it is null, it will be ignored.
	 */
	public SimpleJavaProcessOptions(JVMArch arch, JVMType type, Integer initHeapSizeMb, Integer maxHeapSizeMb,
			Integer stackSizeKb) {
		if (initHeapSizeMb != null && initHeapSizeMb <= 0)
			throw new IllegalArgumentException("The initHeapSizeMb must be greater than 0.");
		if (maxHeapSizeMb != null && maxHeapSizeMb <= 0)
			throw new IllegalArgumentException("The maxHeapSizeMb must be greater than 0.");
		if (initHeapSizeMb > maxHeapSizeMb)
			throw new IllegalArgumentException("The initHeapSizeMb cannot be greater than the maxHeapSizeMb.");
		if (stackSizeKb != null && stackSizeKb <= 0)
			throw new IllegalArgumentException("The stackSize must be greater than 0.");
		this.arch = arch;
		this.type = type;
		this.initHeapSizeMb = initHeapSizeMb;
		this.maxHeapSizeMb = maxHeapSizeMb;
		this.stackSizeKb = stackSizeKb;
	}
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param initHeapSizeMb The initial heap size of the JVM in megabytes. If it is null, it will be ignored.
	 * @param maxHeapSizeMb The maximum heap size of the JVM in megabytes. If it is null, it will be ignored.
	 * @param stackSizeKb The maximum stack size of the JVM in kilobytes. If it is null, it will be ignored.
	 */
	public SimpleJavaProcessOptions(Integer initHeapSizeMb, Integer maxHeapSizeMb, Integer stackSizeKb) {
		this(null, null, initHeapSizeMb, maxHeapSizeMb, stackSizeKb);
	}
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param initHeapSizeMb The initial heap size of the JVM in megabytes. If it is null, it will be ignored.
	 * @param maxHeapSizeMb The maximum heap size of the JVM in megabytes. If it is null, it will be ignored.
	 */
	public SimpleJavaProcessOptions(Integer initHeapSizeMb, Integer maxHeapSizeMb) {
		this(initHeapSizeMb, maxHeapSizeMb, null);
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
	public Integer getInitHeapSizeMb() {
		return initHeapSizeMb;
	}
	@Override
	public Integer getMaxHeapSizeMb() {
		return maxHeapSizeMb;
	}
	@Override
	public Integer getStackSizeKb() {
		return stackSizeKb;
	}
	
}
