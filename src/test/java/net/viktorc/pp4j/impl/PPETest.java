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
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.Command.Status;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.Submission;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * A test class for the standard process pool implementation.
 *
 * @author Viktor Csomor
 */
public class PPETest {

  @Rule
  public final ExpectedException exceptionRule = ExpectedException.none();

  private final String programLocation;

  /**
   * Resolves the path to the test program and ensures that it is executable.
   */
  public PPETest() {
    programLocation = TestUtils.getExecutable().getAbsolutePath();
  }

  /**
   * Performs some basic checks on the pool concerning its size and other parameters.
   *
   * @param pool The pool to check.
   * @param minPoolSize The minimum pool size.
   * @param maxPoolSize The maximum pool size.
   * @param reserveSize The process reserve size.
   */
  private void checkPool(ProcessPoolExecutor pool, int minPoolSize, int maxPoolSize, int reserveSize) {
    // Basic, implementation-specific pool state checks.
    assert minPoolSize == pool.getMinSize() : "Different min pool sizes: " + minPoolSize + " and " + pool.getMinSize() + ".";
    assert maxPoolSize == pool.getMaxSize() : "Different max pool sizes: " + maxPoolSize + " and " + pool.getMaxSize() + ".";
    assert reserveSize == pool.getReserveSize() : "Different reserve sizes: " + reserveSize + " and " + pool.getReserveSize() + ".";
    assert pool.getNumOfSubmissions() == 0 : "Non-zero number of active submissions on " + "startup: " + pool.getNumOfSubmissions() + ".";
    assert pool.getNumOfProcesses() == Math.max(minPoolSize, reserveSize) : "Unexpected number of " + "total processes: " +
        pool.getNumOfProcesses() + " instead of " + Math.max(minPoolSize, reserveSize) + ".";
  }

