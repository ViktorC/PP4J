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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.viktorc.pp4j.api.JavaProcessConfig;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.Submission;
import net.viktorc.pp4j.impl.TestUtils.TestProcessManagerFactory;
import org.junit.Assume;
import org.junit.Test;

/**
 * An integration test class for comparing the performance of {@link ProcessPoolExecutor} using a native executable wrapper program and
 * {@link JavaProcessPoolExecutor} using JNI/JNA.
 *
 * @author Viktor Csomor
 */
public class PPEvsJPPEIntegrationTest extends TestCase {

  private static final int WARM_UP_SUBMISSIONS = 5;

  /**
   * Times the execution of the specified number of submissions provided by the submission supplier in the process pool executor.
   *
   * @param processPool The process pool executor to test.
   * @param submissions The number of submissions to execute.
   * @param reuse Whether processes should be reused for multiple submissions.
   * @param submissionSupplier The submission supplier.
   * @return The time it took to execute all submissions in nanoseconds.
   * @throws Exception If something goes south.
   */
  private static long testProcessPool(ProcessPoolExecutor processPool, int submissions, boolean reuse,
      Supplier<Submission<?>> submissionSupplier) throws Exception {
    try {
      for (int i = 0; i < WARM_UP_SUBMISSIONS; i++) {
        processPool.submit(submissionSupplier.get(), !reuse).get();
      }
      List<Future<?>> futures = new ArrayList<>();
      long start = System.nanoTime();
      for (int i = 0; i < submissions; i++) {
        futures.add(processPool.submit(submissionSupplier.get(), !reuse));
      }
      for (Future<?> f : futures) {
        f.get();
      }
      return System.nanoTime() - start;
    } finally {
      processPool.shutdown();
      processPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }
  }

  /**
   * Times the execution of the specified number of submissions provided by the submission supplier in the Java process pool executor.
   *
   * @param minSize The minimum sizes of the two pools.
   * @param maxSize The maximum sizes of the two pools.
   * @param reserveSize The reserve sizes of the two pools.
   * @param submissions The number of submissions to send to the pools.
   * @param reuse Whether processes should be reused or killed after the execution of a submission.
   * @param startupTask The startup task.
   * @param submissionTask The submission task that is to be executed <code>submissions</code> times (possibly in parallel).
   * @param <T> The type of the serializable startup task.
   * @return The time it took to execute all submissions in nanoseconds.
   * @throws Exception If something goes south.
   */
  private static <T extends Runnable & Serializable> long testJavaProcessPool(int minSize, int maxSize, int reserveSize, int submissions,
      boolean reuse, T startupTask, T submissionTask) throws Exception {
    JavaProcessConfig javaConfig = new SimpleJavaProcessConfig(1, 10, 512);
    T actualStartupTask = reuse ? startupTask : null;
    JavaProcessManagerFactory<T> processManagerFactory = new JavaProcessManagerFactory<>(javaConfig, actualStartupTask, null, null);
    JavaProcessPoolExecutor javaProcessPool = new JavaProcessPoolExecutor(processManagerFactory, minSize, maxSize, reserveSize);
    return testProcessPool(javaProcessPool, submissions, reuse, () -> new JavaSubmission<>((Callable<Serializable> & Serializable) () -> {
      submissionTask.run();
      return null;
    }));
  }

  /**
   * Executes the performance test according to the specified arguments on a Java process pool using JNI to invoke the native test method.
   *
   * @param minSize The minimum sizes of the two pools.
   * @param maxSize The maximum sizes of the two pools.
   * @param reserveSize The reserve sizes of the two pools.
   * @param submissions The number of submissions to send to the pools.
   * @param taskTime The number of seconds a single task should take.
   * @param reuse Whether processes should be reused or killed after the execution of a submission.
   * @return The time it took to execute the specified number of submissions with the specified execution length in nanoseconds.
   * @throws Exception If something goes wrong.
   */
  private static long testJNIProcessPool(int minSize, int maxSize, int reserveSize, int submissions, int taskTime, boolean reuse)
      throws Exception {
    return testJavaProcessPool(minSize, maxSize, reserveSize, submissions, reuse,
        (Runnable & Serializable) WrapperJNI::new,
        (Runnable & Serializable) () -> (new WrapperJNI()).doStuff(taskTime));
  }

