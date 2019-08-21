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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.FailedCommandException;
import net.viktorc.pp4j.api.Submission;
import net.viktorc.pp4j.impl.TestUtils.TestProcessManagerFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * An integration test class for {@link ProcessPoolExecutor}.
 *
 * @author Viktor Csomor
 */
public class PPEIntegrationTest extends TestCase {

  /**
   * Makes assertions about the properties and the state of the process pool after startup.
   *
   * @param pool The process pool to test.
   * @param minPoolSize The expected minimum pool size.
   * @param maxPoolSize The expected maximum pool size.
   * @param reserveSize The expected pool reserve size.
   */
  private static void checkPoolAfterStartup(ProcessPoolExecutor pool, int minPoolSize, int maxPoolSize, int reserveSize) {
    assert minPoolSize == pool.getMinSize() : String.format("Different min pool sizes: %d instead of %s", minPoolSize, pool.getMinSize());
    assert maxPoolSize == pool.getMaxSize() : String.format("Different max pool sizes: %d instead of %s", maxPoolSize, pool.getMaxSize());
    assert reserveSize == pool.getReserveSize() :
        String.format("Different pool reserve sizes: %d instead of %s", reserveSize, pool.getReserveSize());
    assert pool.getNumOfSubmissions() == 0 :
        String.format("Non-zero number of active submissions on startup: %d", pool.getNumOfSubmissions());
    assert pool.getNumOfProcesses() == Math.max(minPoolSize, reserveSize) :
        String.format("Unexpected number of total processes: %d instead of %d", pool.getNumOfProcesses(),
            Math.max(minPoolSize, reserveSize));
    assert !pool.isTerminated() : "Process pool terminated after startup";
    assert !pool.isShutdown() : "Process pool shut down after startup";
  }

  /**
   * Makes assertions about the state of the process pool before shutdown.
   *
   * @param pool The process pool to test.
   */
  private static void checkPoolBeforeShutdown(ProcessPoolExecutor pool) {
    assert !pool.isTerminated() : "Process pool terminated early";
    assert !pool.isShutdown() : "Process pool shut down early";
  }

  /**
   * Makes assertions about the state of the process pool after shutdown.
   *
   * @param pool The process pool to test.
   */
  private static void checkPoolAfterShutdown(ProcessPoolExecutor pool) {
    assert pool.isShutdown() : "Process pool is not deemed shut down";
  }

  /**
   * Makes assertions about the state of the process pool after termination.
   *
   * @param pool The process pool to test.
   */
  private static void checkPoolAfterTermination(ProcessPoolExecutor pool) {
    assert pool.isTerminated() : "Process pool is not deemed terminated";
  }

  /**
   * Makes assertions about the command output saved by the command implementation.
   *
   * @param command The command whose stored output is to be tested.
   * @param procTime The number of processing cycles the command's instruction prompts.
   */
  private static void checkSavedCommandOutputBeforeReset(AbstractCommand command, int procTime) {
    assert command.getStandardOutLines().size() == procTime - 1 && command.getStandardErrLines().size() == 0 :
        String.format("Unexpected numbers of output lines - standard out: %d instead of %d; standard error: %d instead of %d",
            command.getStandardOutLines().size(), procTime - 1, command.getStandardErrLines().size(), 0);
    String expectedStdOutput = Arrays.stream(new String[procTime - 1])
        .map(s -> "in progress")
        .reduce("", (s1, s2) -> (s1 + "\n" + s2).trim());
    assert expectedStdOutput.equals(command.getJointStandardOutLines()) :
        String.format("Wrongly captured standard output - expected: \"%s\"; actual: \"%s\"", expectedStdOutput,
            command.getJointStandardOutLines());
    assert command.getJointStandardErrLines().isEmpty() :
        String.format("Wrongly captured standard error - expected: \"\"; actual: \"%s\"", command.getJointStandardErrLines());
  }

