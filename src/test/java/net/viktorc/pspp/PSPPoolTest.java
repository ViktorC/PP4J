package net.viktorc.pspp;

import org.junit.Assert;
import org.junit.Test;

import net.viktorc.pspp.CommandSubmission;
import net.viktorc.pspp.PSPPool;
import net.viktorc.pspp.ProcessListener;
import net.viktorc.pspp.ProcessManager;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
	 * Creates a test process pool according to the specified parameters. See 
	 * {@link net.viktorc.pspp.PSPPool#PSPPool(ProcessBuilder, ProcessListener, int, int, int, long)}.
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
		return new PSPPool(new ProcessBuilder(programLocation), new ProcessListener() {
			
			@Override
			public void onStartup(ProcessManager manager) {
				try {
					manager.execute(new CommandSubmission(new Command("start",
							new SimpleCommandListener((l, s) -> "ok".equals(s), (l, s) -> true)), false));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			@Override
			public boolean isStartedUp(String output, boolean standard) {
				return !verifyStartup || standard && "hi".equals(output);
			}
			@Override
			public boolean terminate(ProcessManager manager) {
				if (manuallyTerminate) {
					try {
						AtomicBoolean success = new AtomicBoolean(true);
						if (manager.execute(new CommandSubmission(new Command("stop",
								new SimpleCommandListener((l, s) -> "bye".equals(s), (l, s) -> {
									success.set(false);
									return true;
								})), false))) {
							return success.get();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				return false;
			}
			@Override
			public void onTermination(int resultCode) { }
		}, minPoolSize, maxPoolSize, reserveSize, keepAliveTime);
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
	 * @param requestPerSe The number of commands to submit per second
	 * @return A list of the total execution times of the commands.
	 * @throws Exception
	 */
	private List<Long> testBase(int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime,
			boolean verifyStartup, boolean manuallyTerminate, boolean reuse, int[] procTimes, int requests,
			float requestPerSe) throws Exception {
		try (PSPPool procPool = getPool(minPoolSize, maxPoolSize, reserveSize, keepAliveTime, verifyStartup,
				manuallyTerminate)) {
			List<Future<Long>> futures = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				try {
					Thread.sleep((long) (1000/requestPerSe));
				} catch (InterruptedException e) {
					return null;
				}
				List<Command> commands = new ArrayList<>();
				for (int procTime : procTimes) {
					commands.add(new Command("process " + procTime,
							new SimpleCommandListener((l, s) -> "ready".equals(s), (l, s) -> true)));
				}
				futures.add(procPool.submit(new CommandSubmission(commands, !reuse)));
			}
			List<Long> times = new ArrayList<>();
			for (Future<Long> future : futures)
				times.add(future.get());
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
	 * @param failTime The duration from which on commands are considered to be executed too slowly and cause
	 * the test to fail.
	 * @return Whether the test passes.
	 */
	private boolean test(String testName, int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime,
			boolean verifyStartup, boolean manuallyTerminate, boolean reuse, int[] procTimes, int requests,
			float requestPerSe, long failTime) {
		try {
			List<Long> times = testBase(minPoolSize, maxPoolSize, reserveSize, keepAliveTime, verifyStartup,
					manuallyTerminate, reuse, procTimes, requests, requestPerSe);
			System.out.println("\n" + testName);
			System.out.println("-------------------------------------------------------------------------------------");
			System.out.printf("minPoolSize: %d; maxPoolSize: %d; reserveSize: %d; keepAliveTime: %d;%n" +
					"verifyStartup: %s; manuallyTerminate: %s; reuse: %s; procTimes: %s;%n" +
					"requests: %d; requestPerSec: %.3f; failTime: %.3f%n",
					minPoolSize, maxPoolSize, reserveSize, keepAliveTime, Boolean.toString(verifyStartup),
					Boolean.toString(manuallyTerminate), Boolean.toString(reuse), Arrays.toString(procTimes),
					requests, requestPerSe, (float) (((double) failTime)/1000));
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
		Assert.assertTrue(test("Test 1", 0, 100, 0, 0, true, false, true, new int[] { 5 }, 100, 10, 6100));
	}
	@Test
	public void test02() {
		Assert.assertTrue(test("Test 2", 50, 150, 20, 0, false, false, true, new int[] { 5 }, 100, 20, 5080));
	}
	@Test
	public void test03() {
		Assert.assertTrue(test("Test 3", 10, 25, 5, 15000, true, false, true, new int[] { 5 }, 20, 2, 5008));
	}
	@Test
	public void test04() {
		Assert.assertTrue(test("Test 4", 50, 150, 20, 0, false, true, true, new int[] { 5 }, 100, 20, 5080));
	}
	@Test
	public void test05() {
		Assert.assertTrue(test("Test 5", 10, 50, 5, 15000, true, false, true, new int[] { 5, 3, 2 }, 50, 5, 10060));
	}
	@Test
	public void test06() {
		Assert.assertTrue(test("Test 6", 0, 100, 0, 0, false, false, false, new int[] { 5 }, 100, 10, 6200));
	}
	@Test
	public void test07() {
		Assert.assertTrue(test("Test 7", 50, 150, 10, 0, true, false, false, new int[] { 5 }, 100, 20, 5600));
	}
	@Test
	public void test08() {
		Assert.assertTrue(test("Test 8", 10, 25, 5, 15000, false, false, false, new int[] { 5 }, 20, 2, 5010));
	}
	@Test
	public void test09() {
		Assert.assertTrue(test("Test 9", 50, 150, 10, 0, true, true, false, new int[] { 5 }, 100, 20, 5600));
	}
	@Test
	public void test10() {
		Assert.assertTrue(test("Test 10", 10, 50, 5, 15000, false, false, false, new int[] { 5, 3, 2 }, 50, 5, 10080));
	}
	
}
