package net.viktorc.pspp;

import org.junit.Assert;
import org.junit.Test;

import net.viktorc.pspp.PSPPool;
import net.viktorc.pspp.ProcessShell;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A test class for the process pool.
 * 
 * @author A6714
 *
 */
public class PSPPoolTest {
	
	/**
	 * Creates a test process pool according to the specified parameters.
	 * 
	 * @param minPoolSize The minimum pool size.
	 * @param maxPoolSize The maximum pool size.
	 * @param reserveSize The process reserve size.
	 * @param keepAliveTime The time after which idled processes are killed.
	 * @param verifyStartup Whether the startup should be verified.
	 * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
	 */
	private PSPPool getPool(int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime, boolean verifyStartup,
			boolean manuallyTerminate) throws IllegalArgumentException, IOException, URISyntaxException {
		// The tests are either run locally on my Windows machine or through Travis CI on Linux.
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		String programLocation = new File(getClass().getResource(windows ? "win/test.exe" : "linux/test")
				.toURI().getPath()).getAbsolutePath();
		File file = new File(programLocation);
		// For testing on Travis CI
		file.setExecutable(true);
		PSPPool pool = new PSPPool(new AbstractProcessManager(new ProcessBuilder(programLocation)) {
			
			@Override
			public boolean startsUpInstantly() {
				return !verifyStartup;
			}
			@Override
			public boolean isStartedUp(String output, boolean standard) {
				return standard && "hi".equals(output);
			}
			@Override
			public void onStartup(ProcessShell manager) {
				try {
					manager.execute(new SimpleSubmission(new SimpleCommand("start",
							(c, o) -> "ok".equals(o), (c, o) -> true), false));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			@Override
			public boolean terminate(ProcessShell manager) {
				if (manuallyTerminate) {
					try {
						AtomicBoolean success = new AtomicBoolean(true);
						if (manager.execute(new SimpleSubmission(new SimpleCommand("stop", (c, o) -> "bye".equals(o),
								(c, o) -> {
									success.set(false);
									return true;
								}), false)))
							return success.get();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				return false;
			}
			@Override
			public void onTermination(int resultCode) {
				
			}
			
		}, minPoolSize, maxPoolSize, reserveSize, keepAliveTime);
		return pool;
	}
	/**
	 * Submits the specified number of commands with the specified frequency to a the test process pool 
	 * corresponding to the specified parameters and returns a list of the total execution times of the 
	 * commands.
	 * 
	 * @param minPoolSize The minimum pool size.
	 * @param maxPoolSize The maximum pool size.
	 * @param reserveSize The process reserve size.
	 * @param keepAliveTime The time after which idled processes are killed.
	 * @param verifyStartup Whether the startup should be verified.
	 * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
	 * @param reuse Whether a process can execute multiple commands.
	 * @param procTimes The times for which the test processes should "execute" commands. Each element 
	 * stands for a command. If there are multiple elements, the commands will be chained.
	 * @param requests The number of commands to submit.
	 * @param timeSpan The number of milliseconds in which the specified number of requests should be sent. 
	 * If it is 0 or less, they are all sent at once.
	 * @param cancelTime The number of milliseconds after which the futures should be cancelled. If it 
	 * is 0 or less, the futures are not cancelled.
	 * @return A list of the total execution times of the commands.
	 * @throws Exception
	 */
	private List<Long> testBase(int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime,
			boolean verifyStartup, boolean manuallyTerminate, boolean reuse, int[] procTimes, int requests,
			long timeSpan, long cancelTime) throws Exception {
		try (PSPPool procPool = getPool(minPoolSize, maxPoolSize, reserveSize, keepAliveTime, verifyStartup,
				manuallyTerminate)) {
			long frequency = requests > 0 ? timeSpan/requests : 0;
			List<Future<Long>> futures = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				if (frequency > 0) {
					try {
						Thread.sleep(frequency);
					} catch (InterruptedException e) {
						return null;
					}
				}
				List<Command> commands = new ArrayList<>();
				for (int procTime : procTimes)
					commands.add(new SimpleCommand("process " + procTime, (c, o) -> "ready".equals(o), (c, o) -> true));
				futures.add(procPool.submit(new SimpleSubmission(commands, !reuse)));
			}
			if (cancelTime > 0) {
				Thread.sleep(cancelTime);
				for (Future<Long> future : futures)
					future.cancel(true);
			}
			List<Long> times = new ArrayList<>();
			for (Future<Long> future : futures) {
				try {
					times.add(future.get());
				} catch (CancellationException e) {
					times.add(cancelTime*1000000);
				}
			}
			procPool.close();
			return times;
		}
	}
	/**
	 * Submits the specified number of commands with the specified frequency to a the test process pool 
	 * corresponding to the specified parameters and determines whether it performs well enough based on 
	 * the number of processed requests and the times it took to process them.
	 * 
	 * @param testName The name of the test.
	 * @param minPoolSize The minimum pool size.
	 * @param maxPoolSize The maximum pool size.
	 * @param reserveSize The process reserve size.
	 * @param keepAliveTime The time after which idled processes are killed.
	 * @param verifyStartup Whether the startup should be verified.
	 * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
	 * @param reuse Whether a process can execute multiple commands.
	 * @param procTimes The times for which the test processes should "execute" commands. Each element 
	 * stands for a command. If there are multiple elements, the commands will be chained.
	 * @param requests The number of commands to submit.
	 * @param requestPerSe The number of commands to submit per second
	 * @param cancelTime The number of milliseconds after which the futures should be cancelled. If it 
	 * is 0 or less, the futures are not cancelled.
	 * @param failTime The duration from which on commands are considered to be executed too slowly and cause
	 * the test to fail.
	 * @return Whether the test passes.
	 */
	private boolean test(String testName, int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime,
			boolean verifyStartup, boolean manuallyTerminate, boolean reuse, int[] procTimes, int requests,
			long timeSpan, long cancelTime, long failTime) {
		try {
			List<Long> times = testBase(minPoolSize, maxPoolSize, reserveSize, keepAliveTime, verifyStartup,
					manuallyTerminate, reuse, procTimes, requests, timeSpan, cancelTime);
			System.out.println("\n" + testName);
			System.out.println("-------------------------------------------------------------------------------------");
			System.out.printf("minPoolSize: %d; maxPoolSize: %d; reserveSize: %d; keepAliveTime: %d;%n" +
					"verifyStartup: %s; manuallyTerminate: %s; reuse: %s; procTimes: %s;%n" +
					"requests: %d; timeSpan: %d; cancelTime: %d; failTime: %.3f%n",
					minPoolSize, maxPoolSize, reserveSize, keepAliveTime, Boolean.toString(verifyStartup),
					Boolean.toString(manuallyTerminate), Boolean.toString(reuse), Arrays.toString(procTimes),
					requests, timeSpan, cancelTime, (float) (((double) failTime)/1000));
			System.out.println("-------------------------------------------------------------------------------------");
			if (times.size() == requests) {
				boolean pass = true;
				for (Long time : times) {
					boolean fail = time == null || time/1000000 >= failTime;
					if (fail)
						pass = false;
					System.out.printf("Time: %.3f %s%n", (float) (((double) time)/1000000000), fail ? "FAIL" : "");
				}
				return pass;
			} else {
				System.out.printf("Some requests were not processed %d/%d%n", times.size(), requests);
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	@Test
	public void test01() {
		Assert.assertTrue(test("Test 1", 0, 100, 0, 0, true, false, true, new int[] { 5 }, 100, 10000, 0, 6200));
	}
	@Test
	public void test02() {
		Assert.assertTrue(test("Test 2", 50, 150, 20, 0, false, false, true, new int[] { 5 }, 100, 5000, 0, 5100));
	}
	@Test
	public void test03() {
		Assert.assertTrue(test("Test 3", 10, 25, 5, 15000, true, false, true, new int[] { 5 }, 20, 10000, 0, 5100));
	}
	@Test
	public void test04() {
		Assert.assertTrue(test("Test 4", 50, 150, 20, 0, false, true, true, new int[] { 5 }, 100, 5000, 0, 5080));
	}
	@Test
	public void test05() {
		Assert.assertTrue(test("Test 5", 10, 50, 5, 15000, true, false, true, new int[] { 5, 3, 2 }, 50, 10000, 0, 10320));
	}
	@Test
	public void test06() {
		Assert.assertTrue(test("Test 6", 100, 250, 20, 0, true, true, true, new int[] { 5 }, 800, 20000, 0, 6000));
	}
	@Test
	public void test07() {
		Assert.assertTrue(test("Test 7", 0, 100, 0, 0, false, false, false, new int[] { 5 }, 100, 10000, 0, 7000));
	}
	@Test
	public void test08() {
		Assert.assertTrue(test("Test 8", 50, 150, 10, 0, true, false, false, new int[] { 5 }, 100, 5000, 0, 5600));
	}
	@Test
	public void test09() {
		Assert.assertTrue(test("Test 9", 10, 25, 5, 15000, false, false, false, new int[] { 5 }, 20, 10000, 0, 5100));
	}
	@Test
	public void test10() {
		Assert.assertTrue(test("Test 10", 50, 150, 10, 0, true, true, false, new int[] { 5 }, 100, 5000, 0, 5600));
	}
	@Test
	public void test11() {
		Assert.assertTrue(test("Test 11", 10, 50, 5, 15000, false, false, false, new int[] { 5, 3, 2 }, 50, 10000, 0, 10350));
	}
	@Test
	public void test12() {
		Assert.assertTrue(test("Test 12", 50, 250, 20, 0, true, true, false, new int[] { 5 }, 800, 20000, 0, 6000));
	}
	@Test
	public void test13() {
		Assert.assertTrue(test("Test 13", 10, 30, 5, 0, true, true, false, new int[] { 5 }, 20, 0, 3000, 3001));
	}
	@Test
	public void test14() {
		Assert.assertTrue(test("Test 14", 10, 30, 5, 0, false, false, false, new int[] { 5 }, 20, 0, 2000, 2001));
	}
	@Test
	public void test15() {
		Assert.assertTrue(test("Test 15", 10, 30, 5, 250, true, true, false, new int[] { 5 }, 50, 10000, 0, 6000));
	}
	
}
