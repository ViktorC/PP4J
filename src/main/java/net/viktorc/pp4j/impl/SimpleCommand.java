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
 * A simple sub-class of the {@link AbstractCommand} class that relies on instances of a functional interface passed to its constructor to
 * implement the {@link AbstractCommand#isExecutionCompleted(String, boolean)} method.
 *
 * @author Viktor Csomor
 */
public class SimpleCommand extends AbstractCommand {

  private final boolean generatesOutput;
  private final CommandCompletionPredicate isCompletedStdOut;
  private final CommandCompletionPredicate isCompletedStdErr;

  /**
   * Constructs a <code>SimpleCommand</code> according to the specified parameters assuming that the command generates some output.
   *
   * @param instruction The instruction to write to the process' standard in.
   * @param isCompletedStdOut The predicate that allows for the processing of the process' standard output in response to the command and
   * determines when the command is to be considered processed.
   * @param isCompletedStdErr The predicate that allows for the processing of the process' standard error output in response to the command
   * and determines when the command is to be considered processed.
   * @throws IllegalArgumentException If either of the two predicates is <code>null</code>.
   */
  public SimpleCommand(String instruction, CommandCompletionPredicate isCompletedStdOut, CommandCompletionPredicate isCompletedStdErr) {
    super(instruction);
    if (isCompletedStdOut == null || isCompletedStdErr == null) {
      throw new IllegalArgumentException("Command completion predicate cannot be null");
    }
    generatesOutput = true;
    this.isCompletedStdOut = isCompletedStdOut;
    this.isCompletedStdErr = isCompletedStdErr;
  }

  /**
   * Constructs a <code>SimpleCommand</code> according to the specified parameters assuming that the command generates some output and if
   * there is anything output to the process' standard error stream, a <code>FailedCommandException</code> is to be thrown.
   *
   * @param instruction The instruction to write to the process' standard in.
   * @param isCompletedStdOut The predicate that allows for the processing of the process' standard output in response to the command and
   * determines when the command is to be considered processed.
   */
  public SimpleCommand(String instruction, CommandCompletionPredicate isCompletedStdOut) {
    this(instruction, isCompletedStdOut, ((outputLine, outputStore) -> {
      throw new FailedCommandException(String.format("Command failure indicated by %s printed to stderr", outputLine));
    }));
  }

  /**
   * Constructs a <code>SimpleCommand</code> assuming that the command does not generate any output.
   *
   * @param instruction The instruction to write to the process' standard in.
   */
  public SimpleCommand(String instruction) {
    super(instruction);
    generatesOutput = false;
    isCompletedStdOut = null;
    isCompletedStdErr = null;
  }

  @Override
  public boolean generatesOutput() {
    return generatesOutput;
  }

  @Override
  protected boolean isExecutionCompleted(String outputLine, boolean error) throws FailedCommandException {
    return (error ?
        isCompletedStdErr == null || isCompletedStdErr.isCompleted(outputLine, getCommandOutputStore()) :
        isCompletedStdOut == null || isCompletedStdOut.isCompleted(outputLine, getCommandOutputStore()));
  }

  /**
   * A bi-predicate that may throw {@link FailedCommandException} for determining when a command's execution is complete based on the
   * executing process' output.
   *
   * @author Viktor Csomor
   */
  @FunctionalInterface
  public interface CommandCompletionPredicate {

    /**
     * Returns whether the command's execution is complete based on the latest line of output.
     *
     * @param outputLine The latest line output by the process executing the command.
     * @param commandOutputStore The output store holding all previous outputs of the process in response to the command.
     * @return Whether the latest line of output denotes command completion.
     * @throws FailedCommandException If the command's execution is completed but the command failed.
     */
    boolean isCompleted(String outputLine, ProcessOutputStore commandOutputStore) throws FailedCommandException;

  }

}