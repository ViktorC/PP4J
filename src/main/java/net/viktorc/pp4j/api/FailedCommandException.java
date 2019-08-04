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
 * An exception thrown when a command executed by a process fails.
 *
 * @author Viktor Csomor
 */
public class FailedCommandException extends Exception {

  /**
   * Wraps the provided exception in a <code>FailedCommandException</code>.
   *
   * @param command The failed command.
   * @param e The cause exception.
   */
  public FailedCommandException(Command command, Throwable e) {
    super(getBaseErrorMessage(command), e);
  }

  /**
   * Creates a <code>FailedCommandException</code> for the specified command with the provided error message.
   *
   * @param command The failed command.
   * @param message The reason of the failure.
   */
  public FailedCommandException(Command command, String message) {
    super(String.format("%s with reason: %s", getBaseErrorMessage(command), message));
  }

  /**
   * Creates a <code>FailedCommandException</code> for the specified command.
   *
   * @param command The failed command.
   */
  public FailedCommandException(Command command) {
    super(getBaseErrorMessage(command));
  }

  /**
   * Returns the base error message containing the command instruction.
   *
   * @param command The failed command.
   * @return The base error message.
   */
  private static String getBaseErrorMessage(Command command) {
    return String.format("Execution of command \"%s\" failed with", command.getInstruction());
  }

}
