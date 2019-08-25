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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import net.viktorc.pp4j.impl.JavaProcess.Response;
import net.viktorc.pp4j.impl.JavaProcess.ResponseType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * A unit test class for {@link JavaProcess}.
 *
 * @author Viktor Csomor
 */
public class JavaProcessTest extends TestCase {

  private PrintStream origStream;
  private Callable<?> exitTask;

  @Before
  public void setUp() {
    origStream = System.out;
    exitTask = (Callable<Serializable> & Serializable) () -> {
      JavaProcess.exit();
      return null;
    };
  }

  @Test
  public void testExitStopsMainMethod() throws InterruptedException {
    Thread thread = new Thread(() -> JavaProcess.main(null));
    thread.start();
    JavaProcess.exit();
    thread.join();
    Assert.assertTrue(true);
  }

  @Test
  public void testSendsStartupSignal() throws IOException, InterruptedException, ClassNotFoundException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      System.setOut(new PrintStream(out));
      Thread thread = new Thread(() -> JavaProcess.main(null));
      thread.start();
      JavaProcess.exit();
      thread.join();
      String output = out.toString().trim();
      Object decodedOutput = JavaObjectCodec.getInstance().decode(output);
      Assert.assertTrue(decodedOutput instanceof Response);
      Response response = (Response) decodedOutput;
      Assert.assertEquals(ResponseType.STARTUP_SUCCESS, response.getType());
      Assert.assertFalse(response.getResult().isPresent());
      Assert.assertFalse(response.getError().isPresent());
    } finally {
      System.setOut(origStream);
    }
  }

  @Test
  public void testExitInvocationSentToStdInStopsMainMethod() throws IOException, ClassNotFoundException {
    String testInput = String.format("%s%n", JavaObjectCodec.getInstance().encode(exitTask));
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(testInput.getBytes(JavaObjectCodec.CHARSET))) {
      System.setOut(new PrintStream(out));
      System.setIn(in);
      JavaProcess.main(new String[0]);
      String[] lines = out.toString().split(System.lineSeparator());
      Assert.assertEquals(2, lines.length);
      Object decodedFirstOutput = JavaObjectCodec.getInstance().decode(lines[0]);
      Assert.assertTrue(decodedFirstOutput instanceof Response);
      Response firstResponse = (Response) decodedFirstOutput;
      Assert.assertEquals(ResponseType.STARTUP_SUCCESS, firstResponse.getType());
      Object decodedSecondOutput = JavaObjectCodec.getInstance().decode(lines[1]);
      Assert.assertTrue(decodedSecondOutput instanceof Response);
      Response secondResponse = (Response) decodedSecondOutput;
      Assert.assertEquals(ResponseType.TASK_SUCCESS, secondResponse.getType());
      Assert.assertFalse(secondResponse.getResult().isPresent());
      Assert.assertFalse(secondResponse.getError().isPresent());
    } finally {
      System.setOut(origStream);
    }
  }

  @Test
  public void testTaskResultReturnedMatchesExpectations() throws IOException, ClassNotFoundException {
    Callable<Integer> task = (Callable<Integer> & Serializable) () -> -1;
    String testInput = String.format("%s%n%s%n",
        JavaObjectCodec.getInstance().encode(task),
        JavaObjectCodec.getInstance().encode(exitTask));
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(testInput.getBytes(JavaObjectCodec.CHARSET))) {
      System.setOut(new PrintStream(out));
      System.setIn(in);
      JavaProcess.main(new String[0]);
      String[] lines = out.toString().split(System.lineSeparator());
      Assert.assertEquals(3, lines.length);
      Object decodedSecondOutput = JavaObjectCodec.getInstance().decode(lines[1]);
      Assert.assertTrue(decodedSecondOutput instanceof Response);
      Response secondResponse = (Response) decodedSecondOutput;
      Assert.assertEquals(ResponseType.TASK_SUCCESS, secondResponse.getType());
      Assert.assertTrue(secondResponse.getResult().isPresent());
      Assert.assertFalse(secondResponse.getError().isPresent());
      Assert.assertEquals(-1, secondResponse.getResult().orElse(null));
    } finally {
      System.setOut(origStream);
    }
  }

  @Test
  public void testTaskErrorReturnedMatchesExpectations() throws IOException, ClassNotFoundException {
    Callable<Integer> task = (Callable<Integer> & Serializable) () -> {
      throw new IllegalArgumentException("test");
    };
    String testInput = String.format("%s%n%s%n",
        JavaObjectCodec.getInstance().encode(task),
        JavaObjectCodec.getInstance().encode(exitTask));
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(testInput.getBytes(JavaObjectCodec.CHARSET))) {
      System.setOut(new PrintStream(out));
      System.setIn(in);
      JavaProcess.main(new String[0]);
      String[] lines = out.toString().split(System.lineSeparator());
      Assert.assertEquals(3, lines.length);
      Object decodedSecondOutput = JavaObjectCodec.getInstance().decode(lines[1]);
      Assert.assertTrue(decodedSecondOutput instanceof Response);
      Response secondResponse = (Response) decodedSecondOutput;
      Assert.assertEquals(ResponseType.TASK_FAILURE, secondResponse.getType());
      Assert.assertFalse(secondResponse.getResult().isPresent());
      Assert.assertTrue(secondResponse.getError().isPresent());
      Throwable error = secondResponse.getError().get();
      Assert.assertTrue(error instanceof IllegalArgumentException);
      Assert.assertEquals("test", error.getMessage());
    } finally {
      System.setOut(origStream);
    }
  }

  @Test
  public void testTaskPrintsToRedirectedStdOut() throws IOException, ClassNotFoundException {
    Callable<Integer> task = (Callable<Integer> & Serializable) () -> {
      System.out.println("test");
      return 0;
    };
    String testInput = String.format("%s%n%s%n",
        JavaObjectCodec.getInstance().encode(task),
        JavaObjectCodec.getInstance().encode(exitTask));
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(testInput.getBytes(JavaObjectCodec.CHARSET))) {
      System.setOut(new PrintStream(out));
      System.setIn(in);
      JavaProcess.main(new String[0]);
      String[] lines = out.toString().split(System.lineSeparator());
      Assert.assertEquals(3, lines.length);
      Assert.assertTrue(JavaObjectCodec.getInstance().decode(lines[0]) instanceof Response);
      Assert.assertTrue(JavaObjectCodec.getInstance().decode(lines[1]) instanceof Response);
      Assert.assertTrue(JavaObjectCodec.getInstance().decode(lines[2]) instanceof Response);
    } finally {
      System.setOut(origStream);
    }
  }

  @Test
  public void testProcessErrorReturnedIfInputNotBase64() throws IOException, ClassNotFoundException {
    String testInput = String.format("%s%n", "hello");
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(testInput.getBytes(JavaObjectCodec.CHARSET))) {
      System.setOut(new PrintStream(out));
      System.setIn(in);
      JavaProcess.main(new String[0]);
      String[] lines = out.toString().split(System.lineSeparator());
      Assert.assertEquals(2, lines.length);
      Object decodedSecondOutput = JavaObjectCodec.getInstance().decode(lines[1]);
      Assert.assertTrue(decodedSecondOutput instanceof Response);
      Response secondResponse = (Response) decodedSecondOutput;
      Assert.assertEquals(ResponseType.PROCESS_FAILURE, secondResponse.getType());
      Assert.assertFalse(secondResponse.getResult().isPresent());
      Assert.assertTrue(secondResponse.getError().isPresent());
      Throwable error = secondResponse.getError().get();
      Assert.assertTrue(error instanceof IllegalArgumentException);
    } finally {
      System.setOut(origStream);
    }
  }

  @Test
  public void testProcessErrorReturnedIfInputNotCallable() throws IOException, ClassNotFoundException {
    String testInput = String.format("%s%n", JavaObjectCodec.getInstance().encode(new AtomicInteger(1)));
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(testInput.getBytes(JavaObjectCodec.CHARSET))) {
      System.setOut(new PrintStream(out));
      System.setIn(in);
      JavaProcess.main(new String[0]);
      String[] lines = out.toString().split(System.lineSeparator());
      Assert.assertEquals(2, lines.length);
      Object decodedSecondOutput = JavaObjectCodec.getInstance().decode(lines[1]);
      Assert.assertTrue(decodedSecondOutput instanceof Response);
      Response secondResponse = (Response) decodedSecondOutput;
      Assert.assertEquals(ResponseType.PROCESS_FAILURE, secondResponse.getType());
      Assert.assertFalse(secondResponse.getResult().isPresent());
      Assert.assertTrue(secondResponse.getError().isPresent());
      Throwable error = secondResponse.getError().get();
      Assert.assertTrue(error instanceof ClassCastException);
    } finally {
      System.setOut(origStream);
    }
  }

}
