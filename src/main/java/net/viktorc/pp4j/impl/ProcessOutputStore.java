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

/**
 * A class for capturing and storing output printed to the standard out and standard error streams of a process.
 *
 * @author Viktor Csomor
 */
public class ProcessOutputStore {

  private final List<String> stdOutLines;
  private final List<String> stdErrLines;

  /**
   * Constructs a <code>ProcessOutputStore</code> instance.
   */
  protected ProcessOutputStore() {
    stdOutLines = new ArrayList<>();
    stdErrLines = new ArrayList<>();
  }

  /**
   * Returns a list of lines output to the standard out stream of the underlying process.
   *
   * @return A list of lines output to the standard out stream of the underlying process.
   */
  public List<String> getStandardOutLines() {
    return new ArrayList<>(stdOutLines);
  }

  /**
   * Returns a list of lines output to the standard error stream of the underlying process.
   *
   * @return A list of lines output to the standard error stream of the underlying process.
   */
  public List<String> getStandardErrLines() {
    return new ArrayList<>(stdErrLines);
  }

  /**
   * Returns the lines output to the standard out stream of the underlying process concatenated into a single string delimited by newline
   * characters.
   *
   * @return A string of the lines output to the standard out stream of the underlying process.
   */
  public String getJointStandardOutLines() {
    return String.join(System.lineSeparator(), stdOutLines);
  }

  /**
   * Returns the lines output to the standard error stream of the underlying process concatenated into a single string delimited by newline
   * characters.
   *
   * @return A string of the lines output to the standard error stream of the underlying process.
   */
  public String getJointStandardErrLines() {
    return String.join(System.lineSeparator(), stdErrLines);
  }

  /**
   * Stores the process output by adding it to the appropriate list of output lines based on the stream it was printed to.
   *
   * @param outputLine The output of the process
   * @param error Whether the line was output to the process' standard error stream or standard out stream.
   */
  protected void storeOutput(String outputLine, boolean error) {
    if (error) {
      stdErrLines.add(outputLine);
    } else {
      stdOutLines.add(outputLine);
    }
  }

  /**
   * Clears the saved output lines.
   */
  protected void clear() {
    stdOutLines.clear();
    stdErrLines.clear();
  }

}