  /**
   * Makes assertions about the command output saved by the command implementation after the command is reset.
   *
   * @param command The command whose stored output is to be tested.
   */
  private static void checkSavedCommandOutputAfterReset(AbstractCommand command) {
    assert command.getStandardOutLines().isEmpty() :
        String.format("Non-zero lines of standard output after reset: %d", command.getStandardOutLines().size());
    assert command.getStandardErrLines().isEmpty() :
        String.format("Non-zero lines of error output after reset: %d", command.getStandardErrLines().size());
    assert command.getJointStandardOutLines().isEmpty() :
        String.format("Non-empty joint standard output after reset: \"%s\"", command.getJointStandardOutLines());
    assert command.getJointStandardErrLines().isEmpty() :
        String.format("Non-empty joint standard error after reset: \"%s\"", command.getJointStandardErrLines());
  }

  /**
   * Creates and, if possible, checks a process pool of test processes.
   *
   * @param minPoolSize The minimum pool size.
   * @param maxPoolSize The maximum pool size.
   * @param reserveSize The reserve size.
   * @param keepAliveTime The number of milliseconds of idleness after which the process are to be terminated.
   * @param verifyStartup Whether the processes should only be considered started up after printing a certain output to their standard outs.
   * @param manuallyTerminate Whether the processes should be terminated using the termination command or an OS level kill signal.
   * @param throwStartupException Whether the process manager should throw a runtime exception when its
   * {@link net.viktorc.pp4j.api.ProcessManager#isStartedUp(String, boolean)} is invoked.
   * @return The process pool.
   * @throws InterruptedException If the thread is interrupted while waiting for the pool to be initialized.
   */
  private static ProcessPoolExecutor createPool(int minPoolSize, int maxPoolSize, int reserveSize, Long keepAliveTime,
      boolean verifyStartup, boolean manuallyTerminate, boolean throwStartupException) throws InterruptedException {
    TestProcessManagerFactory managerFactory = new TestProcessManagerFactory(keepAliveTime, verifyStartup, true, manuallyTerminate,
        throwStartupException);
    ProcessPoolExecutor processPool = new ProcessPoolExecutor(managerFactory, minPoolSize, maxPoolSize, reserveSize);
    if (!throwStartupException) {
      checkPoolAfterStartup(processPool, minPoolSize, maxPoolSize, reserveSize);
    }
    return processPool;
  }

  /**
   * Creates a command to use in a submission to submit to a test process.
   *
   * @param procTime The number of processing cycles the command is to entail.
   * @param throwExecutionException Whether the command should throw a {@link FailedCommandException} when its
   * {@link Command#isCompleted(String, boolean)} method is invoked.
   * @return The command.
   */
  private static SimpleCommand createCommand(int procTime, boolean throwExecutionException) {
    return new SimpleCommand("process " + procTime,
        (command, outputLine) -> {
          if (throwExecutionException) {
            throw new FailedCommandException(command, "test");
          }
          if ("ready".equals(outputLine)) {
            checkSavedCommandOutputBeforeReset(command, procTime);
            command.reset();
            checkSavedCommandOutputAfterReset(command);
            return true;
          }
          return false;
        });
  }

  /**
   * Creates a submission to submit to a test process.
   *
   * @param procTimes An array of the number of processing cycles each command of the submission is to entail.
   * @param throwExecutionException Whether the command should throw a {@link FailedCommandException} when its
   * {@link Command#isCompleted(String, boolean)} method is invoked.
   * @param times A list of times that the submission is to update upon its completion to calculate the execution duration.
   * @param index The index of the submission. Only the element at this index is to be updated in <code>times</code>.
   * @return The submission.
   */
  private static SimpleSubmission createSubmission(int[] procTimes, boolean throwExecutionException, List<Long> times, int index) {
    List<Command> commands = null;
    if (procTimes != null) {
      commands = new ArrayList<>();
      for (int procTime : procTimes) {
        commands.add(createCommand(procTime, throwExecutionException));
      }
    }
    return new SimpleSubmission(commands) {

      @Override
      public void onFinishedExecution() {
        times.set(index, System.nanoTime() - times.get(index));
      }
    };
  }

