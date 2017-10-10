package net.viktorc.pp4j.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

/**
 * A test class for comparing the performance of {@link net.viktorc.pp4j.impl.ProcessPoolExecutor} using a 
 * native executable wrapper program and {@link net.viktorc.pp4j.impl.JavaProcessPoolExecutor} using JNI.
 * 
 * @author Viktor Csomor
 *
 */
public class PPEVsJPPEJNITest {

	/**
	 * Executes the performance comparison according to the provided arguments.
	 * 
	 * @param minSize The minimum sizes of the two pools.
	 * @param maxSize The maximum sizes of the two pools.
	 * @param reserveSize The reserve sizes of the two pools.
	 * @param submissions The number of submissions to send to the pools.
	 * @param taskTime The number of seconds a single task should take.
	 * @param reuse Whether processes should be reused or killed after the execution of a submission.
	 * @throws Exception If something goes wrong.
	 */
	private void test(int minSize, int maxSize, int reserveSize, int submissions, int taskTime,
			boolean reuse) throws Exception {
		long start, nativeTime, javaTime;
		ProcessPoolExecutor nativePool = new ProcessPoolExecutor(TestUtils.createTestProcessManagerFactory(),
				minSize, maxSize, reserveSize, false);
		SimpleSubmission nativeSubmission = new SimpleSubmission(new SimpleCommand("process " + taskTime,
				(c, o) -> "ready".equals(o), (c, o) -> true));
		for (int i = 0; i < 5; i++)
			nativePool.submit(nativeSubmission, !reuse).get();
		List<Future<?>> futures = new ArrayList<>();
		start = System.nanoTime();
		for (int i = 0; i < submissions; i++)
			futures.add(nativePool.submit(nativeSubmission, !reuse));
		for (Future<?> f : futures)
			f.get();
		nativeTime = System.nanoTime() - start;
		nativePool.shutdown();
		nativePool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		JavaProcessPoolExecutor javaPool = new JavaProcessPoolExecutor(new SimpleJavaProcessOptions(1, 10, 512, 0),
				minSize, maxSize, reserveSize, reuse ? (Runnable & Serializable) () -> new JNIWrapper() :
				null, false);
		Runnable javaTask = (Runnable & Serializable) () -> (new JNIWrapper()).doStuff(taskTime);
		for (int i = 0; i < 5; i++)
			javaPool.submit(javaTask).get();
		futures = new ArrayList<>();
		start = System.nanoTime();
		for (int i = 0; i < submissions; i++)
			futures.add(javaPool.submit(javaTask, !reuse));
		for (Future<?> f : futures)
			f.get();
		javaTime = System.nanoTime() - start;
		javaPool.shutdown();
		javaPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		boolean success = nativeTime < javaTime;
		System.out.printf("Native time - %.3f%n : Java time - %.3f %s%n", (float) (((double) nativeTime)/1000000),
				(float) (((double) javaTime)/1000000), success ? "" : "FAIL");
		Assert.assertTrue(success);
	}
	@Test
	public void test01() throws Exception {
		System.out.printf(TestUtils.TEST_TITLE_FORMAT, 1);
		test(1, 1, 0, 10, 2, true);
	}
	@Test
	public void test02() throws Exception {
		System.out.printf(TestUtils.TEST_TITLE_FORMAT, 2);
		test(20, 50, 10, 50, 2, true);
	}
	
}
