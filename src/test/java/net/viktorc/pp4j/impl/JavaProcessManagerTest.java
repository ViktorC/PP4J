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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.viktorc.pp4j.api.FailedStartupException;
import net.viktorc.pp4j.api.Submission;
import net.viktorc.pp4j.impl.JavaProcess.Response;
import net.viktorc.pp4j.impl.JavaProcess.ResponseType;
import org.junit.Assert;
import org.junit.Test;

/**
 * A unit test class for {@link JavaProcessManager}.
 *
 * @author Viktor Csomor
 */
public class JavaProcessManagerTest extends TestCase {

  @Test
  public void testIsStartedUpReturnsFalseIfLineNotDecodeable() throws FailedStartupException {
    JavaProcessManager<?> javaProcessManager = new JavaProcessManager<>(new ProcessBuilder(""), null, null, null);
    Assert.assertFalse(javaProcessManager.isStartedUp("", false));
  }

  @Test
  public void testIsStartedUpReturnsFalseIfLineFromStdErr() throws IOException, FailedStartupException {
    JavaProcessManager<?> javaProcessManager = new JavaProcessManager<>(new ProcessBuilder(""), null, null, null);
    Response response = new Response(ResponseType.STARTUP_SUCCESS);
    String line = JavaObjectCodec.getInstance().encode(response);
    Assert.assertFalse(javaProcessManager.isStartedUp(line, true));
  }

  @Test
  public void testIsStartedUpOnlyReturnsTrueIfResponseStartupSuccess() throws IOException, FailedStartupException {
    JavaProcessManager<?> javaProcessManager = new JavaProcessManager<>(new ProcessBuilder(""), null, null, null);
    JavaObjectCodec codec = JavaObjectCodec.getInstance();
    Assert.assertFalse(javaProcessManager.isStartedUp(codec.encode(new Response(ResponseType.TASK_SUCCESS)), false));
    Assert.assertFalse(javaProcessManager.isStartedUp(codec.encode(new Response(ResponseType.TASK_FAILURE)), false));
    Assert.assertTrue(javaProcessManager.isStartedUp(codec.encode(new Response(ResponseType.STARTUP_SUCCESS)), false));
  }

  @Test
  public void testIsStartedUpThrowsFailedStartupExceptionIfResponseProcessFailure() throws FailedStartupException, IOException {
    JavaProcessManager<?> javaProcessManager = new JavaProcessManager<>(new ProcessBuilder(""), null, null, null);
    exceptionRule.expect(FailedStartupException.class);
    javaProcessManager.isStartedUp(JavaObjectCodec.getInstance().encode(new Response(ResponseType.PROCESS_FAILURE)), false);
  }

  @Test
  public void testIsStartedUpThrowsFailedStartupExceptionIfCalledWithJavaFailureMessage() throws FailedStartupException {
    JavaProcessManager<?> javaProcessManager = new JavaProcessManager<>(new ProcessBuilder(""), null, null, null);
    exceptionRule.expect(FailedStartupException.class);
    javaProcessManager.isStartedUp(JavaProcessManager.JAVA_FATAL_ERROR_MESSAGE, false);
  }

  @Test
  public void testTerminationSubmissionIncludesWrapUpTask() {
    AtomicInteger atomicInteger = new AtomicInteger(0);
    JavaProcessManager<?> javaProcessManager = new JavaProcessManager<>(new ProcessBuilder(""), null,
        (Runnable & Serializable) atomicInteger::incrementAndGet, null);
    Optional<Submission<?>> optionalSubmission = javaProcessManager.getTerminationSubmission();
    Assert.assertTrue(optionalSubmission.isPresent());
    Submission<?> submission = optionalSubmission.get();
    Assert.assertTrue(submission instanceof JavaSubmission);
    JavaSubmission<?> javaSubmission = (JavaSubmission<?>) submission;
    javaSubmission.getTask().run();
    Assert.assertEquals(1, atomicInteger.get());
  }

}
