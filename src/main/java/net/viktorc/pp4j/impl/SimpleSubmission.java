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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.viktorc.pp4j.api.Command;

/**
 * A simple sub-class of the {@link AbstractSubmission} class that allows for the specification of the commands to execute and the
 * result object to return.
 *
 * @param <T> The expected type of the result of the submission.
 * @author Viktor Csomor
 */
public class SimpleSubmission<T> extends AbstractSubmission<T> {

  private final List<Command> commands;

  /**
   * Constructs a <code>SimpleSubmission</code> instance according to the specified parameters.
   *
   * @param commands A list of commands to execute. It should not contain null references.
   * @param result The result object that will be returned wrapped in an <code>Optional</code> when invoking the {@link #getResult()}
   * method of the instance. Keeping the result a parameter of the submission allows for the commands of the submission to have a reference
   * to the result object before instantiating this submission and therefore, it enables them to modify the result from their method
   * definitions.
   * @throws IllegalArgumentException If the commands are null or empty or contain at least one null reference.
   */
  public SimpleSubmission(List<Command> commands, T result) {
    if (commands == null) {
      throw new IllegalArgumentException("The commands cannot be null.");
    }
    if (commands.isEmpty()) {
      throw new IllegalArgumentException("The commands cannot be empty.");
    }
    if (commands.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("The commands cannot include null references.");
    }
    this.commands = new ArrayList<>(commands);
    setResult(result);
  }

  /**
   * Constructs a <code>SimpleSubmission</code> instance according to the specified parameters. The submission's {@link #getResult()}
   * method will always return an empty optional.
   *
   * @param commands A list of commands to execute. It should not contain null references.
   * @throws IllegalArgumentException If the commands are null or empty or contain at least one null reference.
   */
  public SimpleSubmission(List<Command> commands) {
    this(commands, null);
  }

  /**
   * Constructs a <code>SimpleSubmission</code> instance according to the specified parameters.
   *
   * @param command A command to execute.
   * @param result The result object that will be returned wrapped in an <code>Optional</code> when invoking the {@link #getResult()}
   * method of the instance. Keeping the result a parameter of the submission allows for the commands of the submission to have a reference
   * to the result object before instantiating this submission and therefore, it enables them to modify the result from their method
   * definitions.
   * @throws IllegalArgumentException If the command is null.
   */
  public SimpleSubmission(Command command, T result) {
    this(Collections.singletonList(command), result);
  }

  /**
   * Constructs a <code>SimpleSubmission</code> instance according to the specified parameters. The submission's {@link #getResult()}
   * method will always return an empty optional.
   *
   * @param command A command to execute.
   * @throws IllegalArgumentException If the command is null.
   */
  public SimpleSubmission(Command command) {
    this(command, null);
  }

  @Override
  public List<Command> getCommands() {
    return new ArrayList<>(commands);
  }

}