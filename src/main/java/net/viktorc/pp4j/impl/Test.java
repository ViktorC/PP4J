package net.viktorc.pp4j.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.viktorc.pp4j.api.jpp.ProcessPoolExecutorService;
import net.viktorc.pp4j.api.jpp.JavaProcessOptions.JVMArch;
import net.viktorc.pp4j.api.jpp.JavaProcessOptions.JVMType;
import net.viktorc.pp4j.impl.jpp.JavaProcessPoolExecutorService;
import net.viktorc.pp4j.impl.jpp.SimpleJavaProcessOptions;

public class Test {

	public static void main(String[] args) throws InterruptedException, ExecutionException {
		ProcessPoolExecutorService pool = new JavaProcessPoolExecutorService(
				new SimpleJavaProcessOptions(JVMArch.BIT_64, JVMType.CLIENT, 2, 8, 256, 60000),
				10, 20, 2, false);
		Random rand = new Random();
		List<Future<Long>> results = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			results.add(pool.submit((Callable<Long> & Serializable) () -> {
				Thread.sleep(1000);
				return rand.nextLong();
			}));
		}
		for (Future<Long> res : results)
			System.out.println(res.get());
		pool.shutdown();
		pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
	}

}
