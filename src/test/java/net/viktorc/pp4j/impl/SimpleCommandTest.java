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

import java.util.List;
import net.viktorc.pp4j.api.FailedCommandException;
import org.junit.Assert;
import org.junit.Test;

/**
 * A unit test class for {@link SimpleCommand}.
 *
 * @author Viktor Csomor
 */
public class SimpleCommandTest extends TestCase {

  @Test
  public void testGeneratesOutputFalseWhenNoCompletionPredicatesDefined() {
    SimpleCommand command = new SimpleCommand("");
    Assert.assertFalse(command.generatesOutput());
  }

  @Test
  public void testGeneratesOutputTrueWhenCompletionPredicatesDefined() {
    SimpleCommand command = new SimpleCommand("", (c, o) -> true, (c, o) -> false);
    Assert.assertTrue(command.generatesOutput());
  }

  @Test
  public void testThrowsExceptionWhenInstructionNull() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleCommand(null);
  }

  @Test
  public void testThrowsExceptionWhenPredicateNull() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleCommand("", (c, o) -> true, null);
  }

  @Test
  public void testIsCompletedThrowsExceptionWhenStdErrPredicateNotDefined() throws FailedCommandException {
    SimpleCommand command = new SimpleCommand("", (c, o) -> true);
    exceptionRule.expect(FailedCommandException.class);
    command.isCompleted("", true);
  }

  @Test
  public void testIsCompletedReturnsTrueIfPredicateDoes() throws FailedCommandException {
    SimpleCommand command = new SimpleCommand("", (c, o) -> "done".equals(o), (c, o) -> "error".equals(o));
    Assert.assertFalse(command.isCompleted("error", false));
    Assert.assertFalse(command.isCompleted("", false));
    Assert.assertTrue(command.isCompleted("done", false));
    Assert.assertFalse(command.isCompleted("done", true));
    Assert.assertFalse(command.isCompleted("", true));
    Assert.assertTrue(command.isCompleted("error", true));
  }

  @Test
  public void testSavesProcessOutputCorrectly() throws FailedCommandException {
    SimpleCommand command = new SimpleCommand("", (c, o) -> true, (c, o) -> true);
    command.isCompleted("1", false);
    command.isCompleted("dog", true);
    command.isCompleted("2", false);
    command.isCompleted("cat", false);
    command.isCompleted("3", true);
    List<String> stdOutLines = command.getStandardOutLines();
    List<String> stdErrLines = command.getStandardErrLines();
    String jointStdOutLines = command.getJointStandardOutLines();
    String jointStdErrLines = command.getJointStandardErrLines();
    Assert.assertEquals(3, stdOutLines.size());
    Assert.assertEquals(2, stdErrLines.size());
    Assert.assertEquals("1", stdOutLines.get(0));
    Assert.assertEquals("2", stdOutLines.get(1));
    Assert.assertEquals("cat", stdOutLines.get(2));
    Assert.assertEquals("dog", stdErrLines.get(0));
    Assert.assertEquals("3", stdErrLines.get(1));
    Assert.assertEquals(String.format("1%n2%ncat"), jointStdOutLines);
    Assert.assertEquals(String.format("dog%n3"), jointStdErrLines);
  }

}
