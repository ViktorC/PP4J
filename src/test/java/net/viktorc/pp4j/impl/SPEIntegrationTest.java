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

import net.viktorc.pp4j.impl.TestUtils.TestProcessManagerFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * An integration test class for {@link SimpleProcessExecutor}.
 *
 * @author Viktor Csomor
 */
public class SPEIntegrationTest extends TestCase {

  @Test
  public void test01() throws Exception {
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(new TestProcessManagerFactory().newProcessManager())) {
      executor.start();
      Assert.assertFalse(executor.tryTerminate());
    }
  }

  @Test
  public void test02() throws Exception {
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(new TestProcessManagerFactory().newProcessManager())) {
      executor.start();
      exceptionRule.expect(IllegalStateException.class);
      executor.start();
    }
  }

  @Test
  public void test03() throws Exception {
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(new TestProcessManagerFactory().newProcessManager())) {
      executor.start();
      exceptionRule.expect(IllegalStateException.class);
      executor.run();
    }
  }

  @Test
  public void test04() throws Exception {
    SimpleProcessExecutor executorRef;
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(new TestProcessManagerFactory().newProcessManager())) {
      executorRef = executor;
      Assert.assertFalse(executor.isAlive());
      executor.start();
      Assert.assertTrue(executor.isAlive());
    }
    Assert.assertFalse(executorRef.isAlive());
  }

  @Test
  public void test05() throws Exception {
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(new TestProcessManagerFactory().newProcessManager())) {
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
  public void test06() throws Exception {
    Thread t;
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(new TestProcessManagerFactory().newProcessManager())) {
      executor.start();
      t = new Thread(() -> {
        try {
          executor.waitFor();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      });
      t.start();
      Assert.assertTrue(t.isAlive());
      executor.terminate();
      executor.waitFor();
      Thread.sleep(20);
      Assert.assertFalse(t.isAlive());
    }
  }

  @Test
  public void test07() throws Exception {
    SimpleCommand command = new SimpleCommand("process 3", (c, o) -> "ready".equals(o));
    SimpleSubmission<?> submission = new SimpleSubmission<>(command);
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(new TestProcessManagerFactory().newProcessManager())) {
      executor.start();
      executor.execute(submission);
      Assert.assertTrue(command.getJointStandardOutLines().contains("in progress\nin progress\nready"));
    }
  }

}
