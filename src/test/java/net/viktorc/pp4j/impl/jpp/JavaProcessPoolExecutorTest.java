package net.viktorc.pp4j.impl.jpp;

import java.io.Serializable;
import java.lang.Runnable;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import net.viktorc.pp4j.api.jpp.JavaProcessOptions.JVMArch;
import net.viktorc.pp4j.api.jpp.JavaProcessOptions.JVMType;
import net.viktorc.pp4j.api.jpp.ProcessPoolExecutorService;

/**
 * A test class for the Java process based process pool executor implementation.
 * 
 * @author Viktor Csomor
 *
 */
public class JavaProcessPoolExecutorTest {

	@SuppressWarnings("unused")
	private <T> List<Entry<T,Long>> test(ProcessPoolExecutorService exec, Callable<T> task,
			int reps, int freq) throws InterruptedException, ExecutionException {
		List<Entry<T,Long>> times = new ArrayList<>();
		CompletionService<T> service = new ExecutorCompletionService<>(exec);
		Map<Future<T>,Entry<Integer,Long>> map = new HashMap<>();
		long interval = freq == 0 ? 0 : 1000/freq;
		for (int i = 0; i < reps; i++) {
			if (i != 0) {
				if (interval != 0)
					Thread.sleep(interval);
			}
			Future<T> f = service.submit(task);
			map.put(f, new SimpleEntry<>(i, System.nanoTime()));
		}
		while (!map.isEmpty()) {
			Future<T> f = service.take();
			Entry<Integer,Long> val = map.get(f);
			times.add(val.getKey(), new SimpleEntry<>(f.get(), Math.round(((double)
					(System.nanoTime() - val.getValue()))/1000000)));
			map.remove(f);
		}
		return times;
	}
	@Test
	public void test01() throws InterruptedException, ExecutionException {
		ProcessPoolExecutorService exec = new JavaProcessPoolExecutorService(
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
			exec.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
		}
	}
	
}