  /**
   * Submits the specified number of submissions to the provided process pool.
   *
   * @param processPool The process pool to submit the submissions to.
   * @param procTimes An array of the number of processing cycles each command of each submission is to entail.
   * @param submissions The total number of submissions to submit.
   * @param timeSpan The number of milliseconds within which the uniformly distributed requests should be submitted.
   * @param reuse Whether a process can execute multiple commands or should be terminated after execution.
   * @param throwExecutionException Whether an exception should be thrown by the submitted command.
   * @param futures An empty list to add the futures returned by the process pool when submitting the submissions to.
   * @param times An empty list to which an entry is added for each submission then each submission updates it upon completion to record
   * their individual execution times.
   * @throws InterruptedException If the thread is interrupted while waiting between submissions.
   */
  private static void submitSubmissions(ProcessPoolExecutor processPool, int[] procTimes, int submissions, long timeSpan, boolean reuse,
      boolean throwExecutionException, List<Future<?>> futures, List<Long> times) throws InterruptedException {
    long frequency = submissions > 0 ? timeSpan / submissions : 0;
    for (int i = 0; i < submissions; i++) {
      if (i != 0 && frequency > 0) {
        Thread.sleep(frequency);
      }
      Submission<?> submission = createSubmission(procTimes, throwExecutionException, times, i);
      times.add(System.nanoTime());
      futures.add(processPool.submit(submission, !reuse));
    }
  }

  /**
   * Waits for the the submissions to complete.
   *
   * @param futures The list of futures representing the results.
   * @param waitTimeout The maximum number of milliseconds to wait for a submission.
   * @throws InterruptedException If the thread is interrupted while waiting.
   * @throws ExecutionException If a submission failed.
   * @throws TimeoutException If the submission does not complete within <code>waitTimeout</code>.
   */
  private static void waitForFutures(List<Future<?>> futures, long waitTimeout)
      throws InterruptedException, ExecutionException, TimeoutException {
    for (Future<?> future : futures) {
      if (waitTimeout > 0) {
        future.get(waitTimeout, TimeUnit.MILLISECONDS);
      } else {
        future.get();
      }
    }
  }

  /**
   * Evaluates the success of the performance test.
   *
   * @param times A list of the execution times of each submission or an empty list if an error occurred during submission.
   * @param submissions The total number of submissions.
   * @param upperBound The maximum accepted execution time in milliseconds.
   * @param lowerBound The minimum accepted execution time in milliseconds.
   * @return Whether the performance test can be considered successful.
   */
  private boolean evaluatePerfTestResults(List<Long> times, int submissions, long upperBound, long lowerBound) {
    if (times.size() == submissions) {
      boolean pass = true;
      for (Long time : times) {
        time = Math.round(((double) time / 1000000));
        boolean fail = time > upperBound || time < lowerBound;
        if (fail) {
          pass = false;
        }
        logTime(!fail, time);
      }
      return pass;
    } else {
      logger.info(String.format("Some requests were not processed: %d/%d", times.size(), submissions));
      return false;
    }
  }

