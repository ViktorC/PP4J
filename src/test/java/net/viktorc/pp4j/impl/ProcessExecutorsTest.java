package net.viktorc.pp4j.impl;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import net.viktorc.pp4j.api.JavaProcessPool;
import net.viktorc.pp4j.api.ProcessPool;
import net.viktorc.pp4j.impl.StandardProcessPool;

/**
 * A simple test class for the {@link net.viktorc.pp4j.impl.ProcessPools} class.
 * 
 * @author Viktor Csomor
 *
 */
public class ProcessPoolsTest {

	/**
	 * Tests if the specified properties match those of the provided process pool.
	 * 
	 * @param pool The process pool to check.
	 * @param minSize The expected minimum size of the pool.
	 * @param maxSize The expected maximum size of the pool.
	 * @param reserveSize The expected reserve size of the pool.
	 * @param verbose The expected verbosity of the pool.
	 * @return Whether the specified values match those of the properties of the pool.
	 */
	private boolean test(ProcessPool pool, int minSize, int maxSize, int reserveSize,
			boolean verbose) {
		if (pool instanceof StandardProcessPool) {
			StandardProcessPool stdPool = (StandardProcessPool) pool;
			return stdPool.getMinSize() == minSize && stdPool.getMaxSize() == maxSize &&
					stdPool.getReserveSize() == reserveSize && stdPool.isVerbose() == verbose;
		}
		return false;
	}
	@Test
	public void test01() throws InterruptedException, URISyntaxException {
		ProcessPool pool = ProcessPools.newCustomProcessPool(TestUtils
				.createTestProcessManagerFactory(), 0, 5, 2);
		try {
			Assert.assertTrue(test(pool, 0, 5, 2, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test02() throws InterruptedException, URISyntaxException {
		ProcessPool pool = ProcessPools.newFixedProcessPool(TestUtils
				.createTestProcessManagerFactory(), 5);
		try {
			Assert.assertTrue(test(pool, 5, 5, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test03() throws InterruptedException, URISyntaxException {
		ProcessPool pool = ProcessPools.newCachedProcessPool(TestUtils
				.createTestProcessManagerFactory());
		try {
			Assert.assertTrue(test(pool, 0, Integer.MAX_VALUE, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test04() throws InterruptedException, URISyntaxException {
		ProcessPool pool = ProcessPools.newSingleProcessPool(TestUtils
				.createTestProcessManagerFactory());
		try {
			Assert.assertTrue(test(pool, 1, 1, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test05() throws InterruptedException {
		JavaProcessPool pool = ProcessPools.newCustomJavaProcessPool(0, 5, 2);
		try {
			Assert.assertTrue(test(pool, 0, 5, 2, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test06() throws InterruptedException {
		JavaProcessPool pool = ProcessPools.newFixedJavaProcessPool(5);
		try {
			Assert.assertTrue(test(pool, 5, 5, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test07() throws InterruptedException {
		JavaProcessPool pool = ProcessPools.newCachedJavaProcessPool();
		try {
			Assert.assertTrue(test(pool, 0, Integer.MAX_VALUE, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test08() throws InterruptedException {
		JavaProcessPool pool = ProcessPools.newSingleJavaProcessPool();
		try {
			Assert.assertTrue(test(pool, 1, 1, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	
}
