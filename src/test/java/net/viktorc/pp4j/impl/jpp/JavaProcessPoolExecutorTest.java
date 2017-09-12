package net.viktorc.pp4j.impl.jpp;

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

import net.viktorc.pp4j.api.jpp.JavaProcessOptions.JVMArch;
import net.viktorc.pp4j.api.jpp.JavaProcessOptions.JVMType;

/**
 * A test class for the Java process based process pool executor implementation.
 * 
 * @author Viktor Csomor
 *
 */
public class JavaProcessPoolExecutorTest {

	private static final String TEST_TITLE_FORMAT = "%nTest %d%n" +
			"-------------------------------------------------%n";
	
	@Rule
	public final ExpectedException exceptionRule = ExpectedException.none();
	
	// Startup testing
	@Test
	public void test01() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 1);
		long start = System.currentTimeMillis();
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 1, 1,
				0, false);
		try {
			long time = System.currentTimeMillis() - start;
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time <= 750);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test02() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 2);
		long start = System.currentTimeMillis();
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 0),
				1, 1, 0, false);
		try {
			long time = System.currentTimeMillis() - start;
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time <= 500);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test03() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 3);
		long start = System.currentTimeMillis();
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.SERVER, 256, 4096, 4096,
				5000), 1, 1, 0, false);
		try {
			long time = System.currentTimeMillis() - start;
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time <= 500);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test04() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 4);
		long start = System.currentTimeMillis();
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 10, 15,
				5, false);
		try {
			long time = System.currentTimeMillis() - start;
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time <= 2000);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test05() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 5);
		long start = System.currentTimeMillis();
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 4, 256, 0),
				10, 15, 5, false);
		try {
			long time = System.currentTimeMillis() - start;
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time <= 2000);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test06() throws InterruptedException {
		System.out.printf(TEST_TITLE_FORMAT, 6);
		long start = System.currentTimeMillis();
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.SERVER, 256, 4096, 4096,
				5000), 10, 15, 5, false);
		try {
			long time = System.currentTimeMillis() - start;
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time <= 2000);
		} finally {
			exec.shutdownNow();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	// Submission testing.
	@Test
	public void test07() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 7);
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 5, 5,
				0, false);
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
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(
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
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 1, 1,
				0, false);
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
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 1, 1,
				0, false);
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
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time < 5300);
			Assert.assertTrue(time > 4995);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	// Invocation testing.
	@Test
	public void test11() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 11);
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 2, 2,
				0, false);
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
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time < 4300);
			Assert.assertTrue(time > 3995);
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
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 1, 1,
				0, false);
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
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time < 6300);
			Assert.assertTrue(time > 5995);
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
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 2, 2,
				0, false);
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
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time < 3300);
			Assert.assertTrue(time > 2995);
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
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 1, 1,
				0, false);
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
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time < 3300);
			Assert.assertTrue(time > 2995);
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
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 2, 2,
				0, false);
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
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time < 4300);
			Assert.assertTrue(time > 3995);
			Assert.assertTrue(result == 169 || result == 2197);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test16() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 16);
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 2, 2,
				0, false);
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
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time < 2300);
			Assert.assertTrue(time > 1995);
			Assert.assertTrue(result == 169);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test17() throws InterruptedException, ExecutionException, TimeoutException {
		System.out.printf(TEST_TITLE_FORMAT, 17);
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 2, 2,
				0, false);
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
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time < 3300);
			Assert.assertTrue(time > 2995);
			Assert.assertTrue(result == 169);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test18() throws InterruptedException, ExecutionException, TimeoutException {
		System.out.printf(TEST_TITLE_FORMAT, 18);
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 2, 2,
				0, false);
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
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 1, 1,
				0, false);
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
			long time = System.currentTimeMillis() - start;
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue((queuedTasks.get(0) == r1 && queuedTasks.get(1) == r2) ||
					(queuedTasks.get(0) == r2 && queuedTasks.get(1) == r1));
			Assert.assertTrue(time < 20);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	// Task and result exchange performance testing.
	@Test
	public void test20() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 20);
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(null, 1, 1,
				0, false);
		try {
			long start = System.currentTimeMillis();
			AtomicInteger res = exec.submit((Callable<AtomicInteger> & Serializable) () -> {
				Thread.sleep(2000);
				return new AtomicInteger(13);
			}).get();
			long time = System.currentTimeMillis() - start;
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(res.get() == 13);
			Assert.assertTrue(time < 2300);
			Assert.assertTrue(time > 1995);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test21() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 21);
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(
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
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time < 9000);
			Assert.assertTrue(time > 7495);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test22() throws InterruptedException, ExecutionException {
		System.out.printf(TEST_TITLE_FORMAT, 22);
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(
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
			System.out.printf("Time: %.3f%n", ((double) time)/1000);
			Assert.assertTrue(time < 12000);
			Assert.assertTrue(time > 7500);
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
		JavaProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(options,
				5, 5, 0, false);
		try {
			Assert.assertTrue(exec.getJavaProcessOptions() == options);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	
}
