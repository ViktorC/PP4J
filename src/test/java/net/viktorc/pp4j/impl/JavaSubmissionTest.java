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
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.FailedCommandException;
import net.viktorc.pp4j.impl.JavaProcess.Response;
import net.viktorc.pp4j.impl.JavaProcess.ResponseType;
import net.viktorc.pp4j.impl.JavaSubmission.SerializableTask;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

/**
 * A unit test class for {@link JavaSubmission}.
 *
 * @author Viktor Csomor
 */
public class JavaSubmissionTest extends TestCase {

  @Test
  public void testThrowsUncheckedIOExceptionIfNotSerializable() {
    Object nonSerializableObject = new Object();
    exceptionRule.expect(UncheckedIOException.class);
    new JavaSubmission<>((Runnable & Serializable) () -> System.out.println(nonSerializableObject));
  }

  @Test
  public void testTaskMakesRunnableCallable() throws Exception {
    JavaSubmission<?> javaSubmission = new JavaSubmission<>((Runnable & Serializable) () -> System.out.println("hi"));
    Object result = javaSubmission.getTask().call();
    Assert.assertSame(null, result);
  }

  @Test
  public void testTaskThrowsRuntimeExceptionAsRunnable() {
    JavaSubmission<?> javaSubmission = new JavaSubmission<>((Callable<Integer> & Serializable) () -> {
      throw new Exception();
    });
    exceptionRule.expect(RuntimeException.class);
    javaSubmission.getTask().run();
  }

  @Test
  public void testTaskReturnsCorrectValue() throws Exception {
    JavaSubmission<Integer> javaSubmission = new JavaSubmission<>((Callable<Integer> & Serializable) () -> 1);
    SerializableTask<Integer> task = javaSubmission.getTask();
    Assert.assertEquals(new Integer(1), task.call());
  }

  @Test
  public void testTaskIncrementsAtomicInteger() {
    AtomicInteger atomicInteger = new AtomicInteger(0);
    JavaSubmission<?> javaSubmission = new JavaSubmission<>((Runnable & Serializable) atomicInteger::incrementAndGet);
    javaSubmission.getTask().run();
    Assert.assertEquals(1, atomicInteger.get());
  }

  @Test
  public void testGetCommandsReturnsSingleCommand() {
    JavaSubmission<?> submission = new JavaSubmission<>((Runnable & Serializable) () -> {});
    Assert.assertEquals(1, submission.getCommands().size());
  }

  @Test
  public void testDecodedCommandInstruction() throws Exception {
    AtomicInteger atomicInteger = new AtomicInteger(5);
    JavaSubmission<?> javaSubmission = new JavaSubmission<>((Callable<Integer> & Serializable) atomicInteger::incrementAndGet);
    Command command = javaSubmission.getCommands().get(0);
    String instruction = command.getInstruction();
    Object decodedCommandInstruction = JavaObjectCodec.getInstance().decode(instruction);
    Assert.assertTrue(decodedCommandInstruction instanceof Callable);
    Callable<?> task = (Callable<?>) decodedCommandInstruction;
    Assert.assertEquals(6, task.call());
    Assert.assertEquals(5, atomicInteger.get());
  }

  @Test
  public void testCommandIsCompletedReturnsFalseIfStringNotDecodable() throws FailedCommandException {
    JavaSubmission<?> javaSubmission = new JavaSubmission<>((Runnable & Serializable) () -> {});
    Command command = javaSubmission.getCommands().get(0);
    Assert.assertFalse(command.isCompleted("", false));
  }

  @Test
  public void testCommandIsCompletedReturnsFalseIfLineFromStdErr() throws IOException, FailedCommandException {
    JavaSubmission<?> javaSubmission = new JavaSubmission<>((Runnable & Serializable) () -> {});
    Command command = javaSubmission.getCommands().get(0);
    Response response = new Response(ResponseType.TASK_SUCCESS);
    String outputLine = JavaObjectCodec.getInstance().encode(response);
    Assert.assertFalse(command.isCompleted(outputLine, true));
  }

  @Test
  public void testCommandIsCompletedReturnsFalseIfResponseProcessFailure() throws IOException, FailedCommandException {
    JavaSubmission<?> javaSubmission = new JavaSubmission<>((Runnable & Serializable) () -> {});
    Command command = javaSubmission.getCommands().get(0);
    Response response = new Response(ResponseType.PROCESS_FAILURE);
    String outputLine = JavaObjectCodec.getInstance().encode(response);
    Assert.assertFalse(command.isCompleted(outputLine, true));
  }

  @Test
  public void testCommandIsCompletedThrowsFailedCommandExceptionIfResponseTaskFailure() throws IOException, FailedCommandException {
    JavaSubmission<?> javaSubmission = new JavaSubmission<>((Runnable & Serializable) () -> {});
    Command command = javaSubmission.getCommands().get(0);
    Response response = new Response(ResponseType.TASK_FAILURE);
    String outputLine = JavaObjectCodec.getInstance().encode(response);
    exceptionRule.expect(FailedCommandException.class);
    command.isCompleted(outputLine, false);
  }

  @Test
  public void testCommandIsCompletedThrowsFailedCommandExceptionWithCorrectCauseIfResponseTaskFailure() throws IOException,
      FailedCommandException {
    JavaSubmission<?> javaSubmission = new JavaSubmission<>((Runnable & Serializable) () -> {});
    Command command = javaSubmission.getCommands().get(0);
    Response response = new Response(ResponseType.TASK_FAILURE, new IllegalArgumentException());
    String outputLine = JavaObjectCodec.getInstance().encode(response);
    exceptionRule.expect(FailedCommandException.class);
    exceptionRule.expectCause(CoreMatchers.isA(IllegalArgumentException.class));
    command.isCompleted(outputLine, false);
  }

  @Test
  public void testCommandIsCompletedReturnsTrueAndSetsResultIfTaskSuccess() throws IOException, FailedCommandException {
    JavaSubmission<?> javaSubmission = new JavaSubmission<>((Runnable & Serializable) () -> {});
    Command command = javaSubmission.getCommands().get(0);
    Response response = new Response(ResponseType.TASK_SUCCESS, 5);
    String outputLine = JavaObjectCodec.getInstance().encode(response);
    Assert.assertEquals(Optional.empty(), javaSubmission.getResult());
    Assert.assertTrue(command.isCompleted(outputLine, false));
    Assert.assertEquals(5, javaSubmission.getResult().orElse(null));
  }

}
