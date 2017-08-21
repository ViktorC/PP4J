package net.viktorc.pp4j.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.junit.Test;

import net.viktorc.pp4j.ProcessPools;

/**
 * A test class for the Java process based process pool executor implementation.
 * 
 * @author Viktor Csomor
 *
 */
public class JavaProcessPoolExecutorTest {

	@Test
	public void test01() {
		try {
			ExecutorService exec = ProcessPools.newCachedProcessPoolExecutorService();
			try {
				List<Future<String>> futures = new ArrayList<>();
				long start = System.currentTimeMillis();
				for (int i = 0; i < 1; i++) {
					futures.add(exec.submit(() -> {
						Random rand = new Random();
						Thread.sleep(5000);
						return String.format("This is the value: %s.", Long.toHexString(rand.nextLong()));
					}));
				}
				for (Future<String> f : futures) {
					try {
						System.out.println(f.get());
					} catch (InterruptedException | ExecutionException e) {
						e.printStackTrace();
					}
				}
				System.out.printf("It took: %.2f.%n", ((float) (System.currentTimeMillis() - start))/1000);
			} finally {
				exec.shutdown();
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
}
