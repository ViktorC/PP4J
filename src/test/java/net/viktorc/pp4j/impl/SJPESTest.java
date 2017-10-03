package net.viktorc.pp4j.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.Runnable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import net.viktorc.pp4j.api.JavaProcessOptions;
import net.viktorc.pp4j.api.JavaProcessOptions.JVMArch;
import net.viktorc.pp4j.api.JavaProcessOptions.JVMType;
import net.viktorc.pp4j.impl.Conversion;
import net.viktorc.pp4j.impl.JavaProcess;
import net.viktorc.pp4j.impl.SimpleJavaProcessOptions;
import net.viktorc.pp4j.impl.StandardJavaProcessPool;

/**
 * A test class for the Java process based process pool executor implementation.
 * 
 * @author Viktor Csomor
 *
 */
public class StandardJavaProcessPoolTest {

	private static final String TEST_TITLE_FORMAT = "%nTest %d%n" +
			"-------------------------------------------------%n";
	
	@Rule
	public final ExpectedException exceptionRule = ExpectedException.none();
	
	// Startup testing
	@Test
	public void test01() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 1);
		long start = System.currentTimeMillis();
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				1, 1, 0, false);
		try {
			long time = System.currentTimeMillis() - start;
			boolean success = time < 1000;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test02() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 2);
		long start = System.currentTimeMillis();
		StandardJavaProcessPool exec = new StandardJavaProcessPool(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 0),
				1, 1, 0, false);
		try {
			long time = System.currentTimeMillis() - start;
			boolean success = time < 500;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test03() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 3);
		long start = System.currentTimeMillis();
		StandardJavaProcessPool exec = new StandardJavaProcessPool(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.SERVER, 256, 4096, 4096,
				5000), 1, 1, 0, false);
		try {
			long time = System.currentTimeMillis() - start;
			boolean success = time < 500;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test04() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 4);
		long start = System.currentTimeMillis();
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				10, 15, 5, false);
		try {
			long time = System.currentTimeMillis() - start;
			boolean success = time < 2000;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test05() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 5);
		long start = System.currentTimeMillis();
		StandardJavaProcessPool exec = new StandardJavaProcessPool(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 0),
				10, 15, 5, false);
		try {
			long time = System.currentTimeMillis() - start;
			boolean success = time < 2000;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test06() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 6);
		long start = System.currentTimeMillis();
		StandardJavaProcessPool exec = new StandardJavaProcessPool(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.SERVER, 256, 4096, 4096,
				5000), 10, 15, 5, false);
		try {
			long time = System.currentTimeMillis() - start;
			boolean success = time < 2000;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	// Submission testing.
	@Test
	public void test07() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 7);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				5, 5, 0, false);
		try {
			List<Future<?>> futures = new ArrayList<>();
			AtomicInteger j = new AtomicInteger(2);
			for (int i = 0; i < 5; i++) {
				futures.add(exec.submit((Runnable & Serializable) () -> {
					j.incrementAndGet();
					Thread t = new Thread(() -> {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						j.incrementAndGet();
					});
					t.start();
					try {
						t.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}));
			}
			for (Future<?> f : futures)
				f.get();
			Assert.assertTrue(j.get() == 2);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test08() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 8);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 0),
				5, 5, 0, false);
		try {
			List<Future<?>> futures = new ArrayList<>();
			AtomicInteger j = new AtomicInteger(2);
			for (int i = 0; i < 5; i++) {
				futures.add(exec.submit((Runnable & Serializable) () -> {
					j.incrementAndGet();
					Thread t = new Thread(() -> {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						j.incrementAndGet();
					});
					t.start();
					try {
						t.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}, j));
			}
			for (Future<?> f : futures)
				Assert.assertTrue(((AtomicInteger) f.get()).get() == 4);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test09() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 9);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				1, 1, 0, false);
		int base = 13;
		try {
			Assert.assertTrue(exec.submit((Callable<Integer> & Serializable) () -> 4*base)
					.get() == 52);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	// Synchronous execution testing.
	@Test
	public void test10() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 10);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				2, 2, 0, false);
		try {
			long start = System.currentTimeMillis();
			exec.execute((Runnable & Serializable) () -> {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});
			long time = System.currentTimeMillis() - start;
			boolean success = time < 5300 && time > 4995;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	// Invocation testing.
	@Test
	public void test11() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 11);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				2, 2, 0, false);
		try {
			int base = 13;
			List<Future<Integer>> results = new ArrayList<>();
			List<Callable<Integer>> tasks = new ArrayList<>();
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(2000);
				return (int) Math.pow(base, 2);
			});
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(4000);
				return (int) Math.pow(base, 3);
			});
			long start = System.currentTimeMillis();
			results = exec.invokeAll(tasks);
			long time = System.currentTimeMillis() - start;
			boolean success = time < 4300 && time > 3995;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
			Assert.assertTrue(results.get(0).get() == 169);
			Assert.assertTrue(results.get(1).get() == 2197);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test12() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 12);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				1, 1, 0, false);
		try {
			int base = 13;
			List<Future<Integer>> results = new ArrayList<>();
			List<Callable<Integer>> tasks = new ArrayList<>();
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(2000);
				return (int) Math.pow(base, 2);
			});
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(4000);
				return (int) Math.pow(base, 3);
			});
			long start = System.currentTimeMillis();
			results = exec.invokeAll(tasks);
			long time = System.currentTimeMillis() - start;
			boolean success = time < 6300 && time > 5995;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
			Assert.assertTrue(results.get(0).get() == 169);
			Assert.assertTrue(results.get(1).get() == 2197);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test13() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 13);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				2, 2, 0, false);
		try {
			int base = 13;
			List<Future<Integer>> results = new ArrayList<>();
			List<Callable<Integer>> tasks = new ArrayList<>();
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(2000);
				return (int) Math.pow(base, 2);
			});
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(4000);
				return (int) Math.pow(base, 3);
			});
			long start = System.currentTimeMillis();
			results = exec.invokeAll(tasks, 3000, TimeUnit.MILLISECONDS);
			long time = System.currentTimeMillis() - start;
			boolean success = time < 3300 && time > 2995;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
			Assert.assertTrue(results.get(0).get() == 169);
			exceptionRule.expect(CancellationException.class);
			results.get(1).get();
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test14() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 14);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				1, 1, 0, false);
		try {
			int base = 13;
			List<Future<Integer>> results = new ArrayList<>();
			List<Callable<Integer>> tasks = new ArrayList<>();
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(2000);
				return (int) Math.pow(base, 2);
			});
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(4000);
				return (int) Math.pow(base, 3);
			});
			long start = System.currentTimeMillis();
			results = exec.invokeAll(tasks, 3000, TimeUnit.MILLISECONDS);
			long time = System.currentTimeMillis() - start;
			boolean success = time < 3300 && time > 2995;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
			Assert.assertTrue(results.get(0).get() == 169);
			exceptionRule.expect(CancellationException.class);
			results.get(1).get();
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test15() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 15);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				2, 2, 0, false);
		try {
			int base = 13;
			List<Callable<Integer>> tasks = new ArrayList<>();
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(2000);
				return (int) Math.pow(base, 2);
			});
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(4000);
				return (int) Math.pow(base, 3);
			});
			long start = System.currentTimeMillis();
			int result = exec.invokeAny(tasks);
			long time = System.currentTimeMillis() - start;
			boolean success = time < 4300 && time > 3995;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
			Assert.assertTrue(result == 169 || result == 2197);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test16() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 16);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				2, 2, 0, false);
		try {
			int base = 13;
			List<Callable<Integer>> tasks = new ArrayList<>();
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(2000);
				return (int) Math.pow(base, 2);
			});
			tasks.add((Callable<Integer> & Serializable) () -> {
				throw new Exception();
			});
			long start = System.currentTimeMillis();
			int result = exec.invokeAny(tasks);
			long time = System.currentTimeMillis() - start;
			boolean success = time < 2300 && time > 1995;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
			Assert.assertTrue(result == 169);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test17() throws InterruptedException, ExecutionException, TimeoutException {
		System.out.printf(TEST_TITLE_FORMAT, 17);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				2, 2, 0, false);
		try {
			int base = 13;
			List<Callable<Integer>> tasks = new ArrayList<>();
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(2000);
				return (int) Math.pow(base, 2);
			});
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(4000);
				return (int) Math.pow(base, 3);
			});
			long start = System.currentTimeMillis();
			int result = exec.invokeAny(tasks, 3000, TimeUnit.MILLISECONDS);
			long time = System.currentTimeMillis() - start;
			boolean success = time < 3300 && time > 2995;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
			Assert.assertTrue(result == 169);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test18() throws InterruptedException, ExecutionException, TimeoutException {
		System.out.printf(TEST_TITLE_FORMAT, 18);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				2, 2, 0, false);
		try {
			int base = 13;
			List<Callable<Integer>> tasks = new ArrayList<>();
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(2000);
				return (int) Math.pow(base, 2);
			});
			tasks.add((Callable<Integer> & Serializable) () -> {
				Thread.sleep(4000);
				return (int) Math.pow(base, 3);
			});
			exceptionRule.expect(TimeoutException.class);
			exec.invokeAny(tasks, 1000, TimeUnit.MILLISECONDS);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	// Test of shutdownNow.
	@Test
	public void test19() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 19);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				1, 1, 0, false);
		try {
			Runnable r1 = (Runnable & Serializable) () -> {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			};
			Runnable r2 = (Runnable & Serializable) () -> {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			};
			exec.submit((Runnable & Serializable) () -> {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});
			exec.submit(r1);
			exec.submit(r2);
			long start = System.currentTimeMillis();
			List<Runnable> queuedTasks = exec.shutdownNow();
			Runnable a1, a2;
			if (queuedTasks.size() == 2) {
				a1 = queuedTasks.get(0);
				a2 = queuedTasks.get(1);
			} else {
				a1 = queuedTasks.get(1);
				a2 = queuedTasks.get(2);
			}
			long time = System.currentTimeMillis() - start;
			boolean success = time < 20;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
			Assert.assertTrue(a1 == r1 && a2 == r2);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	// Task and result exchange performance testing.
	@Test
	public void test20() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 20);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(new JavaProcessOptions() {},
				1, 1, 0, false);
		try {
			long start = System.currentTimeMillis();
			AtomicInteger res = exec.submit((Callable<AtomicInteger> & Serializable) () -> {
				Thread.sleep(2000);
				return new AtomicInteger(13);
			}).get();
			long time = System.currentTimeMillis() - start;
			boolean success = time < 2300 && time > 1995;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
			Assert.assertTrue(res.get() == 13);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test21() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 21);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 0),
				30, 80, 10, false);
		try {
			List<Future<AtomicInteger>> results = new ArrayList<>();
			long start = System.currentTimeMillis();
			for (int i = 0; i < 50; i++) {
				Thread.sleep(50);
				results.add(exec.submit((Callable<AtomicInteger> & Serializable) () -> {
					Thread.sleep(5000);
					return new AtomicInteger();
				}));
			}
			for (Future<AtomicInteger> res : results)
				res.get();
			long time = System.currentTimeMillis() - start;
			boolean success = time < 12000 && time > 7495;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test22() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 22);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 500),
				30, 80, 10, false);
		try {
			List<Future<AtomicInteger>> results = new ArrayList<>();
			long start = System.currentTimeMillis();
			for (int i = 0; i < 50; i++) {
				Thread.sleep(50);
				results.add(exec.submit((Callable<AtomicInteger> & Serializable) () -> {
					Thread.sleep(5000);
					return new AtomicInteger();
				}));
			}
			for (Future<AtomicInteger> res : results)
				res.get();
			long time = System.currentTimeMillis() - start;
			boolean success = time < 15000 && time > 7500;
			System.out.printf("Time: %.3f %s%n", ((double) time)/1000, success ? "" : "FAIL");
			Assert.assertTrue(success);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	// Java process options testing.
	@Test
	public void test23() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 23);
		SimpleJavaProcessOptions options = new SimpleJavaProcessOptions(2, 4, 256, 0);
		StandardJavaProcessPool exec = new StandardJavaProcessPool(options,
				5, 5, 0, false);
		try {
			Assert.assertTrue(exec.getJavaProcessOptions() == options);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	// Java process testing.
	@Test
	public void test24() throws IOException, InterruptedException, ClassNotFoundException {
		String testInput = String.format("%s%n%s%n%s%n%s%n%s%n%s%n", "", Conversion.toString("test"),
				Conversion.toString((Callable<Long> & Serializable) () -> Math.round(Math.E)),
				Conversion.toString((Callable<Object> & Serializable) () -> {
					throw new Exception("test");
				}), "test", JavaProcess.STOP_REQUEST);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				ByteArrayInputStream in = new ByteArrayInputStream(testInput.getBytes())) {
			System.setOut(new PrintStream(out));
			System.setIn(in);
			JavaProcess.main(new String[0]);
			String[] lines = out.toString().split(System.lineSeparator());
			Assert.assertTrue(lines.length == 5);
			Assert.assertTrue(JavaProcess.STARTUP_SIGNAL.equals(lines[0]));
			Assert.assertTrue(((Long) Conversion.toObject(lines[1])) == 3);
			Assert.assertTrue(lines[2].startsWith(JavaProcess.ERROR_PREFIX));
			Assert.assertTrue("test".equals(((Exception) Conversion.toObject(lines[2]
					.substring(JavaProcess.ERROR_PREFIX.length()))).getMessage()));
			Assert.assertTrue(lines[3].startsWith(JavaProcess.ERROR_PREFIX));
			Assert.assertTrue(JavaProcess.STOP_SIGNAL.equals(lines[4]));
		}
	}
	
}
