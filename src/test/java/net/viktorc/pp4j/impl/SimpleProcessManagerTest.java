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
 * A unit test class for {@link SimpleProcessManager}.
 *
 * @author Viktor Csomor
 */
public class SimpleProcessManagerTest extends TestCase {

  @Test
  public void testStartsUpInstantlyTrueIfNoPredicateDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""));
    Assert.assertTrue(manager.startsUpInstantly());
  }

  @Test
  public void testStartsUpInstantlyFalseIfPredicateDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), null, (o, e) -> true);
    Assert.assertFalse(manager.startsUpInstantly());
  }

  @Test
  public void testIsStartedUpReturnsTrueIfPredicateDoes() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), null, (o, e) -> !e && "ready".equals(o));
    Assert.assertFalse(manager.isStartedUp("bla", false));
    Assert.assertFalse(manager.isStartedUp("ready", true));
    Assert.assertTrue(manager.isStartedUp("ready", false));
  }

  @Test
  public void testInitSubmissionEmptyIfNoProviderDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""));
    Assert.assertFalse(manager.getInitSubmission().isPresent());
  }

  @Test
  public void testInitSubmissionNotEmptyIfProviderDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), null, null,
        () -> new SimpleSubmission<>(new SimpleCommand("")), null);
    Assert.assertTrue(manager.getInitSubmission().isPresent());
  }

  @Test
  public void testTerminationSubmissionEmptyIfNoProviderDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""));
    Assert.assertFalse(manager.getTerminationSubmission().isPresent());
  }

  @Test
  public void tesTerminationSubmissionNotEmptyIfProviderDefined() {
    SimpleProcessManager manager = new SimpleProcessManager(new ProcessBuilder(""), null, null, null,
        () -> new SimpleSubmission<>(new SimpleCommand("")));
    Assert.assertTrue(manager.getTerminationSubmission().isPresent());
  }

}