  /**
   * Creates a custom test process pool according to the specified parameters.
   *
   * @param minPoolSize The minimum pool size.
   * @param maxPoolSize The maximum pool size.
   * @param reserveSize The process reserve size.
   * @param keepAliveTime The time after which idled processes are killed.
   * @param verifyStartup Whether the startup should be verified.
   * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
   * @param throwStartupException Whether a process exception should be thrown on startup.
   * @return The process pool created according to the specified parameters.
   * @throws InterruptedException If the thread is interrupted while it is waiting for the core threads to start up.
   */
  private ProcessPoolExecutor getPool(int minPoolSize, int maxPoolSize, int reserveSize, Long keepAliveTime, boolean verifyStartup,
      boolean manuallyTerminate, boolean throwStartupException) throws InterruptedException {
    TestProcessManagerFactory managerFactory = new TestProcessManagerFactory(keepAliveTime, verifyStartup, manuallyTerminate,
        throwStartupException);
    ProcessPoolExecutor pool = new ProcessPoolExecutor(managerFactory, minPoolSize, maxPoolSize, reserveSize);
    checkPool(pool, minPoolSize, maxPoolSize, reserveSize);
    return pool;
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
   * @param requests The number of commands to submit.
   * @param timeSpan The number of milliseconds in which the uniformly distributed requests should be submitted.
   * @param throwExecutionException Whether a process exception should be thrown by the submitted command.
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
  private boolean perfTest(ProcessPoolExecutor processPool, boolean reuse, int[] procTimes, int requests, long timeSpan,
      boolean throwExecutionException, long cancelTime, boolean forcedCancel, boolean earlyClose, boolean forcedEarlyClose,
      long waitTimeout, long lowerBound, long upperBound) throws Exception {
    try {
      List<Long> times = new ArrayList<>();
      List<Future<?>> futures = new ArrayList<>(requests);
      long frequency = requests > 0 ? timeSpan / requests : 0;
      for (int i = 0; i < requests; i++) {
        if (i != 0 && frequency > 0) {
          try {
            Thread.sleep(frequency);
          } catch (InterruptedException e) {
            return false;
          }
        }
        List<Command> commands;
        if (procTimes == null) {
          commands = null;
        } else {
          commands = new ArrayList<>();
          for (int procTime : procTimes) {
            commands.add(new SimpleCommand("process " + procTime, (c, o) -> {
              if ("ready".equals(o)) {
                // Output line caching check.
                assert c.getStandardOutLines().size() == procTime - 1 && c.getStandardErrLines().size() == 0 :
                    String.format("Unexpected numbers of output lines: %d instead of %d and %d instead of %d.",
                        c.getStandardOutLines().size(), procTime - 1, c.getStandardErrLines().size(), 0);
                String expectedStdOutput = Arrays.stream(new String[procTime - 1])
                    .map(s -> "in progress")
                    .reduce("", (s1, s2) -> (s1 + "\n" + s2).trim());
                assert expectedStdOutput.equals(c.getJointStandardOutLines()) :
                    String.format("Wrongly captured standard output. Expected: \"%s\"%nActual: \"%s\"", expectedStdOutput,
                        c.getJointStandardOutLines());
                assert "".equals(c.getJointStandardErrLines()) :
                    String.format("Wrongly captured standard output. Expected: \"\"%nActual: \"%s\"", c.getJointStandardOutLines());
                c.reset();
                return Status.SUCCESSFUL;
              }
              return Status.IN_PROGRESS;
            }, (c, o) -> Status.FAILED) {

              @Override
              public boolean generatesOutput() {
                if (throwExecutionException) {
                  throw new RuntimeException("Test execution exception.");
                }
                return super.generatesOutput();
              }
            });
          }
        }
        int index = i;
        Submission<?> submission;
        if (commands != null && commands.size() == 1) {
          submission = new SimpleSubmission(commands.get(0)) {

            @Override
            public void onFinishedExecution() {
              times.set(index, System.nanoTime() - times.get(index));
            }
          };
        } else {
          submission = new SimpleSubmission(commands) {

            @Override
            public void onFinishedExecution() {
              times.set(index, System.nanoTime() - times.get(index));
            }
          };
        }
        times.add(System.nanoTime());
        futures.add(reuse ? processPool.submit(submission) : processPool.submit(submission, true));
      }
      if (cancelTime > 0) {
        Thread.sleep(cancelTime);
        for (Future<?> future : futures) {
          future.cancel(forcedCancel);
        }
      } else if (earlyClose) {
        assert !processPool.isTerminated() : "Process pool terminated early.";
        assert !processPool.isShutdown() : "Process pool shut down early.";
        if (forcedEarlyClose) {
          processPool.forceShutdown();
        } else {
          processPool.shutdown();
        }
        assert processPool.isShutdown() : "Process pool considered alive falsely.";
      }
      for (Future<?> future : futures) {
        if (waitTimeout > 0) {
          future.get(waitTimeout, TimeUnit.MILLISECONDS);
        } else {
          future.get();
        }
      }
      if (!earlyClose) {
        assert !processPool.isTerminated() : "Process pool terminated early.";
        assert !processPool.isShutdown() : "Process pool shut down early.";
        processPool.shutdown();
        assert processPool.isShutdown() : "Process pool considered alive falsely.";
        processPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        assert processPool.isTerminated() : "Process pool has not terminated.";
      }
      String testArgMessage = "keepAliveTime: %d; throwStartupError: %s; verifyStartup: %s;%n" +
          "manuallyTerminate: %s; reuse: %s; procTimes: %s; requests: %d; timeSpan: %d;%n" +
          "throwExecutionError: %s; cancelTime: %d; forcedCancel: %s; earlyClose: %s;%n" +
          "forcedEarlyClose: %s; waitTimeout: %.3f; lowerBound: %.3f; upperBound: %.3f;%n";
      TestProcessManagerFactory procManagerFactory = (TestProcessManagerFactory) processPool.getProcessManagerFactory();
      Object[] args = new Object[]{procManagerFactory.keepAliveTime, Boolean.toString(procManagerFactory.throwStartupException),
          Boolean.toString(procManagerFactory.verifyStartup), Boolean.toString(procManagerFactory.manuallyTerminate),
          Boolean.toString(reuse), Arrays.toString(procTimes), requests, timeSpan, Boolean.toString(throwExecutionException),
          cancelTime, Boolean.toString(forcedCancel), Boolean.toString(earlyClose), Boolean.toString(forcedEarlyClose),
          (float) (((double) waitTimeout) / 1000), (float) (((double) lowerBound) / 1000), (float) (((double) upperBound) / 1000)};
      testArgMessage = "minPoolSize: %d; maxPoolSize: %d; reserveSize: %d;%n" + testArgMessage;
      Object[] additionalArgs = new Object[]{processPool.getMinSize(), processPool.getMaxSize(), processPool.getReserveSize()};
      Object[] extendedArgs = new Object[args.length + additionalArgs.length];
      System.arraycopy(additionalArgs, 0, extendedArgs, 0, additionalArgs.length);
      System.arraycopy(args, 0, extendedArgs, additionalArgs.length, args.length);
      args = extendedArgs;
      String separator = "------------------------------------------------------------------------------------------";
      System.out.println(separator);
      System.out.printf(testArgMessage, args);
      System.out.println(separator);
      if (times.size() == requests) {
        boolean pass = true;
        for (Long time : times) {
          // Convert nanoseconds to milliseconds.
          time = Math.round(((double) time / 1000000));
          boolean fail = time > upperBound || time < lowerBound;
          if (fail) {
            pass = false;
          }
          System.out.printf("Time: %.3f %s%n", (float) (((double) time) / 1000), fail ? "FAIL" : "");
        }
        return pass;
      } else {
        System.out.printf("Some requests were not processed %d/%d%n", times.size(), requests);
        return false;
      }
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
    System.out.println(System.lineSeparator() + "Test 1");
    exceptionRule.expect(IllegalArgumentException.class);
    ProcessPoolExecutor pool = getPool(-1, 5, 0, null, false, false, false);
    perfTest(pool, false, new int[]{5}, 100, 10000, false, 0, false, false, false, 0, 4995, 6200);
  }

  @Test
  public void test02() throws Exception {
    System.out.println(System.lineSeparator() + "Test 2");
    exceptionRule.expect(IllegalArgumentException.class);
    ProcessPoolExecutor pool = getPool(0, 0, 0, null, false, false, false);
    pool.shutdown();
  }

  @Test
  public void test03() throws Exception {
    System.out.println(System.lineSeparator() + "Test 3");
    exceptionRule.expect(IllegalArgumentException.class);
    ProcessPoolExecutor pool = getPool(10, 5, 0, null, false, false, false);
    pool.shutdown();
  }

  @Test
  public void test04() throws Exception {
    System.out.println(System.lineSeparator() + "Test 4");
    exceptionRule.expect(IllegalArgumentException.class);
    ProcessPoolExecutor pool = getPool(10, 12, -1, null, false, false, false);
    pool.shutdown();
  }

  @Test
  public void test05() throws Exception {
    System.out.println(System.lineSeparator() + "Test 5");
    exceptionRule.expect(IllegalArgumentException.class);
    ProcessPoolExecutor pool = getPool(10, 12, 15, null, false, false, false);
    pool.shutdown();
  }

  @Test
  public void test06() throws Exception {
    System.out.println(System.lineSeparator() + "Test 6");
    ProcessPoolExecutor pool = getPool(0, Integer.MAX_VALUE, 0, null, false, false, false);
    exceptionRule.expect(IllegalArgumentException.class);
    perfTest(pool, false, null, 100, 10000, false, 0, false, false, false, 0, 4995, 6200);
  }

  @Test
  public void test07() throws Exception {
    System.out.println(System.lineSeparator() + "Test 7");
    ProcessPoolExecutor pool = getPool(0, Integer.MAX_VALUE, 0, null, false, false, false);
    exceptionRule.expect(IllegalArgumentException.class);
    perfTest(pool, false, new int[0], 100, 10000, false, 0, false, false, false, 0, 4995, 6200);
  }

  // Performance testing.
  @Test
  public void test08() throws Exception {
    System.out.println(System.lineSeparator() + "Test 8");
    ProcessPoolExecutor pool = getPool(0, 100, 0, null, true, false, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5}, 100, 10000, false, 0, false, false, false,
        0, 4995, 6250));
  }

