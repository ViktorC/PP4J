package net.viktorc.pp4j.impl.jp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.viktorc.pp4j.impl.ProcessException;
import net.viktorc.pp4j.impl.SimpleProcessManager;
import net.viktorc.pp4j.impl.jp.JavaOptions.JVMArch;
import net.viktorc.pp4j.impl.jp.JavaOptions.JVMType;

public class JavaProcessManager extends SimpleProcessManager {
	
	public JavaProcessManager(JavaOptions options, long keepAliveTime, Runnable onStartup) {
		super(createJavaProcessBuilder(options), keepAliveTime, exec -> {
			if (onStartup != null) {
				try {
					exec.execute(new RunnableJavaSubmission(onStartup, false));
				} catch (Exception e) {
					throw new ProcessException(e);
				}
			}
		});
	}
	public JavaProcessManager(JavaOptions options, long keepAliveTime) {
		this(options, keepAliveTime, null);
	}
	public JavaProcessManager(JavaOptions options, Runnable onStartup) {
		this(options, 0, onStartup);
	}
	public JavaProcessManager(long keepAliveTime, Runnable onStartup) {
		this(null, keepAliveTime, onStartup);
	}
	public JavaProcessManager(JavaOptions options) {
		this(options, 0);
	}
	public JavaProcessManager(long keepAliveTime) {
		this(keepAliveTime, null);
	}
	public JavaProcessManager(Runnable onStartup) {
		this(0, onStartup);
	}
	public JavaProcessManager() {
		this(0);
	}
	private static ProcessBuilder createJavaProcessBuilder(JavaOptions options) {
		String javaPath = System.getProperty("java.home") + File.separator + "bin" +
				File.separator + "java";
		String classPath = System.getProperty("java.class.path");
		String className = JavaProcess.class.getCanonicalName();
		List<String> javaOptions = new ArrayList<>();
		if (options != null) {
			JVMArch arch = options.getArch();
			JVMType type = options.getType();
			Integer initHeap = options.getInitHeapSizeMb();
			Integer maxHeap = options.getMaxHeapSizeMb();
			Integer stack = options.getStackSizeKb();
			if (arch != null)
				javaOptions.add(arch.equals(JVMArch.BIT_32) ? "-d32" : "-d64");
			if (type != null)
				javaOptions.add(type.equals(JVMType.CLIENT) ? "-client" : "-server");
			if (initHeap != null)
				javaOptions.add(String.format("-Xms%dm", initHeap));
			if (maxHeap != null)
				javaOptions.add(String.format("-Xmx%dm", maxHeap));
			if (stack != null)
				javaOptions.add(String.format("-Xss%dk", stack));
		}
		List<String> args = new ArrayList<>();
		args.add(javaPath);
		args.addAll(javaOptions);
		args.add("-cp");
		args.add(classPath);
		args.add(className);
		return new ProcessBuilder(args.toArray(new String[args.size()]));
	}

}
