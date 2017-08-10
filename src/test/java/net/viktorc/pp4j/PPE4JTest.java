package net.viktorc.pp4j;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.ProcessExecutor;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.ProcessPool;
import net.viktorc.pp4j.api.Submission;
import net.viktorc.pp4j.impl.AbstractProcessManager;
import net.viktorc.pp4j.impl.ProcessException;
import net.viktorc.pp4j.impl.SimpleCommand;
import net.viktorc.pp4j.impl.StandardProcessPool;
import net.viktorc.pp4j.impl.SimpleSubmission;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A test class for the PP4J project.
 * 
 * @author Viktor Csomor
 *
 */
public class PPE4JTest {
	
	@Rule
	public final ExpectedException exceptionRule = ExpectedException.none();
	
	private final String programLocation;
	
	/**
	 * Resolves the path to the test program and ensures that it is executable.
	 * 
	 * @throws URISyntaxException If the path to the test program cannot be resolved.
	 */
	public PPE4JTest() throws URISyntaxException {
		// Support testing on Linux and Windows.
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		programLocation = new File(ClassLoader.getSystemClassLoader()
				.getResource(windows ? "win/test.exe" : "linux/test")
				.toURI().getPath()).getAbsolutePath();
		File file = new File(programLocation);
		file.setExecutable(true);
	}
	/**
	 * Performs some basic checks on the pool concerning its size and other parameters.
	 * 
	 * @param pool The pool to check.
	 * @param minPoolSize The minimum pool size.
	 * @param maxPoolSize The maximum pool size.
	 * @param reserveSize The process reserve size.
	 * @param verbose Whether the events relating to the management of the pool should be logged to the console.
	 */
	private void checkPool(ProcessPool pool, int minPoolSize, int maxPoolSize, int reserveSize, boolean verbose) {
		// Basic, implementation-specific pool state checks.
		if (pool instanceof StandardProcessPool) {
			StandardProcessPool stdProcPool = (StandardProcessPool) pool;
			assert minPoolSize == stdProcPool.getMinSize() : "Different min pool sizes: " + minPoolSize + " and " +
					stdProcPool.getMinSize() + ".";
			assert maxPoolSize == stdProcPool.getMaxSize() : "Different max pool sizes: " + maxPoolSize + " and " +
					stdProcPool.getMaxSize() + ".";
			assert reserveSize == stdProcPool.getReserveSize() : "Different reserve sizes: " + reserveSize + " and " +
					stdProcPool.getReserveSize() + ".";
			assert verbose == stdProcPool.isVerbose() : "Different verbosity: " + verbose + " and " +
					stdProcPool.isVerbose() + ".";
			assert stdProcPool.getNumOfQueuedSubmissions() == 0 : "Non-zero number of queued submissions on startup: " +
					stdProcPool.getNumOfQueuedSubmissions() + ".";
			assert stdProcPool.getNumOfExecutingSubmissions() == 0 : "Non-zero number of executing submissions on " +
					"startup: " + stdProcPool.getNumOfExecutingSubmissions() + ".";
			assert stdProcPool.getNumOfProcesses() == Math.max(minPoolSize, reserveSize) : "Unexpected number of " +
					"total processes: " + stdProcPool.getNumOfProcesses() + " instead of " +
					Math.max(minPoolSize, reserveSize) + ".";
		} else {
			// Other implementations of the ProcessPool interface.
		}
	}
	/**
	 * Creates a custom test process pool according to the specified parameters.
	 * 
	 * @param minPoolSize The minimum pool size.
	 * @param maxPoolSize The maximum pool size.
	 * @param reserveSize The process reserve size.
	 * @param keepAliveTime The time after which idled processes are killed.
	 * @param verifyStartup Whether the startup should be verified.
	 * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
	 * @param verbose Whether the events relating to the management of the pool should be logged to the console.
	 * @param throwStartupException Whether a process exception should be thrown on startup.
	 * @return The process pool created according to the specified parameters.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
	 */
	public ProcessPool getCustomPool(int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime,
			boolean verifyStartup, boolean manuallyTerminate, boolean verbose, boolean throwStartupException)
					throws InterruptedException {
		TestProcessManagerFactory managerFactory = new TestProcessManagerFactory(keepAliveTime, verifyStartup,
				manuallyTerminate, throwStartupException);
		ProcessPool pool = verbose ? ProcessPools.newCustomProcessPool(managerFactory, minPoolSize, maxPoolSize,
				reserveSize, verbose) : ProcessPools.newCustomProcessPool(managerFactory, minPoolSize, maxPoolSize,
				reserveSize);
		checkPool(pool, minPoolSize, maxPoolSize, reserveSize, verbose);
		return pool;
	}
	/**
	 * Creates a fixed-size test process pool according to the specified parameters.
	 * 
	 * @param poolSize The pool size.
	 * @param keepAliveTime The time after which idled processes are killed.
	 * @param verifyStartup Whether the startup should be verified.
	 * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
	 * @param verbose Whether the events relating to the management of the pool should be logged to the console.
	 * @param throwStartupException Whether a process exception should be thrown on startup.
	 * @return The process pool created according to the specified parameters.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
	 */
	public ProcessPool getFixedPool(int poolSize, long keepAliveTime, boolean verifyStartup,
			boolean manuallyTerminate, boolean verbose, boolean throwStartupException)
					throws InterruptedException {
		TestProcessManagerFactory managerFactory = new TestProcessManagerFactory(keepAliveTime, verifyStartup,
				manuallyTerminate, throwStartupException);
		ProcessPool pool = verbose ? ProcessPools.newFixedProcessPool(managerFactory, poolSize,
				verbose) : ProcessPools.newFixedProcessPool(managerFactory, poolSize);
		checkPool(pool, poolSize, poolSize, 0, verbose);
		return pool;
	}
	/**
	 * Creates a cached test process pool according to the specified parameters.
	 * 
	 * @param keepAliveTime The time after which idled processes are killed.
	 * @param verifyStartup Whether the startup should be verified.
	 * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
	 * @param verbose Whether the events relating to the management of the pool should be logged to the console.
	 * @param throwStartupException Whether a process exception should be thrown on startup.
	 * @return The process pool created according to the specified parameters.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
	 */
	public ProcessPool getCachedPool(long keepAliveTime, boolean verifyStartup, boolean manuallyTerminate,
			boolean verbose, boolean throwStartupException) throws InterruptedException {
		TestProcessManagerFactory managerFactory = new TestProcessManagerFactory(keepAliveTime, verifyStartup,
				manuallyTerminate, throwStartupException);
		ProcessPool pool = verbose ? ProcessPools.newCachedProcessPool(managerFactory, verbose) :
				ProcessPools.newCachedProcessPool(managerFactory);
		checkPool(pool, 0, Integer.MAX_VALUE, 0, verbose);
		return pool;
	}
	/**
	 * Returns a single process executor according to the specified parameters.
	 * 
	 * @param keepAliveTime The time after which idled processes are killed.
	 * @param verifyStartup Whether the startup should be verified.
	 * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
	 * @param verbose Whether the events relating to the management of the pool should be logged to the console.
	 * @param throwStartupException Whether a process exception should be thrown on startup.
	 * @return The process pool created according to the specified parameters.
	 * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
	 */
	public ProcessPool getSinglePool(long keepAliveTime, boolean verifyStartup,
			boolean manuallyTerminate, boolean verbose, boolean throwStartupException) throws InterruptedException {
		TestProcessManagerFactory managerFactory = new TestProcessManagerFactory(keepAliveTime, verifyStartup,
				manuallyTerminate, throwStartupException);
		ProcessPool pool = verbose ? ProcessPools.newSingleProcessPool(managerFactory, verbose) :
				ProcessPools.newSingleProcessPool(managerFactory);
		checkPool(pool, 1, 1, 0, false);
		return pool;
	}
	/**
	 * Submits the specified number of commands with the specified frequency to a the test process pool 
	 * corresponding to the specified parameters and determines whether it performs well enough based on 
	 * the number of processed requests and the times it took to process them.
	 * 
	 * @param processPool The process pool executor to test.
	 * @param reuse Whether a process can execute multiple commands.
	 * @param procTimes The times for which the test processes should "execute" commands. Each element 
	 * stands for a command. If there are multiple elements, the commands will be chained.
	 * @param requests The number of commands to submit.
	 * @param timeSpan The number of milliseconds in which the uniformly distributed requests should be 
	 * submitted.
	 * @param throwExecutionException Whether a process exception should be thrown by the submitted 
	 * command.
	 * @param cancelTime The number of milliseconds after which the futures should be cancelled. If it 
	 * is 0 or less, the futures are not cancelled.
	 * @param forcedCancel If the command should be interrupted if it is already being processed. If 
	 * <code>cancelTime</code> is not greater than 0, it has no effect.
	 * @param earlyClose Whether the pool should be closed right after the submission of the commands.
	 * @param waitTimeout The number of milliseconds for which the submissions are waited on.
	 * @param lowerBound The minimum acceptable submission execution time.
	 * @param upperBound The maximum acceptable submission execution time.
	 * @return Whether the test passes.
	 * @throws Exception If the process pool cannot be created.
	 */
	private boolean perfTest(ProcessPool processPool, boolean reuse, int[] procTimes,
			int requests, long timeSpan, boolean throwExecutionException, long cancelTime,
			boolean forcedCancel, boolean earlyClose, long waitTimeout, long lowerBound, long upperBound)
					throws Exception {
		List<Long> times = new ArrayList<>();
		List<Future<?>> futures = new ArrayList<>(requests);
		long frequency = requests > 0 ? timeSpan/requests : 0;
		for (int i = 0; i < requests; i++) {
			if (i != 0 && frequency > 0) {
				try {
					Thread.sleep(frequency);
				} catch (InterruptedException e) {
					return false;
				}
			}
			List<Command> commands;
			if (procTimes == null)
				commands = null;
			else {
				commands = new ArrayList<>();
				for (int procTime : procTimes)
					commands.add(new SimpleCommand("process " + procTime, (c, o) -> {
								if ("ready".equals(o)) {
									// Output line caching check.
									assert c.getStandardOutLines().size() == procTime &&
											c.getErrorOutLines().size() == 0 :
											"Unexpected numbers of output lines: " +
											c.getStandardOutLines().size() + " instead of " +
											procTime + " and " + c.getErrorOutLines().size() + 
											" instead of " + 0 + ".";
									String expectedStdOutput = Arrays.stream(new String[procTime - 1])
											.map(s -> "in progress").reduce("", (s1, s2) -> (s1 +
											"\n" + s2).trim()) + "\nready";
									assert expectedStdOutput.equals(c.getJointStandardOutLines()) :
											"Wrongly captured standard output. Expected: \"" +
											expectedStdOutput + "\"" + System.lineSeparator() +
											"Actual: \"" + c.getJointStandardOutLines() + "\"";
									assert "".equals(c.getJointErrorOutLines()) : "Wrongly " +
											"captured error output. Expected: \"\"" + System
											.lineSeparator() + "Actual: \"" + c
											.getJointErrorOutLines() + "\"";
									c.reset();
									return true;
								}
								return false;
							}, (c, o) -> true) {
								
								@Override
								public boolean generatesOutput() {
									if (throwExecutionException)
										throw new ProcessException("Test execution exception.");
									return super.generatesOutput();
								}
							});
			}
			int index = i;
			Submission submission = new SimpleSubmission(commands, !reuse) {
				
				@Override
				public void onFinishedProcessing() {
					times.set(index, System.nanoTime() - times.get(index));
				}
			};
			times.add(System.nanoTime());
			futures.add(processPool.submit(submission));
		}
		if (cancelTime > 0) {
			Thread.sleep(cancelTime);
			for (Future<?> future : futures)
				future.cancel(forcedCancel);
		} else if (earlyClose)
			processPool.shutdown();
		for (int i = 0; i < futures.size(); i++) {
			Future<?> future = futures.get(i);
			if (waitTimeout > 0)
				future.get(waitTimeout, TimeUnit.MILLISECONDS);
			else
				future.get();
		}
		if (!earlyClose)
			processPool.shutdown();
		String testArgMessage = "keepAliveTime: %d; throwStartupError: %s; verifyStartup: %s;%n" +
				"manuallyTerminate: %s; reuse: %s; procTimes: %s; requests: %d; timeSpan: %d;%n" +
				"throwExecutionError: %s; cancelTime: %d; forcedCancel: %s; earlyClose: %s;%n" +
				"waitTimeout: %.3f; lowerBound: %.3f; upperBound: %.3f;%n";
		TestProcessManagerFactory procManagerFactory = (TestProcessManagerFactory) processPool
				.getProcessManagerFactory();
		Object[] args = new Object[] { procManagerFactory.keepAliveTime, Boolean.toString(procManagerFactory
				.throwStartupException), Boolean.toString(procManagerFactory.verifyStartup),
				Boolean.toString(procManagerFactory.manuallyTerminate), Boolean.toString(reuse),
				Arrays.toString(procTimes), requests, timeSpan, Boolean.toString(throwExecutionException),
				cancelTime, Boolean.toString(forcedCancel), Boolean.toString(earlyClose), (float)
				(((double) waitTimeout)/1000), (float) (((double) lowerBound)/1000), (float) (((double)
				upperBound)/1000) };
		// Implementation specific information.
		if (processPool instanceof StandardProcessPool) {
			StandardProcessPool procPool = (StandardProcessPool) processPool;
			testArgMessage = "minPoolSize: %d; maxPoolSize: %d; reserveSize: %d; verbose: %s;%n" +
					testArgMessage;
			Object[] additionalArgs = new Object[] { procPool.getMinSize(), procPool.getMaxSize(),
					procPool.getReserveSize(), Boolean.toString(procPool.isVerbose()) };
			Object[] extendedArgs = new Object[args.length + additionalArgs.length];
			System.arraycopy(additionalArgs, 0, extendedArgs, 0, additionalArgs.length);
			System.arraycopy(args, 0, extendedArgs, additionalArgs.length, args.length);
			args = extendedArgs;
		} else {
			// Other implementations of the ProcessPool interface.
		}
		System.out.println("------------------------------" +
				"---------------------------------------------" +
				"---------------");
		System.out.printf(testArgMessage, args);
		System.out.println("------------------------------" +
				"---------------------------------------------" +
				"---------------");
		if (times.size() == requests) {
			boolean pass = true;
			for (Long time : times) {
				// Convert nanoseconds to milliseconds.
				time = Math.round(((double) time/1000000));
				boolean fail = time == null || time > upperBound || time < lowerBound;
				if (fail)
					pass = false;
				System.out.printf("Time: %.3f %s%n", (float) (((double) time)/1000), fail ? "FAIL" : "");
			}
			return pass;
		} else {
			System.out.printf("Some requests were not processed %d/%d%n", times.size(), requests);
			return false;
		}
	}
	// Exception testing.
	@Test
	public void test01() throws Exception {
		System.out.println(System.lineSeparator() + "Test 1");
		exceptionRule.expect(IllegalArgumentException.class);
		ProcessPool pool = getCustomPool(-1, 5, 0, 0, false, false, false, false);
		perfTest(pool, false, new int[] { 5 }, 100, 10000, false, 0, false, false, 0, 4995, 6200);
	}
	@Test
	public void test02() throws Exception {
		System.out.println(System.lineSeparator() + "Test 2");
		exceptionRule.expect(IllegalArgumentException.class);
		getFixedPool(0, 0, false, false, false, false);
	}
	@Test
	public void test03() throws Exception {
		System.out.println(System.lineSeparator() + "Test 3");
		exceptionRule.expect(IllegalArgumentException.class);
		getCustomPool(10, 5, 0, 0, false, false, false, false);
	}
	@Test
	public void test04() throws Exception {
		System.out.println(System.lineSeparator() + "Test 4");
		exceptionRule.expect(IllegalArgumentException.class);
		getCustomPool(10, 12, -1, 0, false, false, false, false);
	}
	@Test
	public void test05() throws Exception {
		System.out.println(System.lineSeparator() + "Test 5");
		exceptionRule.expect(IllegalArgumentException.class);
		getCustomPool(10, 12, 15, 0, false, false, false, false);
	}
	@Test
	public void test06() throws Exception {
		System.out.println(System.lineSeparator() + "Test 6");
		ProcessPool pool = getCachedPool(0, false, false, false, false);
		exceptionRule.expect(IllegalArgumentException.class);
		perfTest(pool, false, null, 100, 10000, false, 0, false, false, 0, 4995, 6200);
	}
	@Test
	public void test07() throws Exception {
		System.out.println(System.lineSeparator() + "Test 7");
		ProcessPool pool = getCachedPool(0, false, false, false, false);
		exceptionRule.expect(IllegalArgumentException.class);
		perfTest(pool, false, new int[0], 100, 10000, false, 0, false, false, 0, 4995, 6200);
	}
	// Performance testing.
	@Test
	public void test08() throws Exception {
		System.out.println(System.lineSeparator() + "Test 8");
		ProcessPool pool = getCustomPool(0, 100, 0, 0, true, false, false, false);
		Assert.assertTrue(perfTest(pool, true, new int[] { 5 }, 100, 10000, false, 0, false, false, 0, 4995,
				6250));
	}
	@Test
	public void test09() throws Exception {
		System.out.println(System.lineSeparator() + "Test 9");
		ProcessPool pool = getCustomPool(50, 150, 20, 0, false, false, false, false);
		Assert.assertTrue(perfTest(pool, true, new int[] { 5 }, 100, 5000, false, 0, false, false, 0, 4995,
				5100));
	}
	@Test
	public void test10() throws Exception {
		System.out.println(System.lineSeparator() + "Test 10");
		ProcessPool pool = getCustomPool(10, 25, 5, 15000, true, false, false, false);
		Assert.assertTrue(perfTest(pool, true, new int[] { 5 }, 20, 10000, false, 0, false, false, 0, 4995,
				5100));
	}
	@Test
	public void test11() throws Exception {
		System.out.println(System.lineSeparator() + "Test 11");
		ProcessPool pool = getCustomPool(50, 150, 20, 0, false, true, false, false);
		Assert.assertTrue(perfTest(pool, true, new int[] { 5 }, 100, 5000, false, 0, false, false, 0, 4995,
				5100));
	}
	@Test
	public void test12() throws Exception {
		System.out.println(System.lineSeparator() + "Test 12");
		ProcessPool pool = getCustomPool(10, 50, 5, 15000, true, false, false, false);
		Assert.assertTrue(perfTest(pool, true, new int[] { 5, 3, 2 }, 50, 10000, false, 0, false, false, 0,
				9995, 10340));
	}
	@Test
	public void test13() throws Exception {
		System.out.println(System.lineSeparator() + "Test 13");
		ProcessPool pool = getCustomPool(100, 250, 20, 0, true, true, false, false);
		Assert.assertTrue(perfTest(pool, true, new int[] { 5 }, 800, 20000, false, 0, false, false, 0, 4995,
				6000));
	}
	@Test
	public void test14() throws Exception {
		System.out.println(System.lineSeparator() + "Test 14");
		ProcessPool pool = getCustomPool(0, 100, 0, 0, false, false, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 100, 10000, false, 0, false, false, 0, 4995,
				6850));
	}
	@Test
	public void test15() throws Exception {
		System.out.println(System.lineSeparator() + "Test 15");
		ProcessPool pool = getCustomPool(50, 150, 10, 0, true, false, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 100, 5000, false, 0, false, false, 0, 4995,
				5620));
	}
	@Test
	public void test16() throws Exception {
		System.out.println(System.lineSeparator() + "Test 16");
		ProcessPool pool = getCustomPool(10, 25, 5, 15000, false, true, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 20, 10000, false, 0, false, false, 0, 4995,
				5100));
	}
	@Test
	public void test17() throws Exception {
		System.out.println(System.lineSeparator() + "Test 17");
		ProcessPool pool = getCustomPool(50, 150, 10, 0, true, true, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 100, 5000, false, 0, false, false, 0, 4995,
				5600));
	}
	@Test
	public void test18() throws Exception {
		System.out.println(System.lineSeparator() + "Test 18");
		ProcessPool pool = getCustomPool(10, 50, 5, 15000, false, false, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5, 3, 2 }, 50, 10000, false, 0, false, false, 0,
				9995, 10350));
	}
	@Test
	public void test19() throws Exception {
		System.out.println(System.lineSeparator() + "Test 19");
		ProcessPool pool = getCustomPool(50, 250, 20, 0, true, true, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 800, 20000, false, 0, false, false, 0, 4995,
				6000));
	}
	// Keep alive timer and logging test.
	@Test
	public void test20() throws Exception {
		System.out.println(System.lineSeparator() + "Test 20");
		ProcessPool pool = getCustomPool(20, 40, 4, 250, true, true, true, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 50, 5000, false, 0, false, false, 0, 4995,
				8200));
	}
	// Cancellation testing.
	@Test
	public void test21() throws Exception {
		System.out.println(System.lineSeparator() + "Test 21");
		ProcessPool pool = getCustomPool(10, 30, 5, 0, true, true, true, false);
		exceptionRule.expect(CancellationException.class);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 20, 0, false, 2500, true, false, 0, 2495,
				2520));
	}
	@Test
	public void test22() throws Exception {
		System.out.println(System.lineSeparator() + "Test 22");
		ProcessPool pool = getFixedPool(20, 0, false, false, true, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 20, 0, false, 2500, false, false, 0, 4995,
				5120));
	}
	@Test
	public void test23() throws Exception {
		System.out.println(System.lineSeparator() + "Test 23");
		ProcessPool pool = getCustomPool(10, 30, 5, 0, true, true, false, false);
		exceptionRule.expect(CancellationException.class);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5, 5, 3 }, 20, 0, false, 2500, true, false, 0,
				2495, 2520));
	}
	@Test
	public void test24() throws Exception {
		System.out.println(System.lineSeparator() + "Test 24");
		ProcessPool pool = getFixedPool(20, 0, true, true, true, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5, 5, 3 }, 20, 0, false, 3000, false, false, 0,
				12995, 13120));
	}
	// Early shutdown testing.
	@Test
	public void test25() throws Exception {
		System.out.println(System.lineSeparator() + "Test 25");
		ProcessPool pool = getFixedPool(100, 5000, true, false, false, false);
		exceptionRule.expect(ExecutionException.class);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 100, 0, false, 0, false, true, 0, 0, 0));
	}
	// Interrupted construction testing.
	@Test
	public void test26() throws Exception {
		System.out.println(System.lineSeparator() + "Test 26");
		ProcessPool pool = null;
		Thread thread = Thread.currentThread();
		Timer timer = new Timer();
		exceptionRule.expect(InterruptedException.class);
		try {
			timer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					thread.interrupt();
				}
			}, 500);
			pool = getCustomPool(20, 30, 0, 0, false, false, true, false);
		} finally {
			if (pool != null)
				pool.shutdown();
		}
	}
	// Single process pool performance testing.
	@Test
	public void test27() throws Exception {
		System.out.println(System.lineSeparator() + "Test 27");
		ProcessPool pool = getSinglePool(20000, true, true, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 5, 30000, false, 0, false, false, 0, 4995,
				5250));
	}
	@Test
	public void test28() throws Exception {
		System.out.println(System.lineSeparator() + "Test 28");
		ProcessPool pool = getSinglePool(0, true, false, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 5, 20000, false, 0, false, false, 0, 4995,
				13250));
	}
	// Fixed size process pool performance testing.
	@Test
	public void test29() throws Exception {
		System.out.println(System.lineSeparator() + "Test 29");
		ProcessPool pool = getFixedPool(20, 0, true, false, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 20, 5000, false, 0, false, false, 0, 4995,
				5200));
	}
	@Test
	public void test30() throws Exception {
		System.out.println(System.lineSeparator() + "Test 30");
		ProcessPool pool = getFixedPool(20, 0, true, false, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 40, 10000, false, 0, false, false, 0, 4995, 
				6200));
	}
	// Wait with timeout testing.
	@Test
	public void test31() throws Exception {
		System.out.println(System.lineSeparator() + "Test 31");
		ProcessPool pool = getCustomPool(20, 50, 10, 0, true, true, false, false);
		exceptionRule.expect(TimeoutException.class);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 40, 0, false, 0, false, false, 3000, 3000,
				3000));
	}
	@Test
	public void test32() throws Exception {
		System.out.println(System.lineSeparator() + "Test 32");
		ProcessPool pool = getCustomPool(20, 50, 0, 0, true, true, false, false);
		exceptionRule.expect(TimeoutException.class);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5, 5 }, 40, 0, false, 0, false, false, 5000,
				5000, 5000));
	}
	// Wait with timeout plus cancellation testing.
	@Test
	public void test33() throws Exception {
		System.out.println(System.lineSeparator() + "Test 33");
		ProcessPool pool = getCustomPool(10, 30, 5, 0, true, true, false, false);
		exceptionRule.expect(CancellationException.class);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 20, 0, false, 2500, true, false, 5000, 2495,
				2520));
	}
	@Test
	public void test34() throws Exception {
		System.out.println(System.lineSeparator() + "Test 34");
		ProcessPool pool = getFixedPool(20, 0, false, false, false, false);
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 20, 0, false, 2500, false, false, 3000, 4995,
				5120));
	}
	// Execution exception testing.
	@Test
	public void test35() throws Exception {
		System.out.println(System.lineSeparator() + "Test 35");
		ProcessPool pool = getCachedPool(0, false, false, false, false);
		exceptionRule.expect(ExecutionException.class);
		exceptionRule.expectMessage("Test execution exception.");
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 20, 4000, true, 0, false, false, 0, 4995,
				6200));
	}
	@Test
	public void test36() throws Exception {
		System.out.println(System.lineSeparator() + "Test 36");
		ProcessPool pool = getCachedPool(0, false, false, false, false);
		exceptionRule.expect(ExecutionException.class);
		exceptionRule.expectMessage("Test execution exception.");
		Assert.assertTrue(perfTest(pool, false, new int[] { 5 }, 20, 4000, true, 0, false, false, 1000, 4995,
				6200));
	}
	// Startup exception testing.
	@Test
	public void test37() throws Exception {
		System.out.println(System.lineSeparator() + "Test 37");
		ProcessPools.newFixedProcessPool(new TestProcessManagerFactory(0, false, false, true), 20);
		Assert.assertTrue(true);
	}
	
	
	/**
	 * An implementation of the {@link net.viktorc.pp4j.api.ProcessManagerFactory} interface for testing purposes.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private class TestProcessManagerFactory implements ProcessManagerFactory {
		
		final long keepAliveTime;
		final boolean verifyStartup;
		final boolean manuallyTerminate;
		final boolean throwStartupException;
		
		/**
		 * Constructs an instance according to the specified parameters.
		 * 
		 * @param keepAliveTime
		 * @param verifyStartup Whether the startup should be verified.
		 * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
		 * @param throwStartupException Whether a process exception should be thrown on startup.
		 */
		TestProcessManagerFactory(long keepAliveTime, boolean verifyStartup, boolean manuallyTerminate,
				boolean throwStartupException) {
			this.keepAliveTime = keepAliveTime;
			this.verifyStartup = verifyStartup;
			this.manuallyTerminate = manuallyTerminate;
			this.throwStartupException = throwStartupException;
		}
		@Override
		public ProcessManager newProcessManager() {
			return new AbstractProcessManager(new ProcessBuilder(programLocation), keepAliveTime) {
				
				@Override
				public boolean isStartedUp(String output, boolean standard) {
					if (throwStartupException)
						throw new ProcessException("Test startup exception.");
					return !verifyStartup || (standard && "hi".equals(output));
				}
				@Override
				public void onStartup(ProcessExecutor executor) {
					try {
						executor.execute(new SimpleSubmission(new SimpleCommand("start",
								(c, o) -> "ok".equals(o), (c, o) -> true), false));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				@Override
				public boolean terminateGracefully(ProcessExecutor executor) {
					if (manuallyTerminate) {
						try {
							AtomicBoolean success = new AtomicBoolean(true);
							if (executor.execute(new SimpleSubmission(new SimpleCommand("stop",
									(c, o) -> "bye".equals(o),
									(c, o) -> {
										success.set(false);
										return true;
									}), false)))
								return success.get();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					return false;
				}
				@Override
				public void onTermination(int resultCode, long lifeTime) { }
			};
		}
		
	}
	
}