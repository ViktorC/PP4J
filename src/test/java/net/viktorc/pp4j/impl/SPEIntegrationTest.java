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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.viktorc.pp4j.impl.TestUtils.TestProcessManagerFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * An integration test class for {@link SimpleProcessExecutor}.
 *
 * @author Viktor
 */
public class SPEIntegrationTest extends TestCase {

  @Test
  public void test01() throws Exception {
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(new TestProcessManagerFactory().newProcessManager())) {
      SimpleCommand command = new SimpleCommand("process 3", (c, o) -> "ready".equals(o));
      executor.start();
      executor.execute(new SimpleSubmission(command));
      Assert.assertFalse(executor.tryTerminate());
      Assert.assertTrue(command.getJointStandardOutLines().contains("in progress\nin progress\nready"));
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
      Assert.assertFalse(executor.isAlive());
      executor.start();
      Assert.assertTrue(executor.isAlive());
    }
  }

  @Test
  public void test04() throws Exception {
    Thread t;
    try (SimpleProcessExecutor executor = new SimpleProcessExecutor(new TestProcessManagerFactory().newProcessManager())) {
      Future<?> future = executor.start();
      t = new Thread(() -> {
        try {
          future.get();
        } catch (ExecutionException e) {
          throw new RuntimeException(e);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      });
      t.start();
      Assert.assertTrue(t.isAlive());
      executor.terminate();
      future.get();
      Thread.sleep(20);
      Assert.assertFalse(t.isAlive());
    }
  }

}
