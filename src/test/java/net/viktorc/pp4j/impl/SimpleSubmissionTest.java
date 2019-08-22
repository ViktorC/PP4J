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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.FailedCommandException;
import org.junit.Assert;
import org.junit.Test;

/**
 * A unit test class for {@link SimpleSubmission}.
 *
 * @author Viktor Csomor
 */
public class SimpleSubmissionTest extends TestCase {

  @Test
  public void testThrowsExceptionIfCommandsNull() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleSubmission<>((List<Command>) null);
  }

  @Test
  public void testThrowsExceptionIfCommandsEmpty() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleSubmission<>(Collections.emptyList());
  }

  @Test
  public void testThrowsExceptionIfCommandsAllNull() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleSubmission<>(Collections.singletonList(null));
  }

  @Test
  public void testThrowsExceptionIfCommandsContainNull() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleSubmission<>(Arrays.asList(new SimpleCommand(""), null));
  }

  @Test
  public void testReturnsEmptyOptionalResult() {
    SimpleSubmission<?> submission = new SimpleSubmission<>(new SimpleCommand(""));
    Assert.assertFalse(submission.getResult().isPresent());
  }

  @Test
  public void testReturnsCorrectResult() throws FailedCommandException {
    AtomicInteger result = new AtomicInteger(0);
    SimpleSubmission<AtomicInteger> submission = new SimpleSubmission<>(new SimpleCommand("", (c, o) -> {
      if ("done".equals(o)) {
        result.set(1);
        return true;
      }
      return false;
    }), result);
    Command command = submission.getCommands().get(0);
    command.isCompleted("not quite done", false);
    Assert.assertTrue(submission.getResult().isPresent());
    Assert.assertEquals(0, submission.getResult().get().get());
    command.isCompleted("done", false);
    Assert.assertTrue(submission.getResult().isPresent());
    Assert.assertEquals(1, submission.getResult().get().get());
  }

}
