package net.viktorc.pspp;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import net.viktorc.pspp.PSPPool;
import net.viktorc.pspp.ProcessShell;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A test class for the process pool.
 * 
 * @author A6714
 *
 */
public class PSPPoolTest {
	
	@Rule
	public final ExpectedException exceptionRule = ExpectedException.none();
	
	/**
	 * Creates a test process pool according to the specified parameters.
	 * 
	 * @param minPoolSize The minimum pool size.
	 * @param maxPoolSize The maximum pool size.
	 * @param reserveSize The process reserve size.
	 * @param keepAliveTime The time after which idled processes are killed.
	 * @param verifyStartup Whether the startup should be verified.
	 * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
	 * @param verbose Whether the events relating to the management of the pool should be logged to the console.
	 * @return The process pool created according to the specified parameters.
	 */
	private PSPPool getPool(int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime, boolean verifyStartup,
			boolean manuallyTerminate, boolean verbose) throws IllegalArgumentException, IOException, URISyntaxException {
		// The tests are either run locally on my Windows machine or through Travis CI on Linux.
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		String programLocation = new File(getClass().getResource(windows ? "win/test.exe" : "linux/test")
				.toURI().getPath()).getAbsolutePath();
		File file = new File(programLocation);
		// For testing on Travis CI
		file.setExecutable(true);
		PSPPool pool = new PSPPool(() -> new SimpleProcessManager(new ProcessBuilder(programLocation),
				s -> {
					try {
						s.execute(new SimpleSubmission(new SimpleCommand("start",
								(c, o) -> "ok".equals(o), (c, o) -> true), false));
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
					}
				}) {
			
			@Override
			public boolean startsUpInstantly() {
				return !verifyStartup;
			}
			@Override
			public boolean isStartedUp(String output, boolean standard) {
				return standard && "hi".equals(output);
			}
			@Override
			public boolean terminate(ProcessShell shell) {
				if (manuallyTerminate) {
					try {
						AtomicBoolean success = new AtomicBoolean(true);
						if (shell.execute(new SimpleSubmission(new SimpleCommand("stop", (c, o) -> "bye".equals(o),
								(c, o) -> {
									success.set(false);
									return true;
								}), false)))
							return success.get();
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
					}
				}
				return false;
			}
			
		}, minPoolSize, maxPoolSize, reserveSize, keepAliveTime, verbose);
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
	 * @param verbose Whether the events relating to the management of the pool should be logged to the console.
	 * @param reuse Whether a process can execute multiple commands.
	 * @param procTimes The times for which the test processes should "execute" commands. Each element 
	 * stands for a command. If there are multiple elements, the commands will be chained.
	 * @param requests The number of commands to submit.
	 * @param timeSpan The number of milliseconds in which the specified number of requests should be sent. 
	 * If it is 0 or less, they are all sent at once.
	 * @param cancelTime The number of milliseconds after which the futures should be cancelled. If it 
	 * is 0 or less, the futures are not cancelled.
	 * @param forcedCancel If the command should be interrupted if it is already being processed. If 
	 * <code>cancelTime</code> is not greater than 0, it has no effect.
	 * @param earlyClose Whether the pool should be closed right after the submission of the commands.
	 * @return A list of the total execution times of the commands.
	 * @throws Exception
	 */
	private List<Long> testBase(int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime,
			boolean verifyStartup, boolean manuallyTerminate, boolean verbose, boolean reuse, int[] procTimes,
			int requests, long timeSpan, long cancelTime, boolean forcedCancel, boolean earlyClose)
					throws Exception {
		try (PSPPool procPool = getPool(minPoolSize, maxPoolSize, reserveSize, keepAliveTime, verifyStartup,
				manuallyTerminate, verbose)) {
			long frequency = requests > 0 ? timeSpan/requests : 0;
			List<Future<Long>> futures = new ArrayList<>(requests);
			List<Entry<Semaphore,Long>> cancelTimes = cancelTime > 0 ? new ArrayList<>(requests) : null;
			for (int i = 0; i < requests; i++) {
				if (frequency > 0) {
					try {
						Thread.sleep(frequency);
					} catch (InterruptedException e) {
						return null;
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
										if (verbose) {
											System.out.println("Std: " +
													c.getJointStandardOutLines().replaceAll("\n", " "));
											System.out.println("Err: " +
													c.getJointErrorOutLines().replaceAll("\n", " "));
										}
										Assert.assertTrue(c.getStandardOutLines().size() == procTime &&
												c.getErrorOutLines().size() == 0);
										c.reset();
										return true;
									}
									return false;
								}, (c, o) -> true));
				}
				Submission submission;
				if (cancelTime > 0) {
					Semaphore semaphore = new Semaphore(0);
					submission = new SimpleSubmission(commands, !reuse) {
						
						@Override
						public void onFinishedProcessing() {
							semaphore.release();
						}
						
					};
					long startTime = System.nanoTime();
					cancelTimes.add(new SimpleEntry<>(semaphore, startTime));
				} else
					submission = new SimpleSubmission(commands, !reuse);
				futures.add(procPool.submit(submission));
			}
			if (cancelTime > 0) {
				Thread.sleep(cancelTime);
				for (Future<Long> future : futures)
					future.cancel(forcedCancel);
			}
			try {
				if (earlyClose)
					procPool.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			List<Long> times = new ArrayList<>();
			for (int i = 0; i < futures.size(); i++) {
				Future<Long> future = futures.get(i);
				try {
					long time = future.get();
					times.add(time);
				} catch (CancellationException e) {
					if (cancelTime > 0) {
						Entry<Semaphore,Long> cancelEntry = cancelTimes.get(i);
						cancelEntry.getKey().acquire();
						times.add(System.nanoTime() - cancelEntry.getValue());
					} else
						times.add((long) 0);
				}
			}
			if (!earlyClose)
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
	 * @param verbose Whether the events relating to the management of the pool should be logged to the console.
	 * @param reuse Whether a process can execute multiple commands.
	 * @param procTimes The times for which the test processes should "execute" commands. Each element 
	 * stands for a command. If there are multiple elements, the commands will be chained.
	 * @param requests The number of commands to submit.
	 * @param requestPerSe The number of commands to submit per second
	 * @param cancelTime The number of milliseconds after which the futures should be cancelled. If it 
	 * is 0 or less, the futures are not cancelled.
	 * @param forcedCancel If the command should be interrupted if it is already being processed. If 
	 * <code>cancelTime</code> is not greater than 0, it has no effect.
	 * @param lowerBound The minimum acceptable submission execution time.
	 * @param upperBound The maximum acceptable submission execution time.
	 * @param earlyClose Whether the pool should be closed right after the submission of the commands.
	 * @return Whether the test passes.
	 * @throws Exception If the process pool cannot be created.
	 */
	private boolean test(String testName, int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime,
			boolean verifyStartup, boolean manuallyTerminate, boolean verbose, boolean reuse, int[] procTimes,
			int requests, long timeSpan, long cancelTime, boolean forcedCancel, boolean earlyClose, long lowerBound,
			long upperBound) throws Exception {
		List<Long> times = testBase(minPoolSize, maxPoolSize, reserveSize, keepAliveTime, verifyStartup,
				manuallyTerminate, verbose, reuse, procTimes, requests, timeSpan, cancelTime, forcedCancel,
				earlyClose);
		System.out.println("\n" + testName);
		System.out.println("-------------------------------------------------------------------------------------" +
				"-----------------------");
		System.out.printf("minPoolSize: %d; maxPoolSize: %d; reserveSize: %d; keepAliveTime: %d; " + 
				"verifyStartup: %s;%nmanuallyTerminate: %s; verbose: %s; reuse: %s; procTimes: %s;%n" +
				"requests: %d; timeSpan: %d; cancelTime: %d; forcedCancel: %s; earlyClose: %s;%n" +
				"lowerBound: %.3f; upperBound: %.3f;%n", minPoolSize, maxPoolSize, reserveSize, keepAliveTime,
				Boolean.toString(verifyStartup), Boolean.toString(manuallyTerminate), Boolean.toString(verbose),
				Boolean.toString(reuse), Arrays.toString(procTimes), requests, timeSpan, cancelTime,
				Boolean.toString(forcedCancel), Boolean.toString(earlyClose), (float) (((double) lowerBound)/1000),
				(float) (((double) upperBound)/1000));
		System.out.println("-------------------------------------------------------------------------------------" +
				"-----------------------");
		if (times.size() == requests) {
			boolean pass = true;
			for (Long time : times) {
				long timeMs = time/1000000;
				boolean fail = time == null || timeMs > upperBound || timeMs < lowerBound;
				if (fail)
					pass = false;
				System.out.printf("Time: %.3f %s%n", (float) (((double) timeMs)/1000), fail ? "FAIL" : "");
			}
			return pass;
		} else {
			System.out.printf("Some requests were not processed %d/%d%n", times.size(), requests);
			return false;
		}
	}
	@Test
	public void test01() throws Exception {
		exceptionRule.expect(IllegalArgumentException.class);
		exceptionRule.expectMessage("The minimum pool size has to be greater than 0.");
		test("Test 1", -1, 5, 0, 0, false, false, false, false, new int[] { 5 },
				100, 10000, 0, false, false, 4995, 6200);
	}
	@Test
	public void test02() throws Exception {
		exceptionRule.expect(IllegalArgumentException.class);
		exceptionRule.expectMessage("The maximum pool size has to be at least 1 and at least as great as the " +
				"minimum pool size.");
		test("Test 2", 0, 0, 0, 0, false, false, false, false, new int[] { 5 },
				100, 10000, 0, false, false, 4995, 6200);
	}
	@Test
	public void test03() throws Exception {
		exceptionRule.expect(IllegalArgumentException.class);
		exceptionRule.expectMessage("The maximum pool size has to be at least 1 and at least as great as the " +
				"minimum pool size.");
		test("Test 3", 10, 5, 0, 0, false, false, false, false, new int[] { 5 },
				100, 10000, 0, false, false, 4995, 6200);
	}
	@Test
	public void test04() throws Exception {
		exceptionRule.expect(IllegalArgumentException.class);
		exceptionRule.expectMessage("The reserve has to be greater than 0 and less than the maximum pool size.");
		test("Test 4", 10, 12, -1, 0, false, false, false, false, new int[] { 5 },
				100, 10000, 0, false, false, 4995, 6200);
	}
	@Test
	public void test05() throws Exception {
		exceptionRule.expect(IllegalArgumentException.class);
		exceptionRule.expectMessage("The reserve has to be greater than 0 and less than the maximum pool size.");
		test("Test 5", 10, 12, 15, 0, false, false, false, false, new int[] { 5 },
				100, 10000, 0, false, false, 4995, 6200);
	}
	@Test
	public void test06() throws Exception {
		Assert.assertTrue(test("Test 6", 0, 100, 0, 0, true, false, false, true, new int[] { 5 },
				100, 10000, 0, false, false, 4995, 6200));
	}
	@Test
	public void test07() throws Exception {
		Assert.assertTrue(test("Test 7", 50, 150, 20, 0, false, false, false, true, new int[] { 5 },
				100, 5000, 0, false, false, 4995, 5100));
	}
	@Test
	public void test08() throws Exception {
		Assert.assertTrue(test("Test 8", 10, 25, 5, 15000, true, false, false, true, new int[] { 5 },
				20, 10000, 0, false, false, 4995, 5100));
	}
	@Test
	public void test09() throws Exception {
		Assert.assertTrue(test("Test 9", 50, 150, 20, 0, false, true, false, true, new int[] { 5 },
				100, 5000, 0, false, false, 4995, 5080));
	}
	@Test
	public void test10() throws Exception {
		Assert.assertTrue(test("Test 10", 10, 50, 5, 15000, true, false, false, true, new int[] { 5, 3, 2 },
				50, 10000, 0, false, false, 9995, 10340));
	}
	@Test
	public void test11() throws Exception {
		Assert.assertTrue(test("Test 11", 100, 250, 20, 0, true, true, false, true, new int[] { 5 },
				800, 20000, 0, false, false, 4995, 6000));
	}
	@Test
	public void test12() throws Exception {
		Assert.assertTrue(test("Test 12", 0, 100, 0, 0, false, false, false, false, new int[] { 5 },
				100, 10000, 0, false, false, 4995, 7150));
	}
	@Test
	public void test13() throws Exception {
		Assert.assertTrue(test("Test 13", 50, 150, 10, 0, true, false, false, false, new int[] { 5 },
				100, 5000, 0, false, false, 4995, 5600));
	}
	@Test
	public void test14() throws Exception {
		Assert.assertTrue(test("Test 14", 10, 25, 5, 15000, false, false, false, false, new int[] { 5 },
				20, 10000, 0, false, false, 4995, 5100));
	}
	@Test
	public void test15() throws Exception {
		Assert.assertTrue(test("Test 15", 50, 150, 10, 0, true, true, false, false, new int[] { 5 },
				100, 5000, 0, false, false, 4995, 5600));
	}
	@Test
	public void test16() throws Exception {
		Assert.assertTrue(test("Test 16", 10, 50, 5, 15000, false, false, false, false, new int[] { 5, 3, 2 },
				50, 10000, 0, false, false, 9995, 10350));
	}
	@Test
	public void test17() throws Exception {
		Assert.assertTrue(test("Test 17", 50, 250, 20, 0, true, true, false, false, new int[] { 5 },
				800, 20000, 0, false, false, 4995, 6000));
	}
	@Test
	public void test18() throws Exception {
		Assert.assertTrue(test("Test 18", 10, 30, 5, 0, true, true, false, false, new int[] { 5 },
				20, 0, 2500, true, false, 2495, 2520));
	}
	@Test
	public void test19() throws Exception {
		Assert.assertTrue(test("Test 19", 20, 20, 0, 0, false, false, false, false, new int[] { 5 },
				20, 0, 2500, false, false, 4995, 5090));
	}
	@Test
	public void test20() throws Exception {
		Assert.assertTrue(test("Test 20", 10, 30, 5, 0, true, true, false, false, new int[] { 5, 5, 3 },
				20, 0, 2500, true, false, 2495, 2520));
	}
	@Test
	public void test21() throws Exception {
		Assert.assertTrue(test("Test 21", 20, 20, 0, 0, true, true, false, false, new int[] { 5, 5, 3 },
				20, 0, 3000, false, false, 4995, 5090));
	}
	@Test
	public void test22() throws Exception {
		Assert.assertTrue(test("Test 22", 20, 40, 4, 250, true, true, true, false, new int[] { 5 },
				40, 5000, 0, false, false, 4995, 6150));
	}
	@Test
	public void test23() throws Exception {
		Assert.assertTrue(test("Test 23", 100, 100, 0, 5000, true, false, false, false, new int[] { 5 },
				100, 0, 0, false, true, 0, 0));
	}
	@Test
	public void test24() throws Exception {
		exceptionRule.expect(IllegalArgumentException.class);
		exceptionRule.expectMessage("The commands cannot be null.");
		test("Test 24", 10, 20, 0, 0, false, false, false, false, null,
				100, 10000, 0, false, false, 4995, 6200);
	}
	@Test
	public void test25() throws Exception {
		exceptionRule.expect(IllegalArgumentException.class);
		exceptionRule.expectMessage("The commands cannot be empty.");
		test("Test 25", 10, 20, 0, 0, false, false, false, false, new int[0],
				100, 10000, 0, false, false, 4995, 6200);
	}
	
}
