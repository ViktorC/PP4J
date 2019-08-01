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

import java.util.function.BiFunction;

/**
 * A simple sub-class of the {@link net.viktorc.pp4j.impl.AbstractCommand} abstract class that relies on lambda functions to implement the
 * {@link net.viktorc.pp4j.impl.AbstractCommand#getNewStatus(String, boolean)} method and assumes that the process generates an output in
 * response to the command.
 *
 * @author Viktor Csomor
 */
public class SimpleCommand extends AbstractCommand {

  private final BiFunction<SimpleCommand, String, Status> getStatusStdOut;
  private final BiFunction<SimpleCommand, String, Status> getStatusStdErr;

  /**
   * Constructs an instance according to the specified parameters.
   *
   * @param instruction The instruction to write to the process' standard in.
   * @param getStatusStdOut The predicate that allows for the processing of the process' standard output in response to the command and
   * determines when the command is to be considered processed by returning the appropriate <code>Status</code>.
   * @param getStatusStdErr The predicate that allows for the processing of the process' standard error output in response to the command and
   * determines when the command is to be considered processed by returning the appropriate <code>Status</code>.
   */
  public SimpleCommand(String instruction, BiFunction<SimpleCommand, String, Status> getStatusStdOut,
      BiFunction<SimpleCommand, String, Status> getStatusStdErr) {
    super(instruction);
    this.getStatusStdOut = getStatusStdOut;
    this.getStatusStdErr = getStatusStdErr;
  }

  @Override
  public boolean generatesOutput() {
    return true;
  }

  @Override
  protected Status getNewStatus(String outputLine, boolean error) {
    return (error ? getStatusStdErr.apply(this, outputLine) : getStatusStdOut.apply(this, outputLine));
  }

}