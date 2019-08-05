/*
 * Copyright 2017 Viktor Csomor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.viktorc.pp4j.impl;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import net.viktorc.pp4j.api.JavaProcessExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sub-class of {@link ProcessPoolExecutor} that implements the {@link JavaProcessExecutorService} interface. It uses Java processes for
 * the implementation of multiprocessing. It communicates with the processes via their standard streams exchanging serialized and encoded
 * objects. It can send {@link Runnable} and {@link Callable} instances to the processes; and it receives the result or exception object
 * serialized and encoded into a string.
 *
 * @author Viktor Csomor
 */
public class JavaProcessPoolExecutor extends ProcessPoolExecutor implements JavaProcessExecutorService {

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaProcessPoolExecutor.class);

  /**
   * Constructs a Java process pool executor using the specified parameters.
   *
   * @param processManagerFactory The java process manager factory.
   * @param minPoolSize The minimum size of the process pool.
   * @param maxPoolSize The maximum size of the process pool.
   * @param reserveSize The number of available processes to keep in the pool.
   * @param threadKeepAliveTime The number of milliseconds of idleness after which threads are terminated if the sizes of the thread pools
   * exceed their core sizes.
   * @param <T> The type of the startup task.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   * @throws IllegalArgumentException If <code>options</code> is <code>null</code>, the minimum pool size is less than 0, or the maximum
   * pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum pool size.
   */
  public <T extends Runnable & Serializable> JavaProcessPoolExecutor(JavaProcessManagerFactory<T> processManagerFactory, int minPoolSize,
      int maxPoolSize, int reserveSize, long threadKeepAliveTime) throws InterruptedException {
    super(processManagerFactory, minPoolSize, maxPoolSize, reserveSize, threadKeepAliveTime);
  }

  /**
   * Constructs a Java process pool executor using the specified parameters.
   *
   * @param processManagerFactory The java process manager factory.
   * @param minPoolSize The minimum size of the process pool.
   * @param maxPoolSize The maximum size of the process pool.
   * @param reserveSize The number of available processes to keep in the pool.
   * @param <T> The type of the startup task.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   * @throws IllegalArgumentException If <code>options</code> is <code>null</code>, the minimum pool size is less than 0, or the maximum
   * pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum pool size.
   */
  public <T extends Runnable & Serializable> JavaProcessPoolExecutor(JavaProcessManagerFactory<T> processManagerFactory, int minPoolSize,
      int maxPoolSize, int reserveSize) throws InterruptedException {
    super(processManagerFactory, minPoolSize, maxPoolSize, reserveSize);
  }

  /**
   * It executes a serializable {@link java.util.concurrent.Callable} instance with a serializable return type in one of the processes of
   * the pool and returns its return value. If the implementation contains non-serializable, non-transient fields, or the return type is not
   * serializable, the method fails.
   *
   * @param task The task to execute.
   * @param terminateProcessAfterwards Whether the process that executes the task should be terminated afterwards.
   * @param <T> The serializable return type variable of the <code>Callable</code>
   * @param <S> A serializable <code>Callable</code> instance with the return type <code>T</code>.
   * @return A {@link Future} instance associated with the return value of the task.
   * @throws IOException If the serialization fails.
   * @throws NotSerializableException If some object to be serialized does not implement the {@link Serializable} interface.
   */
  public <T extends Serializable, S extends Callable<T> & Serializable> Future<T> submitExplicitly(S task,
      boolean terminateProcessAfterwards) throws IOException {
    return submit(new JavaSubmission<>(task), terminateProcessAfterwards);
  }

  @Override
  public void execute(Runnable command) {
    Future<?> future = submit(command);
    try {
      future.get();
    } catch (ExecutionException e) {
      throw new UncheckedExecutionException(e);
    } catch (InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new UncheckedExecutionException(e);
    }
  }

  @Override
  public <T> Future<T> submit(Callable<T> task, boolean terminateProcessAfterwards) {
    try {
      return new CastFuture<>(submitExplicitly(new SerializableCallable<>((Callable<T> & Serializable) task), terminateProcessAfterwards));
    } catch (Exception e) {
      throw new RejectedExecutionException(e);
    }
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result, boolean terminateProcessAfterwards) {
    try {
      return new CastFuture<>(submitExplicitly(new SerializableCallable<>((Callable<T> & Serializable)
          () -> {
            task.run();
            return result;
          }, task), terminateProcessAfterwards));
    } catch (Exception e) {
      throw new RejectedExecutionException(e);
    }
  }

  @Override
  public Future<?> submit(Runnable task, boolean terminateProcessAfterwards) {
    try {
      return submit(task, null, terminateProcessAfterwards);
    } catch (Exception e) {
      throw new RejectedExecutionException(e);
    }
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    List<Future<T>> futures = new ArrayList<>();
    for (Callable<T> t : tasks) {
      futures.add(submit(t));
    }
    for (Future<T> f : futures) {
      try {
        if (!f.isDone()) {
          f.get();
        }
      } catch (ExecutionException | CancellationException e) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
    return futures;
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
    List<Future<T>> futures = new ArrayList<>();
    for (Callable<T> t : tasks) {
      futures.add(submit(t));
    }
    long waitTimeNs = unit.toNanos(timeout);
    for (int i = 0; i < futures.size(); i++) {
      Future<T> f = futures.get(i);
      long start = System.nanoTime();
      try {
        if (!f.isDone()) {
          f.get(waitTimeNs, TimeUnit.NANOSECONDS);
        }
      } catch (ExecutionException | CancellationException e) {
        LOGGER.warn(e.getMessage(), e);
      } catch (TimeoutException e) {
        for (int j = i; j < futures.size(); j++) {
          futures.get(j).cancel(true);
        }
        break;
      } finally {
        waitTimeNs -= (System.nanoTime() - start);
      }
    }
    return futures;
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    ExecutionException execException = null;
    for (Future<T> f : invokeAll(tasks)) {
      try {
        return f.get();
      } catch (ExecutionException e) {
        execException = e;
      } catch (CancellationException e) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
    if (execException == null) {
      throw new ExecutionException(new Exception("No task completed successfully"));
    }
    throw execException;
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    ExecutionException execException = null;
    for (Future<T> f : invokeAll(tasks, timeout, unit)) {
      try {
        return f.get();
      } catch (ExecutionException e) {
        execException = e;
      } catch (CancellationException e) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
    if (execException == null) {
      throw new TimeoutException();
    }
    throw execException;
  }

  /**
   * See {@link java.util.concurrent.ExecutorService#shutdownNow()}. It is equivalent to {@link #forceShutdown()} with the only difference
   * being that this method filters and converts the returned submissions to a list of {@link Runnable} instances excluding
   * {@link Callable} based submissions.
   */
  @Override
  public List<Runnable> shutdownNow() {
    return super.forceShutdown().stream()
        .filter(s -> s instanceof JavaSubmission)
        .map(s -> ((SerializableCallable<?, ?>) ((JavaSubmission<?, ?>) s).getTask()).runnablePart)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * An implementation of the {@link Future} interface for wrapping a <code>Future</code> instance into one with a return type that is a
   * sub-type of that of the wrapped instance.
   *
   * @param <T> The return type of the original <code>Future</code> instance.
   * @param <S> A subtype of <code>T</code>, the return type of the wrapper <code>Future</code> instance.
   * @author Viktor Csomor
   */
  private static class CastFuture<T, S extends T> implements Future<T> {

    final Future<S> origFuture;

    /**
     * Constructs the wrapper object for the specified <code>Future</code> instance.
     *
     * @param origFuture The <code>Future</code> instance to wrap.
     */
    CastFuture(Future<S> origFuture) {
      this.origFuture = origFuture;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return origFuture.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
      return origFuture.isCancelled();
    }

    @Override
    public boolean isDone() {
      return origFuture.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      return origFuture.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      return origFuture.get(timeout, unit);
    }

  }

  /**
   * A wrapper class implementing the {@link Callable} interface for turning a serializable <code>Callable</code> instance with a not
   * explicitly serializable return type into a serializable instance with an explicitly serializable return type.
   *
   * @param <T> The serializable return type.
   * @param <S> The serializable <code>Callable</code> implementation with a not explicitly serializable return type.
   * @author Viktor Csomor
   */
  private static class SerializableCallable<T extends Serializable, S extends Callable<? super T> & Serializable>
      implements Callable<T>, Serializable {

    final Callable<T> callable;
    final Runnable runnablePart;

    /**
     * Constructs a serializable <code>Callable</code> instance with a serializable return type based on the specified serializable
     * <code>Callable</code> instance with a not explicitly serializable return type.
     *
     * @param callable The <code>Callable</code> to wrap.
     * @param runnablePart The optional <code>Runnable</code> part of the <code>Callable</code> instance in case it consists of a
     * <code>Runnable</code> and a return object.
     */
    SerializableCallable(S callable, Runnable runnablePart) {
      this.callable = (Callable<T> & Serializable) callable;
      this.runnablePart = runnablePart;
    }

    /**
     * Constructs a serializable <code>Callable</code> instance with a serializable return type based on the specified serializable
     * <code>Callable</code> instance with a not explicitly serializable return type.
     *
     * @param callable The <code>Callable</code> to wrap.
     */
    SerializableCallable(S callable) {
      this(callable, null);
    }

    @Override
    public T call() throws Exception {
      return callable.call();
    }

  }

  /**
   * An exception thrown if the execution of Java task fails is or interrupted.
   *
   * @author Viktor Csomor
   */
  public static class UncheckedExecutionException extends RuntimeException {

    /**
     * Constructs a wrapper for the specified exception.
     *
     * @param e The source exception.
     */
    private UncheckedExecutionException(Throwable e) {
      super(e);
    }

  }

}