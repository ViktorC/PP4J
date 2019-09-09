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

import org.junit.Assert;
import org.junit.Test;

/**
 * A unit test class for {@link ProcessOutputStore}.
 *
 * @author Viktor Csomor
 */
public class ProcessOutputStoreTest extends TestCase {

  @Test
  public void testStoreSavesOutputLines() {
    String stdOutMessage1 = "1";
    String stdOutMessage2 = "two";
    String stdErrMessage1 = "-1";
    ProcessOutputStore outputStore = new ProcessOutputStore();
    outputStore.storeOutput(stdOutMessage1, false);
    outputStore.storeOutput(stdOutMessage2, false);
    outputStore.storeOutput(stdErrMessage1, true);
    Assert.assertEquals(2, outputStore.getStandardOutLines().size());
    Assert.assertEquals(1, outputStore.getStandardErrLines().size());
    Assert.assertEquals(stdOutMessage1, outputStore.getStandardOutLines().get(0));
    Assert.assertEquals(stdOutMessage2, outputStore.getStandardOutLines().get(1));
    Assert.assertEquals(stdErrMessage1, outputStore.getStandardErrLines().get(0));
  }

  @Test
  public void testJointOutputLines() {
    String message1 = "alice";
    String message2 = "bob";
    ProcessOutputStore outputStore = new ProcessOutputStore();
    outputStore.storeOutput(message1, false);
    outputStore.storeOutput(message2, false);
    outputStore.storeOutput(message2, true);
    outputStore.storeOutput(message1, true);
    Assert.assertEquals(String.format("%s%n%s", message1, message2), outputStore.getJointStandardOutLines());
    Assert.assertEquals(String.format("%s%n%s", message2, message1), outputStore.getJointStandardErrLines());
  }

  @Test
  public void testClearClearsStoredOutput() {
    String stdOutMessage1 = "ed";
    String stdOutMessage2 = "edd";
    String stdErrMessage1 = "eddy";
    ProcessOutputStore outputStore = new ProcessOutputStore();
    outputStore.storeOutput(stdOutMessage1, false);
    outputStore.storeOutput(stdOutMessage2, false);
    outputStore.storeOutput(stdErrMessage1, true);
    Assert.assertEquals(2, outputStore.getStandardOutLines().size());
    Assert.assertEquals(1, outputStore.getStandardErrLines().size());
    Assert.assertEquals(stdOutMessage1, outputStore.getStandardOutLines().get(0));
    Assert.assertEquals(stdOutMessage2, outputStore.getStandardOutLines().get(1));
    Assert.assertEquals(stdErrMessage1, outputStore.getStandardErrLines().get(0));
    Assert.assertEquals(String.format("%s%n%s", stdOutMessage1, stdOutMessage2), outputStore.getJointStandardOutLines());
    Assert.assertEquals(stdErrMessage1, outputStore.getStandardErrLines().get(0));
    outputStore.clear();
    Assert.assertTrue(outputStore.getStandardOutLines().isEmpty());
    Assert.assertTrue(outputStore.getStandardErrLines().isEmpty());
    Assert.assertTrue(outputStore.getJointStandardOutLines().isEmpty());
    Assert.assertTrue(outputStore.getJointStandardErrLines().isEmpty());
  }

}
