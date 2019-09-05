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
  public void testGeneratesOutputFalseIfNoCompletionPredicatesDefined() {
    SimpleCommand command = new SimpleCommand("");
    Assert.assertFalse(command.generatesOutput());
  }

  @Test
  public void testGeneratesOutputTrueIfCompletionPredicatesDefined() {
    SimpleCommand command = new SimpleCommand("", (c, o) -> true, (c, o) -> false);
    Assert.assertTrue(command.generatesOutput());
  }

  @Test
  public void testThrowsExceptionIfInstructionNull() {
    exceptionRule.expect(IllegalArgumentException.class);
    new SimpleCommand(null);
  }

  @Test
  public void testThrowsExceptionIfPredicateNull() {
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

  @Test
  public void testSavesProcessOutputEvenIfFailedCommandExceptionThrown() throws FailedCommandException {
    String errorTriggerMessage = "boop";
    SimpleCommand command = new SimpleCommand("", (c, o) -> {
      if (errorTriggerMessage.equals(o)) {
        throw new FailedCommandException(c, o);
      }
      return true;
    });
    command.isCompleted("hi", false);
    command.isCompleted("ho", false);
    exceptionRule.expect(FailedCommandException.class);
    try {
      command.isCompleted(errorTriggerMessage, false);
    } finally {
      Assert.assertEquals(0, command.getStandardErrLines().size());
      Assert.assertEquals(3, command.getStandardOutLines().size());
      Assert.assertEquals("", command.getJointStandardErrLines());
      Assert.assertEquals(String.format("hi%nho%n%s", errorTriggerMessage), command.getJointStandardOutLines());
    }
  }

  @Test
  public void testResetClearsSavedProcessOutput() throws FailedCommandException {
    SimpleCommand command = new SimpleCommand("", (c, o) -> "jiff".equals(o), (c, o) -> "jeff".equals(o));
    command.isCompleted("wut", true);
    command.isCompleted("meng", false);
    command.isCompleted("blob", true);
    command.isCompleted("derp", false);
    Assert.assertEquals(2, command.getStandardOutLines().size());
    Assert.assertEquals(2, command.getStandardErrLines().size());
    Assert.assertEquals(String.format("meng%nderp"), command.getJointStandardOutLines());
    Assert.assertEquals(String.format("wut%nblob"), command.getJointStandardErrLines());
    command.reset();
    Assert.assertEquals(0, command.getStandardOutLines().size());
    Assert.assertEquals(0, command.getStandardErrLines().size());
    Assert.assertEquals("", command.getJointStandardOutLines());
    Assert.assertEquals("", command.getJointStandardErrLines());
  }

}
