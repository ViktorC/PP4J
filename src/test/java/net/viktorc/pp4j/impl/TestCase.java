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

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for both integration and unit tests.
 *
 * @author Viktor Csomor
 */
public class TestCase {

  @Rule
  public TestRule watcher = new TestWatcher() {

    protected void starting(Description description) {
      logger.info("Running {}", description.getMethodName());
    }
  };
  @Rule
  public final ExpectedException exceptionRule = ExpectedException.none();

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Logs the execution time and whether it was successful.
   *
   * @param success Whether the execution time was within the acceptable range.
   * @param time The execution time in milliseconds.
   */
  protected void logTime(boolean success, long time) {
    logger.info(String.format("Time: %.3f %s", ((double) time) / 1000, success ? "" : "FAIL"));
  }

}
