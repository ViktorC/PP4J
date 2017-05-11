package net.viktorc.pspp;

import org.junit.Assert;
import org.junit.Test;

import net.viktorc.pspp.CommandListener;
import net.viktorc.pspp.CommandSubmission;
import net.viktorc.pspp.PSPPool;
import net.viktorc.pspp.ProcessListener;
import net.viktorc.pspp.ProcessManager;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * A test class for the {@link net.viktorc.pspp.PSPPool}.
 * 
 * @author A6714
 *
 */
public class PSPoolTest {
	
	/**
	 * Creates a test process pool according to the specified parameters. See 
	 * {@link net.viktorc.pspp.PSPPool#PSPPool(ProcessBuilder, ProcessListener, int, int, int, long)}.
	 */
	private PSPPool getPool(int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime)
			throws IllegalArgumentException, IOException, URISyntaxException {
		// The tests are either run locally on my Windows machine or through Travis CI on Linux.
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		String programLocation = "\"" + new File(getClass().getResource(windows ? "win/test.exe" : "linux/test")
				.toURI().getPath()).getAbsolutePath() + "\"";
		return new PSPPool(new ProcessBuilder(programLocation), new ProcessListener() {
			
			@Override
			public void onStarted(ProcessManager manager) {
				try {
					manager.executeCommand(new CommandSubmission("start", new CommandListener() {
						
						@Override
						public boolean onNewStandardOutput(String standardOutput) {
							return "ok".equals(standardOutput);
						}
						@Override
						public boolean onNewErrorOutput(String errorOutput) {
							return true;
						}
					}));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			@Override
			public void onTermination(int resultCode) {
				
			}
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
	 * @param reuse Whether a process can execute multiple commands.
	 * @param procTime The time for which the test process should "execute".
	 * @param requests The number of commands to submit.
	 * @param requestPerSe The number of commands to submit per second
	 * @return A list of the total execution times of the commands.
	 * @throws Exception
	 */
	private List<Long> testBase(int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime,
			boolean reuse, int procTime, int requests, float requestPerSe) throws Exception {
		try (PSPPool procPool = getPool(minPoolSize, maxPoolSize, reserveSize, keepAliveTime)) {
			List<Future<Long>> futures = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				try {
					Thread.sleep((long) (1000/requestPerSe));
				} catch (InterruptedException e) { }
				futures.add(procPool.submitCommand(new CommandSubmission("process " + procTime,
						new CommandListener() {
					
					@Override
					public boolean onNewStandardOutput(String standardOutput) {
						return standardOutput.equals("ready");
					}
					@Override
					public boolean onNewErrorOutput(String errorOutput) {
						return true;
					}
				}, !reuse)));
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
	 * @param reuse Whether a process can execute multiple commands.
	 * @param procTime The time for which the test process should "execute".
	 * @param requests The number of commands to submit.
	 * @param requestPerSe The number of commands to submit per second
	 * @param failTime The duration from which on commands are considered to be executed too slowly and cause
	 * the test to fail.
	 * @return Whether the test passes.
	 */
	private boolean test(String testName, int minPoolSize, int maxPoolSize, int reserveSize, long keepAliveTime,
			boolean reuse, int procTime, int requests, float requestPerSe, long failTime) {
		try {
			List<Long> times = testBase(minPoolSize, maxPoolSize, reserveSize, keepAliveTime, reuse, procTime,
					requests, requestPerSe);
			System.out.println("\n" + testName);
			System.out.println("---------------------------------------------------------------------------------");
			System.out.printf("minPoolSize: %d; maxPoolSize: %d; reserveSize: %d; keepAliveTime: %d;%n" +
					"reuse: %s; procTime: %d; requests: %d; requestPerSec: %.2f; failTime: %.4f%n", minPoolSize,
					maxPoolSize, reserveSize, keepAliveTime, Boolean.toString(reuse), procTime, requests,
					requestPerSe, (float) (((double) failTime)/1000));
			System.out.println("---------------------------------------------------------------------------------");
			if (times.size() == requests) {
				boolean pass = true;
				for (Long time : times) {
					boolean fail = time == null || time/1000000 >= failTime;
					if (fail)
						pass = false;
					System.out.printf("Time: %.4f %s%n", (float) (((double) time)/1000000000), fail ? "FAIL" : "");
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
	public void test1() {
		Assert.assertTrue(test("Test 1", 0, 100, 0, 0, true, 5, 100, 10, 6200));
	}
	@Test
	public void test2() {
		Assert.assertTrue(test("Test 2", 50, 150, 20, 0, true, 5, 100, 20, 5150));
	}
	@Test
	public void test3() {
		Assert.assertTrue(test("Test 3", 10, 25, 5, 15000, true, 5, 20, 2, 5020));
	}
	@Test
	public void test4() {
		Assert.assertTrue(test("Test 4", 0, 100, 0, 0, false, 5, 100, 10, 6250));
	}
	@Test
	public void test5() {
		Assert.assertTrue(test("Test 5", 50, 150, 10, 0, false, 5, 100, 20, 5800));
	}
	@Test
	public void test6() {
		Assert.assertTrue(test("Test 6", 10, 25, 5, 15000, false, 5, 20, 2, 5050));
	}
	
}
