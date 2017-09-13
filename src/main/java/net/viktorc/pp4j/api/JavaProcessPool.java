package net.viktorc.pp4j.api;

import java.util.concurrent.ExecutorService;

/**
 * An interface for Java process pools that extends both the {@link net.viktorc.pp4j.api.ProcessPool} 
 * and {@link java.util.concurrent.ExecutorService} interfaces.
 * 
 * @author Viktor Csomor
 *
 */
public interface JavaProcessPool extends ProcessPool, ExecutorService {
	
}