  /**
   * Executes the performance test according to the specified arguments on a Java process pool using JNA to invoke the native test method.
   *
   * @param minSize The minimum sizes of the two pools.
   * @param maxSize The maximum sizes of the two pools.
   * @param reserveSize The reserve sizes of the two pools.
   * @param submissions The number of submissions to send to the pools.
   * @param taskTime The number of seconds a single task should take.
   * @param reuse Whether processes should be reused or killed after the execution of a submission.
   * @return The time it took to execute the specified number of submissions with the specified execution length in nanoseconds.
   * @throws Exception If something goes wrong.
   */
  private static long testJNAProcessPool(int minSize, int maxSize, int reserveSize, int submissions, int taskTime, boolean reuse)
      throws Exception {
    return testJavaProcessPool(minSize, maxSize, reserveSize, submissions, reuse,
        (Runnable & Serializable) WrapperJNA::getInstance,
        (Runnable & Serializable) () -> WrapperJNA.getInstance().doStuff(taskTime));
  }

  /**
   * Executes the performance test according to the specified arguments on a native process pool.
   *
   * @param minSize The minimum sizes of the two pools.
   * @param maxSize The maximum sizes of the two pools.
   * @param reserveSize The reserve sizes of the two pools.
   * @param submissions The number of submissions to send to the pools.
   * @param taskTime The number of seconds a single task should take.
   * @param reuse Whether processes should be reused or killed after the execution of a submission.
   * @return The time it took to execute the specified number of submissions with the specified execution length in nanoseconds.
   * @throws Exception If something goes wrong.
   */
  private static long testNativeProcessPool(int minSize, int maxSize, int reserveSize, int submissions, int taskTime, boolean reuse)
      throws Exception {
    ProcessManagerFactory processManagerFactory = new TestProcessManagerFactory();
    ProcessPoolExecutor nativeProcessPool = new ProcessPoolExecutor(processManagerFactory, minSize, maxSize, reserveSize);
    return testProcessPool(nativeProcessPool, submissions, reuse,
        () -> new SimpleSubmission(new SimpleCommand("process " + taskTime, (c, o) -> "ready".equals(o))));
  }

  /**
   * Executes the performance comparison according to the provided arguments.
   *
   * @param minSize The minimum sizes of the two pools.
   * @param maxSize The maximum sizes of the two pools.
   * @param reserveSize The reserve sizes of the two pools.
   * @param submissions The number of submissions to send to the pools.
   * @param taskTime The number of seconds a single task should take.
   * @param reuse Whether processes should be reused or killed after the execution of a submission.
   * @param jna Whether the Java process pool should use JNA or JNI to invoke the native method.
   * @return Whether the test was successful.
   * @throws Exception If something goes wrong.
   */
  private boolean perfTest(int minSize, int maxSize, int reserveSize, int submissions, int taskTime, boolean reuse, boolean jna)
      throws Exception {
    long nativeTime = testNativeProcessPool(minSize, maxSize, reserveSize, submissions, taskTime, reuse);
    long javaTime = jna ? testJNAProcessPool(minSize, maxSize, reserveSize, submissions, taskTime, reuse) :
        testJNIProcessPool(minSize, maxSize, reserveSize, submissions, taskTime, reuse);
    boolean success = nativeTime < javaTime;
    logger.info(String.format("Native time - %.3f : Java time - %.3f %s%n",
        (float) (((double) nativeTime) / 1000000),
        (float) (((double) javaTime) / 1000000),
        success ? "" : "FAIL"));
    return success;
  }

  @Test
  public void test01() throws Exception {
    Assume.assumeTrue(perfTest(1, 1, 0, 6, 3, true, false));
  }

  @Test
  public void test02() throws Exception {
    Assume.assumeTrue(perfTest(1, 1, 0, 10, 2, true, true));
  }

  @Test
  public void test03() throws Exception {
    Assume.assumeTrue(perfTest(20, 50, 10, 50, 2, true, false));
  }

  @Test
  public void test04() throws Exception {
    Assume.assumeTrue(perfTest(20, 50, 10, 50, 2, true, true));
  }

  @Test
  public void test05() throws Exception {
    Assume.assumeTrue(perfTest(1, 1, 0, 10, 2, false, false));
  }

  @Test
  public void test06() throws Exception {
    Assume.assumeTrue(perfTest(1, 1, 0, 10, 2, false, true));
  }

  @Test
  public void test07() throws Exception {
    Assume.assumeTrue(perfTest(20, 50, 10, 50, 2, false, false));
  }

  @Test
  public void test08() throws Exception {
    Assume.assumeTrue(perfTest(20, 50, 10, 50, 2, false, true));
  }

}