  /**
   * Submits the specified number of commands with the specified frequency to a the test process pool corresponding to the specified
   * parameters and determines whether it performs well enough based on the number of processed requests and the times it took to process
   * them.
   *
   * @param processPool The process pool executor to test.
   * @param reuse Whether a process can execute multiple commands.
   * @param procTimes The times for which the test processes should "execute" commands. Each element stands for a command. If there are
   * multiple elements, the commands will be chained.
   * @param submissions The number of commands to submit.
   * @param timeSpan The number of milliseconds within which the uniformly distributed requests should be submitted.
   * @param throwExecutionException Whether an exception should be thrown by the submitted command.
   * @param cancelTime The number of milliseconds after which the futures should be cancelled. If it is 0 or less, the futures are not
   * cancelled.
   * @param forcedCancel If the command should be interrupted if it is already being processed. If
   * <code>cancelTime</code> is not greater than 0, it has no effect.
   * @param earlyClose Whether the pool should be closed right after the submission of the commands.
   * @param forcedEarlyClose Whether the early shutdown of the pool should be orderly or forced.
   * @param waitTimeout The number of milliseconds for which the submissions are waited on.
   * @param lowerBound The minimum acceptable submission execution time.
   * @param upperBound The maximum acceptable submission execution time.
   * @return Whether the test passes.
   * @throws Exception If the process pool cannot be created.
   */
  private boolean perfTest(ProcessPoolExecutor processPool, boolean reuse, int[] procTimes, int submissions, long timeSpan,
      boolean throwExecutionException, long cancelTime, boolean forcedCancel, boolean earlyClose, boolean forcedEarlyClose,
      long waitTimeout, long lowerBound, long upperBound) throws Exception {
    try {
      List<Long> times = new ArrayList<>(submissions);
      List<Future<?>> futures = new ArrayList<>(submissions);
      submitSubmissions(processPool, procTimes, submissions, timeSpan, reuse, throwExecutionException, futures, times);
      if (cancelTime > 0) {
        Thread.sleep(cancelTime);
        for (Future<?> future : futures) {
          future.cancel(forcedCancel);
        }
      } else if (earlyClose) {
        checkPoolBeforeShutdown(processPool);
        if (forcedEarlyClose) {
          processPool.forceShutdown();
        } else {
          processPool.shutdown();
        }
        checkPoolAfterShutdown(processPool);
      }
      waitForFutures(futures, waitTimeout);
      if (!earlyClose) {
        checkPoolBeforeShutdown(processPool);
        processPool.shutdown();
        checkPoolAfterShutdown(processPool);
        processPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        checkPoolAfterTermination(processPool);
      }
      return evaluatePerfTestResults(times, submissions, upperBound, lowerBound);
    } catch (Exception e) {
      if (!processPool.isTerminated()) {
        processPool.forceShutdown();
        processPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
      }
      throw e;
    }
  }

  // Exception testing.
  @Test
  public void test01() throws Exception {
    exceptionRule.expect(IllegalArgumentException.class);
    createPool(-1, 5, 0, null, false, false, false);
  }

  @Test
  public void test02() throws Exception {
    exceptionRule.expect(IllegalArgumentException.class);
    createPool(0, 0, 0, null, false, false, false);
  }

  @Test
  public void test03() throws Exception {
    exceptionRule.expect(IllegalArgumentException.class);
    createPool(10, 5, 0, null, false, false, false);
  }

  @Test
  public void test04() throws Exception {
    exceptionRule.expect(IllegalArgumentException.class);
    createPool(10, 12, -1, null, false, false, false);
  }

  @Test
  public void test05() throws Exception {
    exceptionRule.expect(IllegalArgumentException.class);
    createPool(10, 12, 15, null, false, false, false);
  }

  @Test
  public void test06() throws Exception {
    ProcessPoolExecutor pool = createPool(0, Integer.MAX_VALUE, 0, null, false, false, false);
    exceptionRule.expect(IllegalArgumentException.class);
    perfTest(pool, false, null, 100, 10000, false, 0, false, false, false, 0, 4995, 6200);
  }

  @Test
  public void test07() throws Exception {
    ProcessPoolExecutor pool = createPool(0, Integer.MAX_VALUE, 0, null, false, false, false);
    exceptionRule.expect(IllegalArgumentException.class);
    perfTest(pool, false, new int[0], 100, 10000, false, 0, false, false, false, 0, 4995, 6200);
  }

