package net.viktorc.pp4j.api.jpp;

/**
 * An interface for the definition of options for the "java" program to enable 
 * performance optimization for the forking of JVMs.
 * 
 * @author Viktor Csomor
 *
 */
public interface JavaProcessOptions {

	/**
	 * Returns the architecture the JVM should use, i.e. 32-bit or 64-bit.
	 * 
	 * @return The architecture of the JVM.
	 */
	JVMArch getArch();
	/**
	 * Returns the type of the JVM, i.e. client or server.
	 * 
	 * @return The type of the JVM.
	 */
	JVMType getType();
	/**
	 * Returns the minimum and hence initial heap size the JVM should use in megabytes.
	 * 
	 * @return The minimum heap size of the JVM in megabytes.
	 */
	Integer getInitHeapSizeMb();
	/**
	 * Returns the maximum heap size the JVM should use in megabytes.
	 * 
	 * @return The maximum heap size of the JVM in megabytes.
	 */
	Integer getMaxHeapSizeMb();
	/**
	 * The max stack size the JVM should use in kilobytes.
	 * 
	 * @return The max stack size of the JVM in kilobytes.
	 */
	Integer getStackSizeKb();

	/**
	 * The definitions of the different possible JVM architectures.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	public static enum JVMArch {
		BIT_32, BIT_64; 
	}
	
	/**
	 * The definitions of the possible JVM types.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	public static enum JVMType {
		CLIENT, SERVER;
	}
	
}