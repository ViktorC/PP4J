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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.JavaProcessExecutorService;
import net.viktorc.pp4j.api.JavaProcessOptions;
import net.viktorc.pp4j.api.JavaProcessOptions.JVMArch;
import net.viktorc.pp4j.api.JavaProcessOptions.JVMType;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.Submission;
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

  /**
   * Constructs a Java process pool executor using the specified parameters.
   *
   * @param options The options for the "java" program used to create the new JVM.
   * @param minPoolSize The minimum size of the process pool.
   * @param maxPoolSize The maximum size of the process pool.
   * @param reserveSize The number of available processes to keep in the pool.
   * @param keepAliveTime The duration of continuous idleness, in milliseconds, after which the processes are to be terminated. A process
   * is considered idle if it is started up and not processing any submission. If the value of this parameter is <code>null</code>, the
   * life spans of the processes will not be limited.
   * @param startupTask The task to execute in each process on startup, before the process starts accepting submissions. If it is
   * <code>null</code>, no taks are executed on startup.
   * @param <T> The type of the startup task.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   * @throws IllegalArgumentException If <code>options</code> is <code>null</code>, the minimum pool size is less than 0, or the maximum
   * pool size is less than the minimum pool size or 1, or the reserve size is less than 0 or greater than the maximum pool size.
   */
  public <T extends Runnable & Serializable> JavaProcessPoolExecutor(JavaProcessOptions options, int minPoolSize, int maxPoolSize,
      int reserveSize, Long keepAliveTime, T startupTask) throws InterruptedException {
    super(new JavaProcessManagerFactory<>(options, startupTask, keepAliveTime), minPoolSize, maxPoolSize, reserveSize);
  }

  /**
   * Returns the Java process options associated with the pool.
   *
   * @return The Java process options used to create the processes.
   */
  public JavaProcessOptions getJavaProcessOptions() {
    return ((JavaProcessManagerFactory<?>) getProcessManagerFactory()).options;
  }

  /**
   * Serializes the specified object into a string and encodes it using Base64.
   *
   * @param object The object to serialize and encode.
   * @return The serialized and encoded object as a string.
   * @throws IOException If the serialization fails.
   */
  static String convertToString(Object object) throws IOException {
    try (ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
        ObjectOutputStream objectOutput = new ObjectOutputStream(byteArrayOut)) {
      objectOutput.writeObject(object);
      return Base64.getEncoder().encodeToString(byteArrayOut.toByteArray());
    }
  }

  /**
   * Decodes the string and deserializes it into an object.
   *
   * @param string The Base64-encoded string to deserialize.
   * @return The decoded and deserialized string as an object.
   * @throws IOException If the deserialization fails.
   * @throws ClassNotFoundException If the deserialization fails due to the class of the object not having been found.
   * @throws IllegalArgumentException If the string is not a valid Base64 string.
   */
  static Object convertToObject(String string) throws IOException, ClassNotFoundException {
    byte[] bytes = Base64.getDecoder().decode(string);
    try (ObjectInputStream objectInput = new ObjectInputStream(
        new ByteArrayInputStream(bytes))) {
      return objectInput.readObject();
    }
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
    try {
      submit(command).get();
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
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
        logger.warn(e.getMessage(), e);
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
        logger.warn(e.getMessage(), e);
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
        logger.warn(e.getMessage(), e);
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
        logger.warn(e.getMessage(), e);
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
        .map(s -> ((SerializableCallable<?, ?>) ((JavaSubmission<?, ?>) s).task).runnablePart)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * An implementation of the {@link ProcessManagerFactory} for the creation of {@link JavaProcessManager} instances using a single
   * {@link ProcessBuilder} instance.
   *
   * @param <T> A type variable implementing the {@link Runnable} and {@link Serializable} interfaces that defines the base class of the
   * startup tasks of the created {@link JavaProcessManager} instances.
   * @author Viktor Csomor
   */
  private static class JavaProcessManagerFactory<T extends Runnable & Serializable> implements ProcessManagerFactory {

    JavaProcessOptions options;
    T startupTask;
    Long keepAliveTime;

    /**
     * Constructs an instance based on the specified JVM options and <code>keepAliveTime</code> which are used for the creation of all
     * processes of the pool.
     *
     * @param options The JVM options for starting the Java process.
     * @param startupTask The task to execute in each process on startup, before the process starts accepting submissions. If it is
     * <code>null</code>, no taks are executed on startup.
     * @param keepAliveTime The number of milliseconds after which idle processes are terminated.
     * @throws IllegalArgumentException If the <code>options</code> is <code>null</code> or contains invalid values.
     */
    JavaProcessManagerFactory(JavaProcessOptions options, T startupTask, Long keepAliveTime) {
      if (options == null) {
        throw new IllegalArgumentException("The options argument cannot be null");
      }
      if (options.getInitHeapSizeMb().isPresent() && options.getInitHeapSizeMb().get() <= 0) {
        throw new IllegalArgumentException("Initial heap size must be greater than 0");
      }
      if (options.getMaxHeapSizeMb().isPresent() && options.getMaxHeapSizeMb().get() <= 0) {
        throw new IllegalArgumentException("Maximum heap size must be greater than 0");
      }
      if (options.getStackSizeKb().isPresent() && options.getStackSizeKb().get() <= 0) {
        throw new IllegalArgumentException("Stack size must be greater than 0");
      }
      if (options.getClassPath().isPresent() && options.getClassPath().get().isEmpty()) {
        throw new IllegalArgumentException("Class path cannot be an empty string");
      }
      this.options = options;
      this.startupTask = startupTask;
      this.keepAliveTime = keepAliveTime;
    }

    @Override
    public ProcessManager newProcessManager() {
      String javaCommand = options.getJavaLauncherCommand();
      List<String> javaOptions = new ArrayList<>();
      options.getClassPath().ifPresent(v -> {
        javaOptions.add("-cp");
        javaOptions.add(v);
      });
      options.getArch().ifPresent(v -> javaOptions.add(v == JVMArch.BIT_32 ? "-d32" : "-d64"));
      options.getType().ifPresent(v -> javaOptions.add(v == JVMType.CLIENT ? "-client" : "-server"));
      options.getInitHeapSizeMb().ifPresent(v -> javaOptions.add(String.format("-Xms%dm", v)));
      options.getMaxHeapSizeMb().ifPresent(v -> javaOptions.add(String.format("-Xmx%dm", v)));
      options.getStackSizeKb().ifPresent(v -> javaOptions.add(String.format("-Xss%dk", v)));
      String className = JavaProcess.class.getName();
      List<String> args = new ArrayList<>();
      args.add(javaCommand);
      args.addAll(javaOptions);
      args.add(className);
      ProcessBuilder builder = new ProcessBuilder(args);
      // Redirect the error stream to reduce the number of used threads per process.
      builder.redirectErrorStream(true);
      return new JavaProcessManager<>(builder, keepAliveTime, startupTask);
    }

  }

  /**
   * A sub-class of {@link AbstractProcessManager} for the management of process instances of the {@link JavaProcess} class.
   *
   * @param <T> A type variable implementing the {@link Runnable} and {@link Serializable} interfaces that defines the base class of the
   * startup task.
   * @author Viktor Csomor
   */
  private static class JavaProcessManager<T extends Runnable & Serializable> extends AbstractProcessManager {

    static final Logger LOGGER = LoggerFactory.getLogger(JavaProcessManager.class);

    T startupTask;

    /**
     * Constructs an instance using the specified <code>builder</code> and <code>keepAliveTime</code>.
     *
     * @param builder The <code>ProcessBuilder</code> to use for starting the Java processes.
     * @param keepAliveTime The number of milliseconds of idleness after which the processes should be terminated. If it is
     * <code>null</code>, the life-cycle of processes will not be limited based on idleness.
     */
    JavaProcessManager(ProcessBuilder builder, Long keepAliveTime, T startupTask) {
      super(builder, keepAliveTime);
      this.startupTask = startupTask;
    }

    @Override
    public boolean startsUpInstantly() {
      return false;
    }

    @Override
    public boolean isStartedUp(String outputLine, boolean error) {
      if (!error) {
        try {
          Object output = convertToObject(outputLine);
          return output == JavaProcess.Signal.READY;
        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
          LOGGER.trace(e.getMessage(), e);
        }
      }
      return false;
    }

    @Override
    public Optional<Submission<?>> getInitSubmission() {
      if (startupTask == null) {
        return Optional.empty();
      }
      Optional<Submission<?>> initSubmission;
      try {
        // Avoid having to have the process manager serialized.
        T startupTask = this.startupTask;
        initSubmission = Optional.of(new JavaSubmission<>((Callable<Integer> & Serializable) () -> {
          startupTask.run();
          return null;
        }));
      } catch (IOException e) {
        LOGGER.warn(e.getMessage(), e);
        return Optional.empty();
      }
      return initSubmission;
    }

    @Override
    public Optional<Submission<?>> getTerminationSubmission() {
      try {
        String terminationCommand = convertToString(JavaProcess.Request.TERMINATE);
        return Optional.of(new SimpleSubmission(new SimpleCommand(terminationCommand,
            (command, outputLine) -> {
              try {
                Object output = convertToObject(outputLine);
                return output == JavaProcess.Signal.TERMINATED;
              } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
                LOGGER.trace(e.getMessage(), e);
                return false;
              }
            },
            (command, outputLine) -> false)));
      } catch (IOException e) {
        LOGGER.warn(e.getMessage(), e);
        return Optional.empty();
      }
    }

    @Override
    public Charset getEncoding() {
      return JavaProcess.CHARSET;
    }

  }

  /**
   * An implementation of {@link Submission} for serializable {@link Callable} instances to submit in Java process. It serializes, encodes,
   * and sends the <code>Callable</code> to the process for execution. It also looks for the serialized and encoded return value of the
   * <code>Callable</code>, and for a serialized and encoded {@link Throwable} instance output to the stderr stream in case of an error.
   *
   * @param <T> The serializable return type variable of the <code>Callable</code>
   * @param <S> A serializable <code>Callable</code> instance with the return type <code>T</code>.
   * @author Viktor Csomor
   */
  private static class JavaSubmission<T extends Serializable, S extends Callable<T> & Serializable> implements Submission<T> {

    static final Logger LOGGER = LoggerFactory.getLogger(JavaProcessManager.class);

    final S task;
    final String command;
    volatile T result;
    volatile Throwable error;

    /**
     * Creates a submission for the specified {@link Callable}.
     *
     * @param task The task to execute.
     * @throws IOException If the encoding of the serialized task fails.
     */
    JavaSubmission(S task) throws IOException {
      this.task = task;
      command = convertToString(task);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Command> getCommands() {
      return Collections.singletonList(new SimpleCommand(command,
          (command, outputLine) -> {
            Object output;
            try {
              output = convertToObject(outputLine);
            } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
              LOGGER.trace(e.getMessage(), e);
              return false;
            }
            if (output instanceof JavaProcess.Response) {
              JavaProcess.Response response = (JavaProcess.Response) output;
              if (response.isError()) {
                error = (Throwable) response.getResult();
              } else {
                result = (T) response.getResult();
              }
              return true;
            }
            return false;
          },
          (command, outputLine) -> {
            // It cannot happen, as stderr is redirected.
            return true;
          }));
    }

    @Override
    public Optional<T> getResult() throws ExecutionException {
      if (error != null) {
        throw new ExecutionException(error);
      }
      return Optional.ofNullable(result);
    }

    @Override
    public String toString() {
      return task.toString();
    }

  }

  /**
   * An implementation of the {@link Future} interface for wrapping a <code>Future</code> instance into a <code>Future</code> object with
   * a return type that is a sub-type of that of the wrapped instance.
   *
   * @param <T> The return type of the original <code>Future</code> instance.
   * @param <S> A subtype of <code>T</code>; the return type of the wrapper <code>Future</code> instance.
   * @author Viktor Csomor
   */
  private static class CastFuture<T, S extends T> implements Future<T> {

    final Future<S> origFuture;

    /**
     * Constructs the wrapper object for the specified <code>Future</code> instance.
     *
     * @param origFuture The  <code>Future</code> instance to wrap.
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
   * explicitly serializable return type into a serializable <code>Callable</code> instance with an explicitly serializable return type.
   *
   * @param <T> The serializable return type.
   * @param <S> The serializable <code>Callable</code> implementation with a not explicitly serializable return type.
   * @author Viktor Csomor
   */
  private static class SerializableCallable<T extends Serializable, S extends Callable<? super T> & Serializable>
      implements Callable<T>, Serializable {

    static final long serialVersionUID = -5418713087898561239L;

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
   * The class whose main method is run as a separate process by the Java process pool executor.
   *
   * @author Viktor Csomor
   */
  static class JavaProcess {

    /**
     * The character set used for communicating via the Java processes standard streams.
     */
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Redirects the process' standard out stream.
     *
     * @param outputStream The stream the standard output should be redirected to.
     */
    private static void redirectStdOut(PrintStream outputStream) {
      try {
        System.setOut(outputStream);
      } catch (SecurityException e) {
        // Ignore it.
      }
    }

    /**
     * Redirects the process' standard error stream.
     *
     * @param errorStream The stream the standard error should be redirected to.
     */
    private static void redirectStdErr(PrintStream errorStream) {
      try {
        System.setErr(errorStream);
      } catch (SecurityException e) {
        // Ignore it.
      }
    }

    /**
     * The method executed as a separate process. It listens to its standard in for encoded and serialized {@link Callable} instances,
     * which it decodes, deserializes, and executes on receipt. The return value is normally output to its stdout stream in the form of the
     * serialized and encoded return values of the <code>Callable</code> instances. If an exception occurs, it is serialized, encoded, and
     * output to the stderr stream.
     *
     * @param args They are ignored for now.
     */
    public static void main(String[] args) {
      PrintStream originalOut = System.out;
      try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, CHARSET));
          DummyPrintStream dummyOut = new DummyPrintStream();
          DummyPrintStream dummyErr = new DummyPrintStream()) {
        /* As the process' stderr is redirected to the stdout, there is no need for a
         * reference to this stream; neither should the submissions be able to print to
         * stdout through the System.err stream. */
        redirectStdErr(dummyErr);
        // Send the startup signal.
        System.out.println(convertToString(Signal.READY));
        try {
          for (;;) {
            try {
              String line = in.readLine();
              if (line == null) {
                return;
              }
              line = line.trim();
              if (line.isEmpty()) {
                continue;
              }
              Object input = convertToObject(line);
              if (input == Request.TERMINATE) {
                System.out.println(convertToString(Signal.TERMINATED));
                return;
              } else if (input instanceof Callable<?>) {
                Callable<?> c = (Callable<?>) input;
                /* Try to redirect the out stream to make sure that print
                 * statements and such do not cause the submission to be assumed
                 * done falsely. */
                redirectStdOut(dummyOut);
                Object output = c.call();
                redirectStdOut(originalOut);
                System.out.println(convertToString(new Response(false, output)));
              }
            } catch (Throwable e) {
              redirectStdOut(originalOut);
              System.out.println(convertToString(new Response(true, e)));
            }
          }
        } catch (Throwable e) {
          redirectStdOut(dummyOut);
          throw e;
        }
      } catch (Throwable e) {
        try {
          System.out.println(convertToString(new Response(true, e)));
        } catch (Exception e1) {
          // Give up all hope.
        }
      }
    }

    /**
     * A dummy implementation of {@link PrintStream} that does nothing on <code>write</code>.
     *
     * @author Viktor Csomor
     */
    private static class DummyPrintStream extends PrintStream {

      DummyPrintStream() {
        super(new OutputStream() {

          @Override
          public void write(int b) {
            // This is why it is called "DummyPrintStream".
          }
        });
      }

    }

    /**
     * An enum representing requests that can be sent to the Java process.
     */
    enum Request {
      TERMINATE
    }

    /**
     * An enum representing signals that the Java process can send back to the parent process.
     */
    enum Signal {
      READY,
      TERMINATED
    }

    /**
     * A simple class to encapsulate the response of the Java process to a task.
     */
    static class Response implements Serializable {

      private boolean error;
      private Object result;

      /**
       * Constructs a <code>Response</code> object.
       *
       * @param error Whether the result is an exception thrown during execution.
       * @param result The result of the submission.
       */
      private Response(boolean error, Object result) {
        this.error = error;
        this.result = result;
      }

      /**
       * Returns whether the result is an exception or error thrown during the execution of the task.
       *
       * @return Whether the result is an instance of {@link Throwable} thrown during the execution of the task.
       */
      boolean isError() {
        return error;
      }

      /**
       * Returns the result of the task or the {@link Throwable} thrown during the execution of the task.
       *
       * @return The result of the task.
       */
      Object getResult() {
        return result;
      }

    }

  }

}