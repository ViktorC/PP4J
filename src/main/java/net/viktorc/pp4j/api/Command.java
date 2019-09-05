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
 * An interface that defines methods for providing an instruction string and processing of the outputs of the process in response to the
 * instruction. The {@link #isCompleted(String, boolean)} method is invoked every time a new line is output to one the process' standard
 * streams and is responsible for determining when the process finished executing the command.
 *
 * @author Viktor Csomor
 */
public interface Command extends Resettable {

  /**
   * Returns the instruction to send to the process.
   *
   * @return The instruction to write to the process' standard in.
   */
  String getInstruction();

  /**
   * A method called before the issuing of the instruction. It denotes whether the command is expected to generate output. If it returns
   * <code>false</code>, the command is considered successfully processed as soon as it is written to the process' standard in and
   * therefore the process is considered ready for new commands right away. If it returns <code>true</code>, the
   * {@link #isCompleted(String, boolean)} method determines when the command is deemed processed.
   *
   * @return Whether the executing process is expected to generate output in response to the command.
   */
  boolean generatesOutput();

  /**
   * A method called every time a new line is printed to the standard out or standard error stream of the process after the command has
   * been sent to its standard in until the method returns <code>true</code> or throws a <code>FailedCommandException</code>.
   *
   * @param outputLine The new line of output printed to the standard out of the process.
   * @param error Whether this line has been output to the standard error or to the standard out stream.
   * @return Whether this line of output denotes that the process has finished processing the command.
   * @throws FailedCommandException If the output denotes the process has finished processing the command and the command failed. The
   * process is to be considered ready for executing new submissions after this exception is thrown.
   */
  boolean isCompleted(String outputLine, boolean error) throws FailedCommandException;

}