package net.viktorc.pp4j.impl.jpp;

import java.io.Serializable;
import java.lang.Runnable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import net.viktorc.pp4j.api.jpp.ProcessPoolExecutorService;

/**
 * A test class for the Java process based process pool executor implementation.
 * 
 * @author Viktor Csomor
 *
 */
public class JavaProcessPoolExecutorTest {

	@Test
	public void test01() throws InterruptedException, ExecutionException {
		ProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(
				new SimpleJavaProcessOptions(2, 4, 256, 0), 1, 1, 0, false);
		try {
			List<Future<?>> futures = new ArrayList<>();
			long start = System.currentTimeMillis();
			AtomicInteger j = new AtomicInteger(2);
			for (int i = 0; i < 1; i++) {
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
			System.out.printf("It took: %.3f.%n", ((float) (System.currentTimeMillis() - start))/1000);
		} finally {
			exec.shutdown();
			exec.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	@Test
	public void test02() {
		
	}
	
}
