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

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.FailedCommandException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sub-class of {@link AbstractSubmission} for serializable {@link Callable} instances to submit in Java process. It serializes, encodes,
 * and sends the <code>Callable</code> to the process for execution. It also looks for the serialized and encoded return value of the
 * <code>Callable</code>, and for a serialized and encoded {@link Throwable} instance output to the stderr stream in case of an error.
 *
 * @param <T> The serializable return type variable of the <code>Callable</code>
 * @param <S> A serializable <code>Callable</code> instance with the return type <code>T</code>.
 * @author Viktor Csomor
 */
public class JavaSubmission<T extends Serializable, S extends Callable<T> & Serializable> extends AbstractSubmission<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaSubmission.class);

  private final S task;
  private final String command;

  /**
   * Creates a submission for the specified {@link Callable}.
   *
   * @param task The task to execute.
   * @throws IOException If the encoding of the serialized task fails.
   */
  public JavaSubmission(S task) throws IOException {
    this.task = task;
    command = JavaObjectCodec.getInstance().encode(task);
  }

  /**
   * Returns the task that constitutes the submission.
   *
   * @return The task submission encapsulates.
   */
  public S getTask() {
    return task;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Command> getCommands() {
    return Collections.singletonList(new SimpleCommand(command,
        (command, outputLine) -> {
          Object output;
          try {
            output = JavaObjectCodec.getInstance().decode(outputLine);
          } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            LOGGER.trace(e.getMessage(), e);
            return false;
          }
          if (output instanceof JavaProcess.Response) {
            JavaProcess.Response response = (JavaProcess.Response) output;
            if (response.isError()) {
              throw new FailedCommandException(command, (Throwable) response.getResult());
            }
            setResult((T) response.getResult());
            return true;
          }
          return false;
        },
        (command, outputLine) -> {
          // It cannot happen, as stderr is redirected.
          throw new FailedCommandException(command, outputLine);
        }));
  }

  @Override
  public String toString() {
    return task.toString();
  }

}
