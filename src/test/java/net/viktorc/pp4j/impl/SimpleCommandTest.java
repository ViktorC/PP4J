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
    SimpleCommand command = new SimpleCommand("", (o, s) -> true, (o, s) -> false);
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
    new SimpleCommand("", (o, s) -> true, null);
  }

  @Test
  public void testIsCompletedThrowsExceptionWhenStdErrPredicateNotDefined() throws FailedCommandException {
    SimpleCommand command = new SimpleCommand("", (o, s) -> true);
    exceptionRule.expect(FailedCommandException.class);
    command.isCompleted("", true);
  }

  @Test
  public void testIsCompletedReturnsTrueIfPredicateDoes() throws FailedCommandException {
    String stdOutSuccessPrerequisiteMessage = "meh";
    String stdOutSuccessMessage = "done";
    String stdErrSuccessMessage = "error";
    SimpleCommand command = new SimpleCommand("",
        (o, s) -> stdOutSuccessMessage.equals(o) && s.getStandardOutLines().contains(stdOutSuccessPrerequisiteMessage),
        (o, s) -> stdErrSuccessMessage.equals(o));
    Assert.assertFalse(command.isCompleted(stdErrSuccessMessage, false));
    Assert.assertFalse(command.isCompleted(stdOutSuccessMessage, false));
    Assert.assertFalse(command.isCompleted(stdOutSuccessPrerequisiteMessage, false));
    Assert.assertTrue(command.isCompleted(stdOutSuccessMessage, false));
    Assert.assertFalse(command.isCompleted(stdOutSuccessMessage, true));
    Assert.assertFalse(command.isCompleted(stdOutSuccessPrerequisiteMessage, true));
    Assert.assertTrue(command.isCompleted(stdErrSuccessMessage, true));
  }

  @Test
  public void testSavesProcessOutputCorrectly() throws FailedCommandException {
    String stdOutMessage1 = "1";
    String stdOutMessage2 = "2";
    String stdOutMessage3 = "cat";
    String stdErrMessage1 = "dog";
    String stdErrMessage2 = "3";
    SimpleCommand command = new SimpleCommand("", (o, s) -> true, (o, s) -> true);
    command.isCompleted(stdOutMessage1, false);
    command.isCompleted(stdErrMessage1, true);
    command.isCompleted(stdOutMessage2, false);
    command.isCompleted(stdOutMessage3, false);
    command.isCompleted(stdErrMessage2, true);
    ProcessOutputStore outputStore = command.getCommandOutputStore();
    List<String> stdOutLines = outputStore.getStandardOutLines();
    List<String> stdErrLines = outputStore.getStandardErrLines();
    String jointStdOutLines = outputStore.getJointStandardOutLines();
    String jointStdErrLines = outputStore.getJointStandardErrLines();
    Assert.assertEquals(3, stdOutLines.size());
    Assert.assertEquals(2, stdErrLines.size());
    Assert.assertEquals(stdOutMessage1, stdOutLines.get(0));
    Assert.assertEquals(stdOutMessage2, stdOutLines.get(1));
    Assert.assertEquals(stdOutMessage3, stdOutLines.get(2));
    Assert.assertEquals(stdErrMessage1, stdErrLines.get(0));
    Assert.assertEquals(stdErrMessage2, stdErrLines.get(1));
    Assert.assertEquals(String.format("%s%n%s%n%s", stdOutMessage1, stdOutMessage2, stdOutMessage3), jointStdOutLines);
    Assert.assertEquals(String.format("%s%n%s", stdErrMessage1, stdErrMessage2), jointStdErrLines);
  }

  @Test
  public void testSavesProcessOutputEvenIfFailedCommandExceptionThrown() throws FailedCommandException {
    String dummyMessage1 = "hi";
    String dummyMessage2 = "ho";
    String errorTriggerMessage = "boop";
    SimpleCommand command = new SimpleCommand("", (o, s) -> {
      if (errorTriggerMessage.equals(o)) {
        throw new FailedCommandException("Command failed");
      }
      return true;
    });
    command.isCompleted(dummyMessage1, false);
    command.isCompleted(dummyMessage2, false);
    exceptionRule.expect(FailedCommandException.class);
    try {
      command.isCompleted(errorTriggerMessage, false);
    } finally {
      ProcessOutputStore outputStore = command.getCommandOutputStore();
      Assert.assertEquals(0, outputStore.getStandardErrLines().size());
      Assert.assertEquals(3, outputStore.getStandardOutLines().size());
      Assert.assertEquals("", outputStore.getJointStandardErrLines());
      Assert.assertEquals(String.format("%s%n%s%n%s", dummyMessage1, dummyMessage2, errorTriggerMessage),
          outputStore.getJointStandardOutLines());
    }
  }

  @Test
  public void testResetClearsSavedProcessOutput() throws FailedCommandException {
    String stdOutMessage1 = "meng";
    String stdOutMessage2 = "derp";
    String stdErrMessage1 = "wut";
    String stdErrMessage2 = "blob";
    SimpleCommand command = new SimpleCommand("", (o, s) -> true, (o, s) -> true);
    command.isCompleted(stdErrMessage1, true);
    command.isCompleted(stdOutMessage1, false);
    command.isCompleted(stdErrMessage2, true);
    command.isCompleted(stdOutMessage2, false);
    ProcessOutputStore outputStore = command.getCommandOutputStore();
    Assert.assertEquals(2, outputStore.getStandardOutLines().size());
    Assert.assertEquals(2, outputStore.getStandardErrLines().size());
    Assert.assertEquals(String.format("%s%n%s", stdOutMessage1, stdOutMessage2), outputStore.getJointStandardOutLines());
    Assert.assertEquals(String.format("%s%n%s", stdErrMessage1, stdErrMessage2), outputStore.getJointStandardErrLines());
    command.reset();
    Assert.assertTrue(outputStore.getStandardOutLines().isEmpty());
    Assert.assertTrue(outputStore.getStandardErrLines().isEmpty());
    Assert.assertTrue(outputStore.getJointStandardOutLines().isEmpty());
    Assert.assertTrue(outputStore.getJointStandardErrLines().isEmpty());
  }

}
