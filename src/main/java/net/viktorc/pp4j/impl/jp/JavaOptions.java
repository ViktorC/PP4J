package net.viktorc.pp4j.impl.jp;

public class JavaOptions {

	private JVMArch arch;
	private JVMType type;
	private Integer initHeapSize;
	private Integer maxHeapSize;
	private Integer stackSize;
	
	public JavaOptions(JVMArch arch, JVMType type, Integer initHeapSize, Integer maxHeapSize, Integer stackSize) {
		this.arch = arch;
		this.type = type;
		this.initHeapSize = initHeapSize;
		this.maxHeapSize = maxHeapSize;
		this.stackSize = stackSize;
	}
	public JavaOptions(Integer initHeapSize, Integer maxHeapSize, Integer stackSize) {
		this(null, null, initHeapSize, maxHeapSize, stackSize);
	}
	public JavaOptions(Integer initHeapSize, Integer maxHeapSize) {
		this(initHeapSize, maxHeapSize, null);
	}
	public JVMArch getArch() {
		return arch;
	}
	public JVMType getType() {
		return type;
	}
	public Integer getInitHeapSize() {
		return initHeapSize;
	}
	public Integer getMaxHeapSize() {
		return maxHeapSize;
	}
	public Integer getStackSize() {
		return stackSize;
	}
	
	public static enum JVMArch {
		BIT_32, BIT_64; 
	}
	public static enum JVMType {
		CLIENT, SERVER;
	}
	
}
