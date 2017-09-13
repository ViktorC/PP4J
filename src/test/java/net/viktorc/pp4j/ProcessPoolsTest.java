package net.viktorc.pp4j;

import java.io.File;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import net.viktorc.pp4j.api.JavaProcessPool;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.ProcessPool;
import net.viktorc.pp4j.impl.SimpleProcessManager;
import net.viktorc.pp4j.impl.StandardProcessPool;

/**
 * A simple test class for the {@link net.viktorc.pp4j.ProcessPools} class.
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
	private boolean test(ProcessPool pool, int minSize, int maxSize, int reserveSize, boolean verbose) {
		if (pool instanceof StandardProcessPool) {
			StandardProcessPool stdPool = (StandardProcessPool) pool;
			return stdPool.getMinSize() == minSize && stdPool.getMaxSize() == maxSize &&
					stdPool.getReserveSize() == reserveSize && stdPool.isVerbose() == verbose;
		}
		return false;
	}
	@Test
	public void test01() throws InterruptedException, URISyntaxException {
		ProcessPool pool = ProcessPools.newCustomProcessPool(new TestProcessManagerFactory(), 0, 5, 2);
		try {
			Assert.assertTrue(test(pool, 0, 5, 2, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test02() throws InterruptedException, URISyntaxException {
		ProcessPool pool = ProcessPools.newFixedProcessPool(new TestProcessManagerFactory(), 5);
		try {
			Assert.assertTrue(test(pool, 5, 5, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test03() throws InterruptedException, URISyntaxException {
		ProcessPool pool = ProcessPools.newCachedProcessPool(new TestProcessManagerFactory());
		try {
			Assert.assertTrue(test(pool, 0, Integer.MAX_VALUE, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test04() throws InterruptedException, URISyntaxException {
		ProcessPool pool = ProcessPools.newSingleProcessPool(new TestProcessManagerFactory());
		try {
			Assert.assertTrue(test(pool, 1, 1, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test05() throws InterruptedException, URISyntaxException {
		JavaProcessPool pool = ProcessPools.newCustomJavaProcessPool(0, 5, 2);
		try {
			Assert.assertTrue(test(pool, 0, 5, 2, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test06() throws InterruptedException, URISyntaxException {
		JavaProcessPool pool = ProcessPools.newFixedJavaProcessPool(5);
		try {
			Assert.assertTrue(test(pool, 5, 5, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test07() throws InterruptedException, URISyntaxException {
		JavaProcessPool pool = ProcessPools.newCachedJavaProcessPool();
		try {
			Assert.assertTrue(test(pool, 0, Integer.MAX_VALUE, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test08() throws InterruptedException, URISyntaxException {
		JavaProcessPool pool = ProcessPools.newSingleJavaProcessPool();
		try {
			Assert.assertTrue(test(pool, 1, 1, 0, false));
		} finally {
			pool.shutdown();
			pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	
	/**
	 * A simple test process manager factory for starting process managers for the test program.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private static class TestProcessManagerFactory implements ProcessManagerFactory {

		ProcessBuilder builder;
		
		/**
		 * Constructs an instance for creating process managers.
		 * 
		 * @throws URISyntaxException If the path to the test executable cannot be resolved.
		 */
		TestProcessManagerFactory() throws URISyntaxException {
			boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
			File programFile = new File(ClassLoader.getSystemClassLoader()
					.getResource(windows ? "win/test.exe" : "linux/test")
					.toURI().getPath());
			programFile.setExecutable(true);
			builder = new ProcessBuilder(programFile.getAbsolutePath());
		}
		@Override
		public ProcessManager newProcessManager() {
			return new SimpleProcessManager(builder);
		}
		
	}
	
}
