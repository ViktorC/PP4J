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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * The class whose main method is run as a separate process by the Java process pool executor.
 *
 * @author Viktor Csomor
 */
public class JavaProcess {

  private static volatile boolean exit;

  /**
   * The method executed as a separate process. It listens to its standard in for encoded and serialized {@link Callable} instances,
   * which it decodes, deserializes, and executes on receipt. The return value is normally output to its stdout stream in the form of the
   * serialized and encoded return values of the <code>Callable</code> instances. If an exception occurs, it is serialized, encoded, and
   * output to the stdout stream as well.
   *
   * @param args They are ignored for now.
   */
  public static void main(String[] args) {
    PrintStream originalOut = System.out;
    try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, JavaObjectCodec.CHARSET));
        PrintStream dummyOut = new DummyPrintStream();
        PrintStream dummyErr = new DummyPrintStream()) {
      /* As the process' stderr is redirected to the stdout, there is no need for a reference to this stream; neither should the
       * submissions be able to print to stdout through the System.err stream. */
      redirectStdErr(dummyErr);
      // Send the startup signal.
      System.out.println(JavaObjectCodec.getInstance().encode(new Response(ResponseType.STARTUP_SUCCESS)));
      while (!exit) {
        String line = in.readLine();
        if (line == null) {
          return;
        }
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        Object input = JavaObjectCodec.getInstance().decode(line);
        executeTask((Callable<?>) input, originalOut, dummyOut);
      }
    } catch (Throwable e) {
      redirectStdOut(originalOut);
      try {
        System.out.println(JavaObjectCodec.getInstance().encode(new Response(ResponseType.PROCESS_FAILURE, e)));
      } catch (IOException ex) {
        // Not much else left to do.
      }
    } finally {
      exit = false;
    }
  }

  /**
   * Invoking this method from within a task results in the main method exiting upon the iteration following the execution of the task.
   */
  static void exit() {
    exit = true;
  }

  /**
   * Handles an execution request by executing the task and printing the response containing its result (or the exception if one is
   * thrown) to the process' standard out stream.
   *
   * @param task The task to execute.
   * @param originalOut The original standard out stream.
   * @param dummyOut The dummy stream to redirect standard out to before executing the request task.
   * @throws IOException If an error occurs while encoding the response.
   */
  private static void executeTask(Callable<?> task, PrintStream originalOut, PrintStream dummyOut) throws IOException {
    try {
      // Redirect the out stream to make sure that messages sent to it from within the task are not treated as responses.
      redirectStdOut(dummyOut);
      Object result = task.call();
      redirectStdOut(originalOut);
      System.out.println(JavaObjectCodec.getInstance().encode(new Response(ResponseType.TASK_SUCCESS, result)));
    } catch (Throwable e) {
      redirectStdOut(originalOut);
      System.out.println(JavaObjectCodec.getInstance().encode(new Response(ResponseType.TASK_FAILURE, e)));
    }
  }

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
   * An enum representing types of responses that the Java process can send back to the parent process.
   *
   * @author Viktor Csomor
   */
  public enum ResponseType {
    STARTUP_SUCCESS,
    TASK_SUCCESS,
    TASK_FAILURE,
    PROCESS_FAILURE
  }

  /**
   * A simple class to encapsulate the response of the Java process to a request.
   *
   * @author Viktor Csomor
   */
  public static class Response implements Serializable {

    private final ResponseType type;
    private final Object result;
    private final Throwable error;

    /**
     * Constructs a <code>Response</code> instance using the specified parameters.
     *
     * @param type The type of the response.
     * @param result The optional result.
     * @param error The optional error.
     */
    Response(ResponseType type, Object result, Throwable error) {
      this.type = type;
      this.result = result;
      this.error = error;
    }

    /**
     * Constructs a <code>Response</code> instance using the specified parameters.
     *
     * @param type The type of the response.
     * @param result The optional result.
     */
    Response(ResponseType type, Object result) {
      this(type, result, null);
    }

    /**
     * Constructs a <code>Response</code> instance using the specified parameters.
     *
     * @param type The type of the response.
     * @param error The optional error.
     */
    Response(ResponseType type, Throwable error) {
      this(type, null, error);
    }

    /**
     * Constructs a <code>Response</code> instance without a result or an error.
     *
     * @param type The type of the response.
     */
    Response(ResponseType type) {
      this(type, null, null);
    }

    /**
     * Returns the type of the response.
     *
     * @return The type of the response.
     */
    public ResponseType getType() {
      return type;
    }

    /**
     * Returns the optional task result.
     *
     * @return The optional task result.
     */
    public Optional<?> getResult() {
      return Optional.ofNullable(result);
    }

    /**
     * Returns the optional <code>Throwable</code>.
     *
     * @return The optional error instance caught in the process.
     */
    public Optional<Throwable> getError() {
      return Optional.ofNullable(error);
    }

  }

}
