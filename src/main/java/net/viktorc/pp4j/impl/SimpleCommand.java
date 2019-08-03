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

import net.viktorc.pp4j.api.FailedCommandException;

/**
 * A simple sub-class of the {@link AbstractCommand} abstract class that relies on lambda functions to implement the
 * {@link AbstractCommand#isExecutionCompleted(String, boolean)} method.
 *
 * @author Viktor Csomor
 */
public class SimpleCommand extends AbstractCommand {

  private final boolean generatesOutput;
  private final CommandOutputPredicate isCompletedStdOut;
  private final CommandOutputPredicate isCompletedStdErr;

  /**
   * Constructs a <code>SimpleCommand</code> according to the specified parameters assuming that the command generates some output.
   *
   * @param instruction The instruction to write to the process' standard in.
   * @param isCompletedStdOut The predicate that allows for the processing of the process' standard output in response to the command and
   * determines when the command is to be considered processed.
   * @param isCompletedStdErr The predicate that allows for the processing of the process' standard error output in response to the command and
   * determines when the command is to be considered processed.
   */
  public SimpleCommand(String instruction, CommandOutputPredicate isCompletedStdOut, CommandOutputPredicate isCompletedStdErr) {
    super(instruction);
    this.isCompletedStdOut = isCompletedStdOut;
    this.isCompletedStdErr = isCompletedStdErr;
    generatesOutput = true;
  }

  /**
   * Constructs a <code>SimpleCommand</code> assuming that the command does not generate any output.
   *
   * @param instruction The instruction to write to the process' standard in.
   */
  public SimpleCommand(String instruction) {
    super(instruction);
    isCompletedStdOut = null;
    isCompletedStdErr = null;
    generatesOutput = false;
  }

  @Override
  public boolean generatesOutput() {
    return generatesOutput;
  }

  @Override
  protected boolean isExecutionCompleted(String outputLine, boolean error) throws FailedCommandException {
    return (error ?
        isCompletedStdErr == null || isCompletedStdErr.isCompleted(this, outputLine) :
        isCompletedStdOut == null || isCompletedStdOut.isCompleted(this, outputLine));
  }

  @FunctionalInterface
  public interface CommandOutputPredicate {

    boolean isCompleted(SimpleCommand command, String outputLine) throws FailedCommandException;

  }

}