  // Performance testing.
  @Test
  public void test08() throws Exception {
    ProcessPoolExecutor pool = createPool(0, 100, 0, null, true, false, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5}, 100, 10000, false, 0, false, false, false,
        0, 4995, 6250));
  }

  @Test
  public void test09() throws Exception {
    ProcessPoolExecutor pool = createPool(50, 150, 20, null, false, false, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5}, 100, 5000, false, 0, false, false, false,
        0, 4995, 5100));
  }

  @Test
  public void test10() throws Exception {
    ProcessPoolExecutor pool = createPool(10, 25, 5, 15000L, true, false, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5}, 20, 10000, false, 0, false, false, false,
        0, 4995, 5100));
  }

  @Test
  public void test11() throws Exception {
    ProcessPoolExecutor pool = createPool(50, 150, 20, null, false, true, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5}, 100, 5000, false, 0, false, false, false,
        0, 4995, 5100));
  }

  @Test
  public void test12() throws Exception {
    ProcessPoolExecutor pool = createPool(10, 50, 5, 15000L, true, false, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5, 3, 2}, 50, 10000, false, 0, false, false,
        false, 0, 9995, 10340));
  }

  @Test
  public void test13() throws Exception {
    ProcessPoolExecutor pool = createPool(100, 250, 20, null, true, true, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5}, 800, 20000, false, 0, false, false, false,
        0, 4995, 6000));
  }

  @Test
  public void test14() throws Exception {
    ProcessPoolExecutor pool = createPool(0, 100, 0, null, false, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 100, 10000, false, 0, false, false, false,
        0, 4995, 6850));
  }

  @Test
  public void test15() throws Exception {
    ProcessPoolExecutor pool = createPool(50, 150, 10, null, true, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 100, 5000, false, 0, false, false, false,
        0, 4995, 5620));
  }

  @Test
  public void test16() throws Exception {
    ProcessPoolExecutor pool = createPool(10, 25, 5, 15000L, false, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 10000, false, 0, false, false, false,
        0, 4995, 5100));
  }

  @Test
  public void test17() throws Exception {
    ProcessPoolExecutor pool = createPool(50, 150, 10, null, true, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 100, 5000, false, 0, false, false, false,
        0, 4995, 5600));
  }

  @Test
  public void test18() throws Exception {
    ProcessPoolExecutor pool = createPool(10, 50, 5, 15000L, false, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5, 3, 2}, 50, 10000, false, 0, false, false,
        false, 0, 9995, 10350));
  }

  @Test
  public void test19() throws Exception {
    ProcessPoolExecutor pool = createPool(50, 250, 20, null, true, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 800, 20000, false, 0, false, false, false,
        0, 4995, 6000));
  }

  // Keep alive timer test.
  @Test
  public void test20() throws Exception {
    ProcessPoolExecutor pool = createPool(20, 40, 4, 250L, true, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 50, 5000, false, 0, false, false, false,
        0, 4995, 8200));
  }

  // Cancellation testing.
  @Test
  public void test21() throws Exception {
    ProcessPoolExecutor pool = createPool(10, 30, 5, null, true, true, false);
    exceptionRule.expect(CancellationException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 0, false, 2500, true, false, false,
        0, 2495, 2520));
  }

  @Test
  public void test22() throws Exception {
    ProcessPoolExecutor pool = createPool(20, 20, 0, null, false, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 0, false, 2500, false, false, false,
        0, 4995, 5120));
  }

  @Test
  public void test23() throws Exception {
    ProcessPoolExecutor pool = createPool(10, 30, 5, null, true, true, false);
    exceptionRule.expect(CancellationException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5, 5, 3}, 20, 0, false, 2500, true, false,
        false, 0, 2495, 2520));
  }

  @Test
  public void test24() throws Exception {
    ProcessPoolExecutor pool = createPool(20, 20, 0, null, true, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5, 5, 3}, 20, 0, false, 3000, false, false,
        false, 0, 12995, 13120));
  }

  // Early shutdown testing.
  @Test
  public void test25() throws Exception {
    ProcessPoolExecutor pool = createPool(100, 100, 0, 5000L, true, false, false);
    exceptionRule.expect(ExecutionException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 100, 0, false, 0, false, true, true,
        0, 0, 0));
  }

  @Test
  public void test26() throws Exception {
    ProcessPoolExecutor pool = createPool(100, 100, 0, 5000L, true, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 100, 0, false, 0, false, true, false,
        0, 4995, 5100));
    exceptionRule.expect(RejectedExecutionException.class);
    pool.submit(new SimpleSubmission(new SimpleCommand("")), false);
    pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
  }

  // Interrupted construction testing.
  @Test
  public void test27() throws Exception {
    ProcessPoolExecutor pool = null;
    Thread thread = Thread.currentThread();
    Timer timer = new Timer();
    exceptionRule.expect(InterruptedException.class);
    try {
      timer.schedule(new TimerTask() {

        @Override
        public void run() {
          thread.interrupt();
        }
      }, 500);
      pool = createPool(20, 30, 0, null, false, false, false);
    } finally {
      if (pool != null) {
        pool.forceShutdown();
      }
    }
  }

  // Single process pool performance testing.
  @Test
  public void test28() throws Exception {
    ProcessPoolExecutor pool = createPool(1, 1, 0, 20000L, true, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 5, 30000, false, 0, false, false, false,
        0, 4995, 5250));
  }

  @Test
  public void test29() throws Exception {
    ProcessPoolExecutor pool = createPool(1, 1, 0, null, true, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 5, 20000, false, 0, false, false, false,
        0, 4995, 13250));
  }

  // Fixed size process pool performance testing.
  @Test
  public void test30() throws Exception {
    ProcessPoolExecutor pool = createPool(20, 20, 0, null, true, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 5000, false, 0, false, false, false,
        0, 4995, 5200));
  }

  @Test
  public void test31() throws Exception {
    ProcessPoolExecutor pool = createPool(20, 20, 0, null, true, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 40, 10000, false, 0, false, false, false,
        0, 4995, 6200));
  }

  // Wait with timeout testing.
  @Test
  public void test32() throws Exception {
    ProcessPoolExecutor pool = createPool(20, 50, 10, null, true, true, false);
    exceptionRule.expect(TimeoutException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 40, 0, false, 0, false, false, false,
        3000, 3000, 3000));
  }

  @Test
  public void test33() throws Exception {
    ProcessPoolExecutor pool = createPool(20, 50, 0, null, true, true, false);
    exceptionRule.expect(TimeoutException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5, 5}, 40, 0, false, 0, false, false,
        false, 5000, 5000, 5000));
  }

  // Wait with timeout plus cancellation testing.
  @Test
  public void test34() throws Exception {
    ProcessPoolExecutor pool = createPool(10, 30, 5, null, true, true, false);
    exceptionRule.expect(CancellationException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 0, false, 2500, true, false,
        false, 5000, 2495, 2520));
  }

  @Test
  public void test35() throws Exception {
    ProcessPoolExecutor pool = createPool(20, 20, 0, null, false, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 0, false, 2500, false, false,
        false, 3000, 4995, 5120));
  }

  // Execution exception testing.
  @Test
  public void test36() throws Exception {
    ProcessPoolExecutor pool = createPool(0, Integer.MAX_VALUE, 0, null, false, false, false);
    exceptionRule.expect(ExecutionException.class);
    exceptionRule.expectMessage("test");
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 4000, true, 0, false, false,
        false, 0, 4995, 6200));
  }

  @Test
  public void test37() throws Exception {
    ProcessPoolExecutor pool = createPool(0, Integer.MAX_VALUE, 0, null, false, false, false);
    exceptionRule.expect(ExecutionException.class);
    exceptionRule.expectMessage("test");
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 4000, true, 0, false, false,
        false, 1000, 4995, 6200));
  }

  // Startup exception testing.
  @Test
  public void test38() throws Exception {
    createPool(20, 20, 0, null, false, false, true);
    Assert.assertTrue(true);
  }

  // Execute method testing.
  @Test
  public void test39() throws Exception {
    ProcessPoolExecutor pool = createPool(1, 1, 0, null, false, false, false);
    try {
      SimpleCommand command = new SimpleCommand("process 3", (c, o) -> "ready".equals(o));
      long start = System.currentTimeMillis();
      pool.execute(new SimpleSubmission(command));
      long time = System.currentTimeMillis() - start;
      boolean success = time > 2995 && time < 3050;
      logTime(success, time);
      Assert.assertTrue(success);
    } finally {
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

}