  @Test
  public void test09() throws Exception {
    System.out.println(System.lineSeparator() + "Test 9");
    ProcessPoolExecutor pool = getPool(50, 150, 20, null, false, false, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5}, 100, 5000, false, 0, false, false, false,
        0, 4995, 5100));
  }

  @Test
  public void test10() throws Exception {
    System.out.println(System.lineSeparator() + "Test 10");
    ProcessPoolExecutor pool = getPool(10, 25, 5, 15000L, true, false, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5}, 20, 10000, false, 0, false, false, false,
        0, 4995, 5100));
  }

  @Test
  public void test11() throws Exception {
    System.out.println(System.lineSeparator() + "Test 11");
    ProcessPoolExecutor pool = getPool(50, 150, 20, null, false, true, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5}, 100, 5000, false, 0, false, false, false,
        0, 4995, 5100));
  }

  @Test
  public void test12() throws Exception {
    System.out.println(System.lineSeparator() + "Test 12");
    ProcessPoolExecutor pool = getPool(10, 50, 5, 15000L, true, false, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5, 3, 2}, 50, 10000, false, 0, false, false,
        false, 0, 9995, 10340));
  }

  @Test
  public void test13() throws Exception {
    System.out.println(System.lineSeparator() + "Test 13");
    ProcessPoolExecutor pool = getPool(100, 250, 20, null, true, true, false);
    Assert.assertTrue(perfTest(pool, true, new int[]{5}, 800, 20000, false, 0, false, false, false,
        0, 4995, 6000));
  }

  @Test
  public void test14() throws Exception {
    System.out.println(System.lineSeparator() + "Test 14");
    ProcessPoolExecutor pool = getPool(0, 100, 0, null, false, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 100, 10000, false, 0, false, false, false,
        0, 4995, 6850));
  }

  @Test
  public void test15() throws Exception {
    System.out.println(System.lineSeparator() + "Test 15");
    ProcessPoolExecutor pool = getPool(50, 150, 10, null, true, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 100, 5000, false, 0, false, false, false,
        0, 4995, 5620));
  }

  @Test
  public void test16() throws Exception {
    System.out.println(System.lineSeparator() + "Test 16");
    ProcessPoolExecutor pool = getPool(10, 25, 5, 15000L, false, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 10000, false, 0, false, false, false,
        0, 4995, 5100));
  }

  @Test
  public void test17() throws Exception {
    System.out.println(System.lineSeparator() + "Test 17");
    ProcessPoolExecutor pool = getPool(50, 150, 10, null, true, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 100, 5000, false, 0, false, false, false,
        0, 4995, 5600));
  }

  @Test
  public void test18() throws Exception {
    System.out.println(System.lineSeparator() + "Test 18");
    ProcessPoolExecutor pool = getPool(10, 50, 5, 15000L, false, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5, 3, 2}, 50, 10000, false, 0, false, false,
        false, 0, 9995, 10350));
  }

  @Test
  public void test19() throws Exception {
    System.out.println(System.lineSeparator() + "Test 19");
    ProcessPoolExecutor pool = getPool(50, 250, 20, null, true, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 800, 20000, false, 0, false, false, false,
        0, 4995, 6000));
  }

  // Keep alive timer test.
  @Test
  public void test20() throws Exception {
    System.out.println(System.lineSeparator() + "Test 20");
    ProcessPoolExecutor pool = getPool(20, 40, 4, 250L, true, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 50, 5000, false, 0, false, false, false,
        0, 4995, 8200));
  }

  // Cancellation testing.
  @Test
  public void test21() throws Exception {
    System.out.println(System.lineSeparator() + "Test 21");
    ProcessPoolExecutor pool = getPool(10, 30, 5, null, true, true, false);
    exceptionRule.expect(CancellationException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 0, false, 2500, true, false, false,
        0, 2495, 2520));
  }

  @Test
  public void test22() throws Exception {
    System.out.println(System.lineSeparator() + "Test 22");
    ProcessPoolExecutor pool = getPool(20, 20, 0, null, false, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 0, false, 2500, false, false, false,
        0, 4995, 5120));
  }

  @Test
  public void test23() throws Exception {
    System.out.println(System.lineSeparator() + "Test 23");
    ProcessPoolExecutor pool = getPool(10, 30, 5, null, true, true, false);
    exceptionRule.expect(CancellationException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5, 5, 3}, 20, 0, false, 2500, true, false,
        false, 0, 2495, 2520));
  }

  @Test
  public void test24() throws Exception {
    System.out.println(System.lineSeparator() + "Test 24");
    ProcessPoolExecutor pool = getPool(20, 20, 0, null, true, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5, 5, 3}, 20, 0, false, 3000, false, false,
        false, 0, 12995, 13120));
  }

  // Early shutdown testing.
  @Test
  public void test25() throws Exception {
    System.out.println(System.lineSeparator() + "Test 25");
    ProcessPoolExecutor pool = getPool(100, 100, 0, 5000L, true, false, false);
    exceptionRule.expect(ExecutionException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 100, 0, false, 0, false, true, true,
        0, 0, 0));
  }

  @Test
  public void test26() throws Exception {
    System.out.println(System.lineSeparator() + "Test 26");
    ProcessPoolExecutor pool = getPool(100, 100, 0, 5000L, true, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 100, 0, false, 0, false, true, false,
        0, 4995, 5100));
    exceptionRule.expect(RejectedExecutionException.class);
    pool.submit(new SimpleSubmission(new SimpleCommand(null, null, null)), false);
    pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
  }

  // Interrupted construction testing.
  @Test
  public void test27() throws Exception {
    System.out.println(System.lineSeparator() + "Test 27");
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
      pool = getPool(20, 30, 0, null, false, false, false);
    } finally {
      if (pool != null) {
        pool.forceShutdown();
      }
    }
  }

  // Single process pool performance testing.
  @Test
  public void test28() throws Exception {
    System.out.println(System.lineSeparator() + "Test 28");
    ProcessPoolExecutor pool = getPool(1, 1, 0, 20000L, true, true, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 5, 30000, false, 0, false, false, false,
        0, 4995, 5250));
  }

  @Test
  public void test29() throws Exception {
    System.out.println(System.lineSeparator() + "Test 29");
    ProcessPoolExecutor pool = getPool(1, 1, 0, null, true, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 5, 20000, false, 0, false, false, false,
        0, 4995, 13250));
  }

  // Fixed size process pool performance testing.
  @Test
  public void test30() throws Exception {
    System.out.println(System.lineSeparator() + "Test 30");
    ProcessPoolExecutor pool = getPool(20, 20, 0, null, true, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 5000, false, 0, false, false, false,
        0, 4995, 5200));
  }

  @Test
  public void test31() throws Exception {
    System.out.println(System.lineSeparator() + "Test 31");
    ProcessPoolExecutor pool = getPool(20, 20, 0, null, true, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 40, 10000, false, 0, false, false, false,
        0, 4995, 6200));
  }

  // Wait with timeout testing.
  @Test
  public void test32() throws Exception {
    System.out.println(System.lineSeparator() + "Test 32");
    ProcessPoolExecutor pool = getPool(20, 50, 10, null, true, true, false);
    exceptionRule.expect(TimeoutException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 40, 0, false, 0, false, false, false,
        3000, 3000, 3000));
  }

  @Test
  public void test33() throws Exception {
    System.out.println(System.lineSeparator() + "Test 33");
    ProcessPoolExecutor pool = getPool(20, 50, 0, null, true, true, false);
    exceptionRule.expect(TimeoutException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5, 5}, 40, 0, false, 0, false, false,
        false, 5000, 5000, 5000));
  }

  // Wait with timeout plus cancellation testing.
  @Test
  public void test34() throws Exception {
    System.out.println(System.lineSeparator() + "Test 34");
    ProcessPoolExecutor pool = getPool(10, 30, 5, null, true, true, false);
    exceptionRule.expect(CancellationException.class);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 0, false, 2500, true, false,
        false, 5000, 2495, 2520));
  }

  @Test
  public void test35() throws Exception {
    System.out.println(System.lineSeparator() + "Test 35");
    ProcessPoolExecutor pool = getPool(20, 20, 0, null, false, false, false);
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 0, false, 2500, false, false,
        false, 3000, 4995, 5120));
  }

  // Execution exception testing.
  @Test
  public void test36() throws Exception {
    System.out.println(System.lineSeparator() + "Test 36");
    ProcessPoolExecutor pool = getPool(0, Integer.MAX_VALUE, 0, null, false, false, false);
    exceptionRule.expect(ExecutionException.class);
    exceptionRule.expectMessage("Test execution exception.");
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 4000, true, 0, false, false,
        false, 0, 4995, 6200));
  }

  @Test
  public void test37() throws Exception {
    System.out.println(System.lineSeparator() + "Test 37");
    ProcessPoolExecutor pool = getPool(0, Integer.MAX_VALUE, 0, null, false, false, false);
    exceptionRule.expect(ExecutionException.class);
    exceptionRule.expectMessage("Test execution exception.");
    Assert.assertTrue(perfTest(pool, false, new int[]{5}, 20, 4000, true, 0, false, false,
        false, 1000, 4995, 6200));
  }

  // Startup exception testing.
  @Test
  public void test38() throws Exception {
    System.out.println(System.lineSeparator() + "Test 38");
    new ProcessPoolExecutor(new TestProcessManagerFactory(null, false, false, true), 20, 20, 0);
    Assert.assertTrue(true);
  }

  // Execute method testing.
  @Test
  public void test39() throws Exception {
    System.out.println(System.lineSeparator() + "Test 39");
    ProcessPoolExecutor pool = getPool(1, 1, 0, null, false, false, false);
    try {
      SimpleCommand command = new SimpleCommand("process 3",
          (c, o) -> "ready".equals(o) ? Status.SUCCESSFUL : Status.IN_PROGRESS,
          (c, o) -> Status.FAILED);
      long start = System.currentTimeMillis();
      pool.execute(new SimpleSubmission(command));
      long dur = System.currentTimeMillis() - start;
      boolean success = dur > 2995 && dur < 3050;
      System.out.println("------------------------------------------------------------------------------------------");
      System.out.printf("Time: %.3f %s%n", ((float) dur) / 1000, success ? "" : "FAIL");
      Assert.assertTrue(success);
    } finally {
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  /**
   * An implementation of the {@link net.viktorc.pp4j.api.ProcessManagerFactory} interface for testing purposes.
   *
   * @author Viktor Csomor
   */
  private class TestProcessManagerFactory implements ProcessManagerFactory {

    final Long keepAliveTime;
    final boolean verifyStartup;
    final boolean manuallyTerminate;
    final boolean throwStartupException;

    /**
     * Constructs an instance according to the specified parameters.
     *
     * @param verifyStartup Whether the startup should be verified.
     * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
     * @param throwStartupException Whether a process exception should be thrown on startup.
     */
    TestProcessManagerFactory(Long keepAliveTime, boolean verifyStartup, boolean manuallyTerminate, boolean throwStartupException) {
      this.keepAliveTime = keepAliveTime;
      this.verifyStartup = verifyStartup;
      this.manuallyTerminate = manuallyTerminate;
      this.throwStartupException = throwStartupException;
    }

    @Override
    public ProcessManager newProcessManager() {
      return new SimpleProcessManager(new ProcessBuilder(programLocation), null, null,
          () -> new SimpleSubmission(new SimpleCommand("start",
              (c, o) -> "ok".equals(o) ? Status.SUCCESSFUL : Status.IN_PROGRESS,
              (c, o) -> Status.FAILED)), null) {

        @Override
        public boolean startsUpInstantly() {
          if (throwStartupException) {
            throw new RuntimeException("Test startup exception.");
          }
          return !verifyStartup && super.startsUpInstantly();
        }

        @Override
        public boolean isStartedUp(String output, boolean error) {
          return (super.isStartedUp(output, error) && !verifyStartup) || (!error && "hi".equals(output));
        }

        @Override
        public Optional<Submission<?>> getTerminationSubmission() {
          if (manuallyTerminate) {
            return Optional.of(new SimpleSubmission(new SimpleCommand("stop",
                (c, o) -> "bye".equals(o) ? Status.SUCCESSFUL : Status.IN_PROGRESS,
                (c, o) -> Status.FAILED)));
          }
          return super.getTerminationSubmission();
        }

        @Override
        public Optional<Long> getKeepAliveTime() {
          return keepAliveTime != null ? Optional.of(keepAliveTime) : super.getKeepAliveTime();
        }
      };
    }

  }

}