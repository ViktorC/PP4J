package net.viktorc.pp4j.impl.jp;

public class JavaOptions {

	private JVMArch arch;
	private JVMType type;
	private Integer initHeapSizeMb;
	private Integer maxHeapSizeMb;
	private Integer stackSizeKb;
	
	public JavaOptions(JVMArch arch, JVMType type, Integer initHeapSizeMb, Integer maxHeapSizeMb,
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
	public JavaOptions(Integer initHeapSizeMb, Integer maxHeapSizeMb, Integer stackSizeKb) {
		this(null, null, initHeapSizeMb, maxHeapSizeMb, stackSizeKb);
	}
	public JavaOptions(Integer initHeapSizeMb, Integer maxHeapSizeMb) {
		this(initHeapSizeMb, maxHeapSizeMb, null);
	}
	public JVMArch getArch() {
		return arch;
	}
	public JVMType getType() {
		return type;
	}
	public Integer getInitHeapSizeMb() {
		return initHeapSizeMb;
	}
	public Integer getMaxHeapSizeMb() {
		return maxHeapSizeMb;
	}
	public Integer getStackSizeKb() {
		return stackSizeKb;
	}
	
	public static enum JVMArch {
		BIT_32, BIT_64; 
	}
	public static enum JVMType {
		CLIENT, SERVER;
	}
	
}
