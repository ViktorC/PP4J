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

import java.nio.charset.Charset;
import net.viktorc.pp4j.api.FailedStartupException;
import org.junit.Assert;
import org.junit.Test;

/**
 * A unit test class for {@link SimpleProcessManager}.
 *
 * @author Viktor Csomor
 */
public class SimpleProcessManagerTest extends TestCase {

  @Test
  public void testStartsUpInstantlyTrueIfNoPredicateDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset());
    Assert.assertTrue(manager.startsUpInstantly());
  }

  @Test
  public void testStartsUpInstantlyFalseIfPredicateDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset(),
        (o, s) -> true);
    Assert.assertFalse(manager.startsUpInstantly());
  }

  @Test
  public void testIsStartedUpReturnsTrueIfAppropriatePredicateDoes() throws FailedStartupException {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset(),
        (o, s) -> "ready".equals(o), (o, s) -> "recovered".equals(o));
    Assert.assertFalse(manager.isStartedUp("bla", false));
    Assert.assertFalse(manager.isStartedUp("ready", true));
    Assert.assertTrue(manager.isStartedUp("ready", false));
    Assert.assertFalse(manager.isStartedUp("recovered", false));
    Assert.assertTrue(manager.isStartedUp("recovered", true));
  }

  @Test
  public void testIsStartedUpThrowsFailedStartupException() throws FailedStartupException {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset(), (o, s) -> true);
    Assert.assertTrue(manager.isStartedUp("", false));
    String errorOutput = "oops";
    exceptionRule.expect(FailedStartupException.class);
    exceptionRule.expectMessage(errorOutput);
    manager.isStartedUp(errorOutput, true);
  }

  @Test
  public void testInitSubmissionEmptyIfNoProviderDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset());
    Assert.assertFalse(manager.getInitSubmission().isPresent());
  }

  @Test
  public void testInitSubmissionNotEmptyIfProviderDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset(), null,
        () -> new SimpleSubmission<>(new SimpleCommand("")), null);
    Assert.assertTrue(manager.getInitSubmission().isPresent());
  }

  @Test
  public void testTerminationSubmissionEmptyIfNoProviderDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset());
    Assert.assertFalse(manager.getTerminationSubmission().isPresent());
  }

  @Test
  public void testTerminationSubmissionNotEmptyIfProviderDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset(), null, null,
        () -> new SimpleSubmission<>(new SimpleCommand("")));
    Assert.assertTrue(manager.getTerminationSubmission().isPresent());
  }

  @Test
  public void testStoresProcessOutputCorrectly() throws FailedStartupException {
    String stdOutMessage1 = "01";
    String stdOutMessage2 = "02";
    String stdErrMessage1 = "aa";
    String stdErrMessage2 = "ab";
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset(),
        (o, s) -> true, (o, s) -> true);
    manager.isStartedUp(stdOutMessage1, false);
    manager.isStartedUp(stdErrMessage1, true);
    manager.isStartedUp(stdOutMessage2, false);
    manager.isStartedUp(stdErrMessage2, true);
    ProcessOutputStore outputStore = manager.getStartupOutputStore();
    Assert.assertEquals(2, outputStore.getStandardOutLines().size());
    Assert.assertEquals(2, outputStore.getStandardErrLines().size());
    Assert.assertEquals(stdOutMessage1, outputStore.getStandardOutLines().get(0));
    Assert.assertEquals(stdOutMessage2, outputStore.getStandardOutLines().get(1));
    Assert.assertEquals(stdErrMessage1, outputStore.getStandardErrLines().get(0));
    Assert.assertEquals(stdErrMessage2, outputStore.getStandardErrLines().get(1));
    Assert.assertEquals(String.format("%s%n%s", stdOutMessage1, stdOutMessage2), outputStore.getJointStandardOutLines());
    Assert.assertEquals(String.format("%s%n%s", stdErrMessage1, stdErrMessage2), outputStore.getJointStandardErrLines());
  }

  @Test
  public void testStoresProcessOutputEvenIfFailedStartupExceptionThrown() throws FailedStartupException {
    String message1 = "01";
    String message2 = "02";
    String errorTriggerMessage = "boop";
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset(),
        (o, s) -> {
          if (errorTriggerMessage.equals(o)) {
            throw new FailedStartupException("ye");
          }
          return true;
        });
    manager.isStartedUp(message1, false);
    manager.isStartedUp(message2, false);
    exceptionRule.expect(FailedStartupException.class);
    try {
      manager.isStartedUp(errorTriggerMessage, false);
    } finally {
      ProcessOutputStore outputStore = manager.getStartupOutputStore();
      Assert.assertEquals(3, outputStore.getStandardOutLines().size());
      Assert.assertTrue(outputStore.getStandardErrLines().isEmpty());
      Assert.assertEquals(message1, outputStore.getStandardOutLines().get(0));
      Assert.assertEquals(message2, outputStore.getStandardOutLines().get(1));
      Assert.assertEquals(errorTriggerMessage, outputStore.getStandardOutLines().get(2));
      Assert.assertEquals(String.format("%s%n%s%n%s", message1, message2, errorTriggerMessage), outputStore.getJointStandardOutLines());
      Assert.assertEquals("", outputStore.getJointStandardErrLines());
    }
  }

  @Test
  public void testResetClearsSavedProcessOutput() throws FailedStartupException {
    String stdOutMessage1 = "hey";
    String stdOutMessage2 = "ho";
    String stdErrMessage1 = "dee";
    String stdErrMessage2 = "quad";
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), Charset.defaultCharset(),
        (o, s) -> true, (o, s) -> true);
    manager.isStartedUp(stdOutMessage1, false);
    manager.isStartedUp(stdErrMessage1, true);
    manager.isStartedUp(stdOutMessage2, false);
    manager.isStartedUp(stdErrMessage2, true);
    ProcessOutputStore outputStore = manager.getStartupOutputStore();
    Assert.assertEquals(2, outputStore.getStandardOutLines().size());
    Assert.assertEquals(2, outputStore.getStandardErrLines().size());
    Assert.assertEquals(stdOutMessage1, outputStore.getStandardOutLines().get(0));
    Assert.assertEquals(stdOutMessage2, outputStore.getStandardOutLines().get(1));
    Assert.assertEquals(stdErrMessage1, outputStore.getStandardErrLines().get(0));
    Assert.assertEquals(stdErrMessage2, outputStore.getStandardErrLines().get(1));
    Assert.assertEquals(String.format("%s%n%s", stdOutMessage1, stdOutMessage2), outputStore.getJointStandardOutLines());
    Assert.assertEquals(String.format("%s%n%s", stdErrMessage1, stdErrMessage2), outputStore.getJointStandardErrLines());
    manager.reset();
    Assert.assertTrue(outputStore.getStandardOutLines().isEmpty());
    Assert.assertTrue(outputStore.getStandardErrLines().isEmpty());
    Assert.assertTrue(outputStore.getJointStandardOutLines().isEmpty());
    Assert.assertTrue(outputStore.getJointStandardErrLines().isEmpty());
  }

}
