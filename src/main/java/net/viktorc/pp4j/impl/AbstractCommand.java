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

import java.util.ArrayList;
import java.util.List;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.FailedCommandException;

/**
 * An abstract implementation of the {@link Command} interface that stores all lines output to the process' standard out and standard
 * error in response to the command.
 *
 * @author Viktor Csomor
 */
public abstract class AbstractCommand implements Command {

  private final String instruction;
  private final List<String> stdOutLines;
  private final List<String> stdErrLines;

  /**
   * Constructs an instance holding the specified instruction.
   *
   * @param instruction The instruction to send to the process' standard in as the command.
   * @throws IllegalArgumentException If the instruction is <code>null</code>.
   */
  protected AbstractCommand(String instruction) {
    if (instruction == null) {
      throw new IllegalArgumentException("The instruction cannot be null");
    }
    this.instruction = instruction;
    stdOutLines = new ArrayList<>();
    stdErrLines = new ArrayList<>();
  }

  /**
   * It allows for the processing of the output and is responsible for determining whether the output line denotes the completion of the
   * execution of the command.
   *
   * @param outputLine The new line of output printed to the standard out of the process.
   * @param error Whether this line has been output to the standard error or to the standard out stream.
   * @return Whether the output denotes the completion of the command's execution.
   * @throws FailedCommandException If the output denotes the process has finished processing the command and the command failed.
   */
  protected abstract boolean isExecutionCompleted(String outputLine, boolean error) throws FailedCommandException;

  /**
   * Returns a list of lines output to the standard out of the underlying process after the instruction has been written to the standard in
   * of the process.
   *
   * @return A list of lines output to the standard out of the underlying process.
   */
  public List<String> getStandardOutLines() {
    return new ArrayList<>(stdOutLines);
  }

  /**
   * Returns a list of lines output to the standard error of the underlying process after the instruction has been written to the standard
   * in of the process.
   *
   * @return A list of lines output to the standard error stream of the underlying process.
   */
  public List<String> getStandardErrLines() {
    return new ArrayList<>(stdErrLines);
  }

  /**
   * Returns a string of the lines output to the standard out of the underlying process after the instruction has been written to the
   * standard in of the process.
   *
   * @return A string of the lines output to the standard out of the underlying process.
   */
  public String getJointStandardOutLines() {
    return String.join(System.lineSeparator(), stdOutLines);
  }

  /**
   * Returns a string of the lines output to the standard error of the underlying process after the instruction has been written to the
   * standard in of the process.
   *
   * @return A string of the lines output to the standard error of the underlying process.
   */
  public String getJointStandardErrLines() {
    return String.join(System.lineSeparator(), stdErrLines);
  }

  @Override
  public String getInstruction() {
    return instruction;
  }

  @Override
  public final boolean isCompleted(String outputLine, boolean error) throws FailedCommandException {
    try {
      return isExecutionCompleted(outputLine, error);
    } finally {
      if (error) {
        stdErrLines.add(outputLine);
      } else {
        stdOutLines.add(outputLine);
      }
    }
  }

  @Override
  public void reset() {
    stdOutLines.clear();
    stdErrLines.clear();
  }

}