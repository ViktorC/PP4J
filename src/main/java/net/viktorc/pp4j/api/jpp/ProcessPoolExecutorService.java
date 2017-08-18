package net.viktorc.pp4j.api.jpp;

import java.util.concurrent.ExecutorService;

import net.viktorc.pp4j.api.ProcessPool;

/**
 * An interface that extends both the {@link net.viktorc.pp4j.api.ProcessPool} and 
 * {@link java.util.concurrent.ExecutorService} interfaces.
 * 
 * @author Viktor Csomor
 *
 */
public interface ProcessPoolExecutorService extends ProcessPool, ExecutorService {
	
}
