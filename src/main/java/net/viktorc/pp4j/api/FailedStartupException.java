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
package net.viktorc.pp4j.api;

/**
 * An exception thrown when the startup of a process fails.
 *
 * @author Viktor Csomor
 */
public class FailedStartupException extends Exception {

  /**
   * Wraps the provided exception in a <code>FailedStartupException</code>.
   *
   * @param e The cause exception.
   */
  public FailedStartupException(Throwable e) {
    super(e);
  }

  /**
   * Creates a <code>FailedStartupException</code> with the provided error message.
   *
   * @param message The error message describing the cause of the exception.
   */
  public FailedStartupException(String message) {
    super(message);
  }

}
