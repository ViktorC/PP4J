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

import java.util.concurrent.atomic.AtomicReference;
import net.viktorc.pp4j.api.DisruptedExecutionException;
import net.viktorc.pp4j.api.FailedCommandException;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.impl.TestUtils.TestProcessManagerFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * A unit test class for {@link SimpleProcessExecutor}.
 *
 * @author Viktor Csomor
 */
public class SimpleProcessExecutorTest extends TestCase {

  /**
   * Creates and returns a new <code>SimpleProcessExecutor</code> instance using the default test process manager factory.
   *
   * @return A new simple process executor instance.
   */
  private static SimpleProcessExecutor newSimpleProcessExecutor() {
    return new SimpleProcessExecutor(new TestProcessManagerFactory().newProcessManager());
  }

  @Test
  public void testIsAliveAfterStart() throws Exception {
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      Assert.assertFalse(executor.isAlive());
      executor.start();
      Assert.assertTrue(executor.isAlive());
    }
  }

  @Test
  public void testIsNotAliveAfterTermination() throws Exception {
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      Assert.assertTrue(executor.isAlive());
      executor.terminate();
      executor.waitFor();
      Assert.assertFalse(executor.isAlive());
    }
  }

  @Test
  public void testIsNotAliveAfterClosure() throws Exception {
    SimpleProcessExecutor executorRef;
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executorRef = executor;
      executor.start();
    }
    Assert.assertFalse(executorRef.isAlive());
  }

  @Test
  public void testThrowsIllegalStateExceptionIfStartingAlreadyStartedExecutor() throws Exception {
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      exceptionRule.expect(IllegalStateException.class);
      executor.start();
    }
  }

  @Test
  public void testThrowsIllegalStateExceptionIfRunningAlreadyStartedExecutor() throws Exception {
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      exceptionRule.expect(IllegalStateException.class);
      executor.run();
    }
  }

  @Test
  public void testThrowsIllegalStateExceptionIfStartingAlreadyRunningExecutor() throws Exception {
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      Thread thread = new Thread(executor);
      thread.start();
      thread.join(WAIT_TIME_FOR_CONCURRENT_EVENTS);
      exceptionRule.expect(IllegalStateException.class);
      executor.start();
    }
  }

  @Test
  public void testThrowsIllegalStateExceptionIfRunningAlreadyRunningExecutor() throws Exception {
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      Thread thread = new Thread(executor);
      thread.start();
      thread.join(WAIT_TIME_FOR_CONCURRENT_EVENTS);
      exceptionRule.expect(IllegalStateException.class);
      executor.run();
    }
  }

  @Test
  public void testTryTerminateFailsIfNoTerminationSubmissionDefined() throws Exception {
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      Assert.assertFalse(executor.tryTerminate());
    }
  }

  @Test
  public void testProcessCanBeRestartedAfterTermination() throws Exception {
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      Assert.assertTrue(executor.isAlive());
      executor.terminate();
      executor.waitFor();
      Assert.assertFalse(executor.isAlive());
      executor.start();
      Assert.assertTrue(executor.isAlive());
    }
  }

  @Test
  public void testWaitForReturnsAfterExecutorTerminatesConcurrently() throws Exception {
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      Thread thread = new Thread(() -> {
        try {
          executor.waitFor();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      });
      thread.start();
      Assert.assertTrue(thread.isAlive());
      executor.terminate();
      executor.waitFor();
      Thread.sleep(WAIT_TIME_FOR_CONCURRENT_EVENTS);
      Assert.assertFalse(thread.isAlive());
    }
  }

  @Test
  public void testExecuteSubmission() throws Exception {
    AtomicReference<String> stringReference = new AtomicReference<>();
    SimpleCommand command = new SimpleCommand("process 3", (c, o) -> {
      if ("ready".equals(o)) {
        stringReference.set("ready");
        return true;
      }
      return false;
    });
    SimpleSubmission<AtomicReference<String>> submission = new SimpleSubmission<>(command, stringReference);
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      executor.execute(submission);
      Assert.assertTrue(submission.getResult().isPresent());
      Assert.assertEquals("ready", submission.getResult().get().get());
      Assert.assertTrue(command.getJointStandardOutLines().contains("in progress\nin progress\nready"));
    }
  }

  @Test
  public void testExecuteSubmissionWithoutOutputProcessing() throws Exception {
    SimpleSubmission<AtomicReference<String>> submission = new SimpleSubmission<>(new SimpleCommand("some invalid command"));
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      executor.execute(submission);
      Assert.assertTrue(true);
    }
  }

  @Test
  public void testExecuteSubmissionThrowsIllegalArgumentExceptionIfSubmissionNull() throws Exception {
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      exceptionRule.expect(IllegalArgumentException.class);
      executor.execute(null);
    }
  }

  @Test
  public void testExecuteSubmissionThrowsFailedCommandException() throws Exception {
    SimpleSubmission<AtomicReference<String>> submission = new SimpleSubmission<>(new SimpleCommand("process 3", (c, o) -> {
      throw new FailedCommandException(c, o);
    }));
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      exceptionRule.expect(FailedCommandException.class);
      executor.execute(submission);
    }
  }

  @Test
  public void testExecuteSubmissionThrowsDisruptedExecutionExceptionIfProcessTerminatedMidExecution() throws Exception {
    SimpleSubmission<AtomicReference<String>> submission = new SimpleSubmission<>(
        new SimpleCommand("process 3", (c, o) -> "ready".equals(o)));
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      Thread thread = new Thread(() -> {
        try {
          Thread.sleep(WAIT_TIME_FOR_CONCURRENT_EVENTS);
        } catch (InterruptedException e) {
          logger.error(e.getMessage(), e);
        }
        executor.terminate();
      });
      executor.start();
      exceptionRule.expect(DisruptedExecutionException.class);
      thread.start();
      executor.execute(submission);
    }
  }

  @Test
  public void testExecuteSubmissionThrowsDisruptedExecutionExceptionIfProcessAlreadyTerminated() throws Exception {
    SimpleSubmission<?> submission = new SimpleSubmission<>(new SimpleCommand("process 5", (c, o) -> "ready".equals(o)));
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      executor.terminate();
      exceptionRule.expect(DisruptedExecutionException.class);
      executor.execute(submission);
    }
  }

  @Test
  public void testTryExecuteSubmissionFalseIfAlreadyExecuting() throws Exception {
    SimpleSubmission<?> submission1 = new SimpleSubmission<>(new SimpleCommand("process 1", (c, o) -> "ready".equals(o)));
    SimpleSubmission<?> submission2 = new SimpleSubmission<>(new SimpleCommand("process 2", (c, o) -> "ready".equals(o)));
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      Thread thread = new Thread(() -> {
        try {
          executor.execute(submission1);
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
        }
      });
      thread.start();
      Thread.sleep(WAIT_TIME_FOR_CONCURRENT_EVENTS);
      Assert.assertFalse(executor.tryExecute(submission2, false));
      thread.join();
    }
  }

  @Test
  public void testTryExecuteSubmissionTerminatesProcessAfterwards() throws Exception {
    SimpleSubmission<?> submission = new SimpleSubmission<>(new SimpleCommand("process 1", (c, o) -> "ready".equals(o)));
    try (SimpleProcessExecutor executor = newSimpleProcessExecutor()) {
      executor.start();
      Assert.assertTrue(executor.isAlive());
      Thread.sleep(WAIT_TIME_FOR_CONCURRENT_EVENTS);
      Assert.assertTrue(executor.tryExecute(submission, true));
      executor.waitFor();
      Assert.assertFalse(executor.isAlive());
    }
  }

  @Test
  public void testProcessTerminatedAfterKeepAliveTimeExpires() throws Exception {
    long keepAliveTime = 500;
    ProcessManager processManager = new TestProcessManagerFactory(keepAliveTime, true, false, false, false).newProcessManager();
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(processManager)) {
      executor.start();
      Assert.assertTrue(executor.isAlive());
      Thread.sleep(keepAliveTime + WAIT_TIME_FOR_CONCURRENT_EVENTS);
      Assert.assertFalse(executor.isAlive());
    }
  }

  @Test
  public void testProcessTerminatedOnlyAfterKeepAliveTimeMillisecondsOfIdleness() throws Exception {
    long halfKeepAliveTime = 500;
    long keepAliveTime = 2 * halfKeepAliveTime;
    SimpleSubmission<?> submission = new SimpleSubmission<>(new SimpleCommand("process 1", (c, o) -> "ready".equals(o)));
    ProcessManager processManager = new TestProcessManagerFactory(keepAliveTime, true, false, false, false).newProcessManager();
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(processManager)) {
      executor.start();
      Assert.assertTrue(executor.isAlive());
      Thread.sleep(halfKeepAliveTime);
      Assert.assertTrue(executor.isAlive());
      executor.execute(submission);
      Assert.assertTrue(executor.isAlive());
      Thread.sleep(halfKeepAliveTime);
      Assert.assertTrue(executor.isAlive());
      Thread.sleep(halfKeepAliveTime + WAIT_TIME_FOR_CONCURRENT_EVENTS);
      Assert.assertFalse(executor.isAlive());
    }
  }

  @Test
  public void testProcessExecutorTerminatesIfStartupFails() throws Exception {
    ProcessManager processManager = new TestProcessManagerFactory(null, true, false, false, true).newProcessManager();
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(processManager)) {
      executor.start();
      executor.waitFor();
      Assert.assertFalse(executor.isAlive());
    }
  }

}
